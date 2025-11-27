// src/main/java/pt/dot/application/service/PoiEnrichmentService.java
package pt.dot.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.OsmPoiSnapshot;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.monumentos.MonumentDto;
import pt.dot.application.monumentos.MonumentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PoiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(PoiEnrichmentService.class);

    private final DistrictRepository districtRepository;
    private final PoiRepository poiRepository;
    private final MonumentService monumentService;
    private final WikipediaService wikipediaService;

    public PoiEnrichmentService(DistrictRepository districtRepository,
                                PoiRepository poiRepository,
                                MonumentService monumentService,
                                WikipediaService wikipediaService) {
        this.districtRepository = districtRepository;
        this.poiRepository = poiRepository;
        this.monumentService = monumentService;
        this.wikipediaService = wikipediaService;
    }

    // ========================================================================
    // ENRIQUECER TODOS OS POIS EXISTENTES
    // ========================================================================

    /**
     * Percorre a BD já populada com POIs (via OSM) e tenta enriquecer:
     *  - atribui distrito por proximidade (se ainda não tiver)
     *  - tenta match no SIPA (respeitando categoria/tipo)
     *  - tenta summary na Wikipedia (respeitando categoria/tipo)
     *  - tenta sempre buscar imagens (Wiki, e no futuro SIPA)
     */
    @Transactional
    public void enrichAllExistingPois() {
        List<Poi> all = poiRepository.findAll();
        log.info("[PoiEnrichment] Iniciar enriquecimento de {} POIs", all.size());

        int processed = 0;
        int enriched = 0;

        for (Poi poi : all) {
            processed++;

            if (poi.getLat() == null || poi.getLon() == null) {
                log.debug("[PoiEnrichment] SKIP poi id={} (sem coords) source={}",
                        poi.getId(), poi.getSource());
                continue;
            }
            double lat = poi.getLat();
            double lon = poi.getLon();

            // bounding box bem simples para PT continental
            if (lat < 36.8 || lat > 42.3 || lon < -9.8 || lon > -6.0) {
                log.debug("[PoiEnrichment] SKIP poi id={} (fora de PT: lat={}, lon={})",
                        poi.getId(), lat, lon);
                continue;
            }

            if (!needsEnrichment(poi)) {
                log.trace("[PoiEnrichment] SKIP poi id={} (não precisa de enrichment)", poi.getId());
                continue;
            }

            boolean changed = enrichSinglePoi(poi);
            if (changed) {
                poiRepository.save(poi);
                enriched++;
            }

            if (processed % 500 == 0) {
                log.info("[PoiEnrichment] Processados {} POIs, enriquecidos até agora {}",
                        processed, enriched);
            }
        }

        log.info("[PoiEnrichment] Enriquecimento concluído. Processados={}, enriquecidos={}",
                processed, enriched);
    }

    /**
     * Decide se ainda vale a pena tentar enriquecer um dado POI.
     * Mesmo que já tenha descrição, continua a enriquecer se faltar distrito / wiki / imagens / sipa.
     */
    private boolean needsEnrichment(Poi poi) {
        // Só mexemos em POIs vindos do OSM (ou OSM+enriched)
        String source = poi.getSource();
        if (source == null || !source.startsWith("osm")) return false;

        boolean missingSipa     = poi.getSipaId() == null || poi.getSipaId().isBlank();
        boolean missingWiki     = poi.getWikipediaUrl() == null || poi.getWikipediaUrl().isBlank();
        boolean missingDesc     = poi.getDescription() == null
                || poi.getDescription().isBlank()
                || "Ponto de interesse ainda sem descrição detalhada.".equals(poi.getDescription());
        boolean missingImage    = poi.getImage() == null || poi.getImage().isBlank();
        boolean missingDistrict = poi.getDistrict() == null;

        return missingSipa || missingWiki || missingDesc || missingImage || missingDistrict;
    }

    /**
     * Enriquecimento de um único POI já existente na BD.
     */
    private boolean enrichSinglePoi(Poi poi) {
        boolean changed = false;

        boolean gotDescFromSipa = false;
        boolean gotImageFromSipa = false;
        boolean gotDescFromWiki = false;
        boolean gotImageFromWiki = false;

        log.debug("[PoiEnrichment] >>> enrichSinglePoi id={} name='{}' lat={} lon={} source={}",
                poi.getId(), poi.getName(), poi.getLat(), poi.getLon(), poi.getSource());

        // 1) distrito por proximidade
        if (poi.getDistrict() == null && poi.getLat() != null && poi.getLon() != null) {
            District nearest = resolveDistrictFromCoords(poi.getLat(), poi.getLon());
            if (nearest != null) {
                poi.setDistrict(nearest);
                changed = true;
                log.debug("[PoiEnrichment] Atribuído distrito {} a POI id={} (osmId={})",
                        nearest.getNamePt(), poi.getId(), poi.getExternalOsmId());
            }
        }

        // 2) SIPA (com verificação de compatibilidade de tipo)
        if (poi.getSipaId() == null || poi.getSipaId().isBlank()) {
            log.debug("[PoiEnrichment] [SIPA] Procurar match para poiId={} name='{}' lat={} lon={}",
                    poi.getId(), poi.getName(), poi.getLat(), poi.getLon());

            MonumentDto fromSipa = monumentService.findBestMatch(
                    poi.getName(),
                    poi.getLat(),
                    poi.getLon()
            );

            if (fromSipa != null) {
                String candidateTitle = fromSipa.getOriginalName();
                String candidateText = (candidateTitle == null ? "" : candidateTitle + " ")
                        + (fromSipa.getShortDescription() != null ? fromSipa.getShortDescription() : "");

                Type poiType = mapCategoryToType(poi.getCategory());
                Type candidateType = detectTypeFromText(candidateText);

                log.debug("[PoiEnrichment] [SIPA] Candidate id={} title='{}' poiType={} candidateType={}",
                        fromSipa.getId(), candidateTitle, poiType, candidateType);

                if (!isTypeCompatible(poiType, candidateType)) {
                    log.info("[PoiEnrichment] SIPA match descartado por tipo incompatível: poiType={} candidateType={} poiId={} name='{}' sipaTitle='{}'",
                            poiType, candidateType, poi.getId(), poi.getName(), candidateTitle);
                } else {
                    log.info("[PoiEnrichment] SIPA match para POI id={} name={} -> sipaId={}",
                            poi.getId(), poi.getName(), fromSipa.getId());

                    if (fromSipa.getOriginalName() != null && !fromSipa.getOriginalName().isBlank()) {
                        poi.setName(fromSipa.getOriginalName());
                        poi.setNamePt(fromSipa.getOriginalName());
                        gotDescFromSipa = true; // nome/título também contam como enriquecimento
                    }

                    String description = fromSipa.getShortDescription();
                    if ((description == null || description.isBlank())
                            && fromSipa.getFullDescriptionHtml() != null) {
                        description = fromSipa.getFullDescriptionHtml()
                                .replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();
                    }

                    // só substitui se a descrição actual for fraca
                    boolean isDescFraca = poi.getDescription() == null
                            || poi.getDescription().isBlank()
                            || "Ponto de interesse ainda sem descrição detalhada.".equals(poi.getDescription());

                    if (isDescFraca && description != null && !description.isBlank()) {
                        poi.setDescription(description);
                        gotDescFromSipa = true;
                    }

                    poi.setSipaId(fromSipa.getId());
                    changed = true;
                }
            } else {
                log.debug("[PoiEnrichment] [SIPA] Nenhum match encontrado para poiId={} name='{}'",
                        poi.getId(), poi.getName());
            }
        }

        log.info("[PoiEnrichment] POI id={} (osmId={}) — SIPA desc={}, SIPA img={}",
                poi.getId(), poi.getExternalOsmId(), gotDescFromSipa, gotImageFromSipa);

        // 3) Wikipedia (sempre tentamos; só sobrescreve descrição se a actual for fraca)
        WikipediaSummary wiki = wikipediaService.fetchSummary(
                poi.getName(),
                poi.getLat(),
                poi.getLon()
        );

        if (wiki != null) {
            log.debug("[PoiEnrichment] [Wiki] Encontrado summary? {} url={} image={}",
                    wiki.getSummary() != null && !wiki.getSummary().isBlank(),
                    wiki.getUrl(),
                    wiki.getImageUrl());

            boolean isDescFraca = poi.getDescription() == null
                    || poi.getDescription().isBlank()
                    || "Ponto de interesse ainda sem descrição detalhada.".equals(poi.getDescription());

            if (isDescFraca && wiki.getSummary() != null && !wiki.getSummary().isBlank()) {
                poi.setDescription(wiki.getSummary());
                gotDescFromWiki = true;
                changed = true;
            }

            if ((poi.getWikipediaUrl() == null || poi.getWikipediaUrl().isBlank())
                    && wiki.getUrl() != null && !wiki.getUrl().isBlank()) {
                poi.setWikipediaUrl(wiki.getUrl());
                changed = true;
            }

            if (wiki.getImageUrl() != null && !wiki.getImageUrl().isBlank()) {
                if (poi.getImage() == null || poi.getImage().isBlank()) {
                    poi.setImage(wiki.getImageUrl());
                    gotImageFromWiki = true;
                    changed = true;
                }

                List<String> imgs = poi.getImages() != null
                        ? new ArrayList<>(poi.getImages())
                        : new ArrayList<>();

                if (!imgs.contains(wiki.getImageUrl())) {
                    imgs.add(wiki.getImageUrl());
                    poi.setImages(imgs);
                    gotImageFromWiki = true;
                    changed = true;
                }
            }
        } else {
            log.debug("[PoiEnrichment] [Wiki] Nenhum resumo/url encontrado para poiId={} name='{}'",
                    poi.getId(), poi.getName());
        }

        log.info("[PoiEnrichment] POI id={} (osmId={}) — Wiki desc={}, Wiki img={}",
                poi.getId(), poi.getExternalOsmId(), gotDescFromWiki, gotImageFromWiki);

        // 4) fallback de descrição
        if (poi.getDescription() == null || poi.getDescription().isBlank()) {
            poi.setDescription("Ponto de interesse ainda sem descrição detalhada.");
            changed = true;
        }

        if (changed && !"osm+enriched".equals(poi.getSource())) {
            poi.setSource("osm+enriched");
        }

        log.debug("[PoiEnrichment] <<< enrichSinglePoi id={} changed={} sipaId={} wikiUrl={}",
                poi.getId(), changed, poi.getSipaId(), poi.getWikipediaUrl());

        return changed;
    }

    // ========================================================================
    // OUTROS MÉTODOS DE SERVIÇO + CRIAÇÃO A PARTIR DE SNAPSHOT OSM
    // ========================================================================

    public List<PoiDto> findPois(Integer districtId, String category) {
        return poiRepository.findPois(districtId, category)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PoiDto createOrEnrichFromOsm(OsmPoiSnapshot snapshot) {
        log.info("[PoiEnrichment][create] >>> OSM POI name='{}' osmId={} lat={} lon={} category={}",
                snapshot.getName(), snapshot.getOsmId(), snapshot.getLat(), snapshot.getLon(), snapshot.getCategory());

        District district = resolveDistrict(snapshot);

        Optional<Poi> existing =
                poiRepository.findByExternalOsmId(snapshot.getOsmId());

        if (existing.isPresent()) {
            log.info("[PoiEnrichment][create] Já existe POI com externalOsmId={}, a devolver DTO.",
                    snapshot.getOsmId());
            return toDto(existing.get());
        }

        boolean gotDescFromSipa = false;
        boolean gotImageFromSipa = false;
        boolean gotDescFromWiki = false;
        boolean gotImageFromWiki = false;

        // 2) tentar enriquecer com SIPA (com verificação de tipo)
        log.debug("[PoiEnrichment][create] [SIPA] Procurar match para name='{}' lat={} lon={}",
                snapshot.getName(), snapshot.getLat(), snapshot.getLon());

        MonumentDto fromSipa = monumentService.findBestMatch(
                snapshot.getName(),
                snapshot.getLat(),
                snapshot.getLon()
        );

        String finalName = snapshot.getName();
        String description = null;
        String sipaId = null;
        String wikipediaUrl = null;
        List<String> imageList = new ArrayList<>();

        Type poiType = mapCategoryToType(snapshot.getCategory());

        if (fromSipa != null) {
            String candidateTitle = fromSipa.getOriginalName();
            String candidateText = (candidateTitle == null ? "" : candidateTitle + " ")
                    + (fromSipa.getShortDescription() != null ? fromSipa.getShortDescription() : "");

            Type candidateType = detectTypeFromText(candidateText);

            log.debug("[PoiEnrichment][create] [SIPA] Candidate id={} title='{}' poiType={} candidateType={}",
                    fromSipa.getId(), candidateTitle, poiType, candidateType);

            if (!isTypeCompatible(poiType, candidateType)) {
                log.info("[PoiEnrichment] SIPA match descartado (createOrEnrich) por tipo incompatível: poiType={} candidateType={} osmId={} sipaTitle='{}'",
                        poiType, candidateType, snapshot.getOsmId(), candidateTitle);
            } else {
                log.info("[PoiEnrichment] SIPA match encontrado: id={} | nome={}",
                        fromSipa.getId(), fromSipa.getOriginalName());

                if (fromSipa.getOriginalName() != null && !fromSipa.getOriginalName().isBlank()) {
                    finalName = fromSipa.getOriginalName();
                    gotDescFromSipa = true;
                }

                description = fromSipa.getShortDescription();
                if ((description == null || description.isBlank())
                        && fromSipa.getFullDescriptionHtml() != null) {
                    description = fromSipa.getFullDescriptionHtml()
                            .replaceAll("<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .trim();
                }

                if (description != null && !description.isBlank()) {
                    gotDescFromSipa = true;
                }

                sipaId = fromSipa.getId();
            }
        } else {
            log.debug("[PoiEnrichment][create] [SIPA] Nenhum match encontrado para osmId={} name='{}'",
                    snapshot.getOsmId(), snapshot.getName());
        }

        log.info("[PoiEnrichment][create] osmId={} — SIPA desc={}, SIPA img={}",
                snapshot.getOsmId(), gotDescFromSipa, gotImageFromSipa);

        // 3) Wikipedia: se não tivermos descrição, usa summary; se já tivermos, usa só imagem/URL
        WikipediaSummary wiki = wikipediaService.fetchSummary(
                snapshot.getName(),
                snapshot.getLat(),
                snapshot.getLon()
        );

        if (wiki != null) {
            log.debug("[PoiEnrichment][create] [Wiki] summary? {} url={} image={}",
                    wiki.getSummary() != null && !wiki.getSummary().isBlank(),
                    wiki.getUrl(),
                    wiki.getImageUrl());

            Type candidateType = detectTypeFromText(wiki.getSummary());
            if (!isTypeCompatible(poiType, candidateType)) {
                log.info("[PoiEnrichment] Wikipedia summary descartado (createOrEnrich) por tipo incompatível: poiType={} candidateType={} osmId={} url={}",
                        poiType, candidateType, snapshot.getOsmId(), wiki.getUrl());
            } else {
                if (description == null || description.isBlank()) {
                    description = wiki.getSummary();
                    wikipediaUrl = wiki.getUrl();
                    gotDescFromWiki = true;
                } else {
                    if (wikipediaUrl == null || wikipediaUrl.isBlank()) {
                        wikipediaUrl = wiki.getUrl();
                    }
                }

                if (wiki.getImageUrl() != null && !wiki.getImageUrl().isBlank()) {
                    if (!imageList.contains(wiki.getImageUrl())) {
                        imageList.add(wiki.getImageUrl());
                    }
                    gotImageFromWiki = true;
                }
            }
        } else {
            log.debug("[PoiEnrichment][create] [Wiki] Nenhum resumo/url encontrado para osmId={} name='{}'",
                    snapshot.getOsmId(), snapshot.getName());
        }

        log.info("[PoiEnrichment][create] osmId={} — Wiki desc={}, Wiki img={}",
                snapshot.getOsmId(), gotDescFromWiki, gotImageFromWiki);

        // 4) fallback total de descrição
        if (description == null || description.isBlank()) {
            description = "Ponto de interesse ainda sem descrição detalhada.";
        }

        // 5) criar o POI na BD
        Poi poi = new Poi();
        poi.setDistrict(district);
        poi.setName(finalName);
        poi.setNamePt(finalName);
        poi.setCategory(snapshot.getCategory());
        poi.setSubcategory(null);
        poi.setDescription(description);
        poi.setLat(snapshot.getLat());
        poi.setLon(snapshot.getLon());
        poi.setWikipediaUrl(wikipediaUrl);
        poi.setSipaId(sipaId);
        poi.setExternalOsmId(snapshot.getOsmId());
        poi.setSource("osm+enriched");

        if (!imageList.isEmpty()) {
            poi.setImage(imageList.get(0));
            poi.setImages(imageList);
        }

        Poi saved = poiRepository.save(poi);

        log.info("[PoiEnrichment][create] <<< criado poiId={} districtId={} sipaId={} wikiUrl={} imageCount={}",
                saved.getId(),
                saved.getDistrict() != null ? saved.getDistrict().getId() : null,
                saved.getSipaId(),
                saved.getWikipediaUrl(),
                saved.getImages() != null ? saved.getImages().size() : 0);

        return toDto(saved);
    }

    private District resolveDistrict(OsmPoiSnapshot snapshot) {
        Long districtId = snapshot.getDistrictId();

        if (districtId != null) {
            return districtRepository.findById(districtId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Distrito não encontrado: " + districtId));
        }

        if (Double.isNaN(snapshot.getLat()) || Double.isNaN(snapshot.getLon())) {
            log.warn("[PoiEnrichment] Snapshot OSM sem districtId e coords inválidas (osmId={})",
                    snapshot.getOsmId());
            return null;
        }

        District nearest = resolveDistrictFromCoords(snapshot.getLat(), snapshot.getLon());
        if (nearest == null) {
            log.warn("[PoiEnrichment] Não foi possível determinar distrito para osmId={} (lat={}, lon={})",
                    snapshot.getOsmId(), snapshot.getLat(), snapshot.getLon());
        } else {
            log.debug("[PoiEnrichment] Atribuído distritoId={} para osmId={} (lat={}, lon={})",
                    nearest.getId(), snapshot.getOsmId(), snapshot.getLat(), snapshot.getLon());
        }

        return nearest;
    }

    /**
     * Encontra o distrito cujo centro (lat/lon) está mais próximo do ponto dado.
     */
    private District resolveDistrictFromCoords(double lat, double lon) {
        List<District> districts = districtRepository.findAll();
        if (districts.isEmpty()) {
            return null;
        }

        District best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (District d : districts) {
            if (d.getLat() == null || d.getLon() == null) {
                continue;
            }
            double dx = lat - d.getLat();
            double dy = lon - d.getLon();
            double dist2 = dx * dx + dy * dy;

            if (dist2 < bestDist2) {
                bestDist2 = dist2;
                best = d;
            }
        }

        return best;
    }

    private PoiDto toDto(Poi p) {
        Long districtId = (p.getDistrict() != null ? p.getDistrict().getId() : null);

        return new PoiDto(
                p.getId(),
                districtId,
                p.getName(),
                p.getNamePt(),
                p.getCategory(),
                p.getSubcategory(),
                p.getDescription(),
                p.getLat(),
                p.getLon(),
                p.getWikipediaUrl(),
                p.getSipaId(),
                p.getExternalOsmId(),
                p.getSource(),
                p.getImage(),
                p.getImages()
        );
    }

    // DTO muito simples só para o resumo da Wikipedia
    public static class WikipediaSummary {
        private final String summary;
        private final String url;
        private final String imageUrl; // imagem principal (se existir)

        public WikipediaSummary(String summary, String url, String imageUrl) {
            this.summary = summary;
            this.url = url;
            this.imageUrl = imageUrl;
        }

        public String getSummary() {
            return summary;
        }

        public String getUrl() {
            return url;
        }

        public String getImageUrl() {
            return imageUrl;
        }
    }

    // ========================================================================
    // Helpers de tipo/categoria
    // ========================================================================

    private enum Type {
        CHURCH,
        PALACE,
        VIEWPOINT,
        PARK,
        CASTLE,
        MONUMENT,
        CULTURAL,
        UNKNOWN
    }

    /**
     * Mapeia a category do POI para um tipo lógico.
     */
    private Type mapCategoryToType(String category) {
        if (category == null) return Type.UNKNOWN;
        String c = category.toLowerCase(Locale.ROOT);

        return switch (c) {
            case "church" -> Type.CHURCH;
            case "palace" -> Type.PALACE;
            case "viewpoint" -> Type.VIEWPOINT;
            case "park" -> Type.PARK;
            case "castle" -> Type.CASTLE;
            case "monument" -> Type.MONUMENT;
            case "cultural" -> Type.CULTURAL;
            default -> Type.UNKNOWN;
        };
    }

    /**
     * Tenta inferir o tipo a partir de texto (nome + descrição).
     */
    private Type detectTypeFromText(String text) {
        if (text == null || text.isBlank()) return Type.UNKNOWN;
        String t = text.toLowerCase(Locale.ROOT);

        // ordem mais específica primeiro
        if (t.contains("igreja") || t.contains("sé ") || t.contains(" sé ")
                || t.contains("basilica") || t.contains("basílica")
                || t.contains("capela") || t.contains("ermida")
                || t.contains("mosteiro") || t.contains("convento")) {
            return Type.CHURCH;
        }

        if (t.contains("palácio") || t.contains("palacio") || t.contains("palace")) {
            return Type.PALACE;
        }

        if (t.contains("miradouro") || t.contains("mirador")
                || t.contains("viewpoint") || t.contains("mirante")) {
            return Type.VIEWPOINT;
        }

        if (t.contains("parque") || t.contains("jardim") || t.contains("garden")
                || t.contains("jardins")) {
            return Type.PARK;
        }

        if (t.contains("castelo") || t.contains("castle")
                || t.contains("fortaleza") || t.contains("fortress")) {
            return Type.CASTLE;
        }

        if (t.contains("monumento") || t.contains("monument")) {
            return Type.MONUMENT;
        }

        return Type.UNKNOWN;
    }

    /**
     * Regra de compatibilidade entre tipo do POI e tipo inferido do texto.
     * - Igual → OK
     * - UNKNOWN em qualquer lado → não bloqueia
     * - VIEWPOINT é o único que pode aceitar matches de PARK/MONUMENT (praça/jardim/afins)
     */
    private boolean isTypeCompatible(Type poiType, Type candidateType) {
        if (poiType == Type.UNKNOWN || candidateType == Type.UNKNOWN) {
            return true; // se não sabemos, não bloqueamos
        }

        if (poiType == candidateType) {
            return true;
        }

        // regra especial: viewpoints podem encaixar em praça/jardim/monumento
        if (poiType == Type.VIEWPOINT &&
                (candidateType == Type.PARK || candidateType == Type.MONUMENT || candidateType == Type.VIEWPOINT)) {
            return true;
        }

        // cultural é genérico: aceitável quase tudo de histórico
        if (poiType == Type.CULTURAL &&
                (candidateType == Type.CASTLE || candidateType == Type.PALACE
                        || candidateType == Type.MONUMENT || candidateType == Type.CHURCH)) {
            return true;
        }

        return false;
    }
}
