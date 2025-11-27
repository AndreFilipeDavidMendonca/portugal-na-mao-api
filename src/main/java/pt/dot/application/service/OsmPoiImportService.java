// src/main/java/pt/dot/application/service/OsmPoiImportService.java
package pt.dot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.osm.OverpassClient;
import pt.dot.application.osm.OverpassQueries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OsmPoiImportService {

    private static final Logger log = LoggerFactory.getLogger(OsmPoiImportService.class);

    private final OverpassClient overpassClient;
    private final PoiRepository poiRepository;

    public OsmPoiImportService(OverpassClient overpassClient,
                               PoiRepository poiRepository) {
        this.overpassClient = overpassClient;
        this.poiRepository = poiRepository;
    }

    /**
     * Importa TODOS os POIs do país (Portugal inteiro), por categoria lógica:
     *  - ruins   (castelos, palácios, monumentos, etc.)
     *  - church  (igrejas, catedrais, capelas, place_of_worship)
     *  - nature  (miradouros, parques, jardins)
     *
     * NÃO enriquece (SIPA/Wikipedia) nem atribui distrito aqui.
     * Apenas cria/atualiza POIs brutos com base no OSM (nome, categoria, coords).
     */
    public void importAllPortugal() {
        // BBOX grosso de Portugal continental (south,west,north,east)
        double minLat = 36.8;
        double minLon = -9.7;
        double maxLat = 42.2;
        double maxLon = -6.0;

        String bbox = minLat + "," + minLon + "," + maxLat + "," + maxLon;
        log.info("[OsmPoiImport] Import global para Portugal bbox={}", bbox);

        importCategoryForPortugal(bbox, "ruins");
        importCategoryForPortugal(bbox, "church");
        importCategoryForPortugal(bbox, "nature");

        long total = poiRepository.count();
        log.info("[OsmPoiImport] Import global concluído (por categoria). Total de POIs na BD = {}", total);
    }

    private void importCategoryForPortugal(String bbox, String logicalCategory) {
        String query;
        switch (logicalCategory) {
            case "ruins" -> query = OverpassQueries.buildCulturalPointsQuery(bbox);
            case "church" -> query = OverpassQueries.buildChurchPointsQuery(bbox);
            case "nature" -> query = OverpassQueries.buildNaturePointsQuery(bbox);
            default -> throw new IllegalArgumentException("Categoria desconhecida: " + logicalCategory);
        }

        log.info("[OsmPoiImport] A chamar Overpass para categoria={} bbox={}", logicalCategory, bbox);

        JsonNode root;
        try {
            root = overpassClient.executeQuery(query);
        } catch (Exception e) {
            log.error("[OsmPoiImport] Erro a chamar Overpass para categoria={} (bbox={})",
                    logicalCategory, bbox, e);
            return;
        }

        if (root == null) {
            log.warn("[OsmPoiImport] Overpass sem resposta (root=null) para categoria={} bbox={}",
                    logicalCategory, bbox);
            return;
        }

        JsonNode elements = root.path("elements");
        if (!elements.isArray()) {
            log.warn("[OsmPoiImport] Campo 'elements' não é array para categoria={} bbox={}", logicalCategory, bbox);
            return;
        }

        if (elements.isEmpty()) {
            log.info("[OsmPoiImport] Sem elementos para categoria={} bbox={}", logicalCategory, bbox);
            return;
        }

        log.info("[OsmPoiImport] {} elementos recebidos do Overpass para categoria={}",
                elements.size(), logicalCategory);

        List<String> createdOrUpdatedIds = new ArrayList<>();
        Set<String> seenOsmIds = new HashSet<>();
        List<String> sampleNames = new ArrayList<>();

        Iterator<JsonNode> it = elements.elements();
        while (it.hasNext()) {
            JsonNode el = it.next();

            try {
                String osmType = el.path("type").asText(null); // node/way/relation
                String osmIdRaw = el.path("id").asText(null);
                JsonNode tags = el.path("tags");

                if (osmIdRaw == null || !tags.isObject()) {
                    continue;
                }

                String externalOsmId = "osm:" + osmType + "/" + osmIdRaw;
                if (!seenOsmIds.add(externalOsmId)) {
                    // já vimos este OSM id noutra categoria / repetição
                    continue;
                }

                // nome (PT primeiro, depois genérico)
                String name = tags.path("name:pt").asText(null);
                if (name == null || name.isBlank()) {
                    name = tags.path("name").asText(null);
                }
                if (name == null || name.isBlank()) {
                    // ignorar sem nome (não dá jeito na app)
                    continue;
                }

                // recolher algumas amostras de nomes para o log
                if (sampleNames.size() < 5) {
                    sampleNames.add(name);
                }

                // coordenadas
                Double lat = null;
                Double lon = null;

                if ("node".equals(osmType)) {
                    if (el.hasNonNull("lat") && el.hasNonNull("lon")) {
                        lat = el.get("lat").asDouble();
                        lon = el.get("lon").asDouble();
                    }
                } else {
                    JsonNode center = el.path("center");
                    if (center.hasNonNull("lat") && center.hasNonNull("lon")) {
                        lat = center.get("lat").asDouble();
                        lon = center.get("lon").asDouble();
                    }
                }

                if (lat == null || lon == null) {
                    // sem coords válidas → ignora
                    continue;
                }

                String category = classifyCategory(tags);
                if (category == null) {
                    category = logicalCategory;
                }

                // Criar ou atualizar POI bruto
                var existingOpt = poiRepository.findByExternalOsmId(externalOsmId);

                if (existingOpt.isPresent()) {
                    Poi existing = existingOpt.get();
                    existing.setName(name);
                    existing.setNamePt(name);
                    existing.setCategory(category);
                    existing.setLat(lat);
                    existing.setLon(lon);
                    existing.setSource("osm");
                    poiRepository.save(existing);
                } else {
                    Poi poi = new Poi();
                    poi.setDistrict(null);       // distrito será inferido depois (enrichment)
                    poi.setName(name);
                    poi.setNamePt(name);
                    poi.setCategory(category);
                    poi.setSubcategory(null);
                    poi.setDescription(null);   // descrição virá do enriquecimento
                    poi.setLat(lat);
                    poi.setLon(lon);
                    poi.setWikipediaUrl(null);
                    poi.setSipaId(null);
                    poi.setExternalOsmId(externalOsmId);
                    poi.setSource("osm");
                    poiRepository.save(poi);
                }

                createdOrUpdatedIds.add(externalOsmId);

            } catch (Exception e) {
                log.error("[OsmPoiImport] Erro ao processar elemento OSM na categoria={}", logicalCategory, e);
            }
        }

        log.info("[OsmPoiImport] Criados/atualizados {} POIs brutos para categoria={} (Portugal)",
                createdOrUpdatedIds.size(), logicalCategory);

        if (!sampleNames.isEmpty()) {
            log.info("[OsmPoiImport] Exemplos de nomes importados para categoria={}: {}",
                    logicalCategory, sampleNames);
        }
    }

    /**
     * Mesma lógica que já tinhas antes no BE para classificar categoria a partir dos tags.
     */
    private String classifyCategory(JsonNode tags) {
        if (tags == null || !tags.isObject()) return null;

        String tourism = tags.path("tourism").asText(null);
        String leisure = tags.path("leisure").asText(null);
        String historic = tags.path("historic").asText(null);
        String building = tags.path("building").asText(null);
        String castleType = tags.path("castle_type").asText(null);
        String amenity = tags.path("amenity").asText(null);

        // miradouro
        if ("viewpoint".equals(tourism)) return "viewpoint";

        // parques/jardins
        if ("park".equals(leisure) ||
                "garden".equals(leisure) ||
                "recreation_ground".equals(leisure)) {
            return "park";
        }

        // palácios
        if ("palace".equals(historic) ||
                "palace".equals(building) ||
                "palace".equalsIgnoreCase(castleType)) {
            return "palace";
        }

        // castelos
        if ("castle".equals(historic) ||
                "castle".equals(building) ||
                (castleType != null && castleType.matches("(?i)castle|fortress"))) {
            return "castle";
        }

        // igrejas
        if ("church".equals(building) ||
                "cathedral".equals(building) ||
                "chapel".equals(building) ||
                "church".equals(historic) ||
                "chapel".equals(historic) ||
                "place_of_worship".equals(amenity)) {
            return "church";
        }

        // monument genérico
        if ("monument".equals(historic)) {
            return "monument";
        }

        return null;
    }
}