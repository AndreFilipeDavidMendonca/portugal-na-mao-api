// src/main/java/pt/dot/application/service/poi/PoiCsvSyncService.java
package pt.dot.application.service.poi;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.service.media.MediaItemService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PoiCsvSyncService {

    private static final Logger log = LoggerFactory.getLogger(PoiCsvSyncService.class);

    private static final int MAX_CSV_IMAGES_PER_POI = 20;

    private final PoiRepository poiRepository;
    private final MediaItemService mediaItemService;

    public PoiCsvSyncService(
            PoiRepository poiRepository,
            MediaItemService mediaItemService
    ) {
        this.poiRepository = poiRepository;
        this.mediaItemService = mediaItemService;
    }

    @Transactional
    public void syncFromCsv(Resource poisCsv, Resource imagesCsv) {
        if (poisCsv == null || !poisCsv.exists()) {
            throw new IllegalArgumentException("CSV de POIs não encontrado: " + safeName(poisCsv));
        }

        log.info("[PoiCsvSync] Início | poisCsv={} | imagesCsv={}", safeName(poisCsv), safeName(imagesCsv));

        Map<Integer, Poi> poiByCsvId = importPois(poisCsv);
        importImages(imagesCsv, poiByCsvId);

        log.info("[PoiCsvSync] Fim | total POIs na BD={}", poiRepository.count());
    }

    private Map<Integer, Poi> importPois(Resource poisCsv) {
        Map<Integer, Poi> csvIdToPoi = new HashMap<>();

        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;

        List<Poi> toSave = new ArrayList<>();

        try (Reader reader = new BufferedReader(
                new InputStreamReader(poisCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(reader);

            for (CSVRecord r : parser) {
                total++;

                Integer csvId = parseInt(get(r, "id"));
                String name = get(r, "name");
                String namePt = get(r, "name_pt");
                String category = get(r, "category");
                String subcategory = get(r, "subcategory");
                String architect = get(r, "architect");
                String yearText = get(r, "year");
                String description = get(r, "description");
                String wikipediaUrl = get(r, "wikipedia_url");
                String sipaId = get(r, "sipa_id");
                Double lat = parseDouble(get(r, "lat"));
                Double lon = parseDouble(get(r, "lon"));
                String source = get(r, "source");

                if (csvId == null) {
                    skipped++;
                    continue;
                }

                if (isBlank(name) || lat == null || lon == null) {
                    skipped++;
                    continue;
                }

                Poi poi = null;

                if (!isBlank(sipaId)) {
                    poi = poiRepository.findBySipaId(sipaId).orElse(null);
                }

                if (poi == null) {
                    poi = new Poi();
                    created++;
                } else {
                    updated++;
                }

                poi.setName(name);
                poi.setNamePt(!isBlank(namePt) ? namePt : name);
                poi.setCategory(category);
                poi.setSubcategory(subcategory);
                poi.setDescription(description);
                poi.setLat(lat);
                poi.setLon(lon);
                poi.setWikipediaUrl(wikipediaUrl);
                poi.setSipaId(sipaId);
                poi.setSource(!isBlank(source) ? source : "csv:pois");
                poi.setArchitect(architect);
                poi.setYearText(yearText);

                toSave.add(poi);
                csvIdToPoi.put(csvId, poi);
            }

            List<Poi> savedPois = poiRepository.saveAll(toSave);

            // Garante que os POIs novos têm ID antes de importImages().
            // Como a ordem de saveAll tende a preservar a ordem de input, mantemos o map atualizado.
            int i = 0;
            for (Integer csvId : new ArrayList<>(csvIdToPoi.keySet())) {
                if (i < savedPois.size()) {
                    csvIdToPoi.put(csvId, savedPois.get(i));
                }
                i++;
            }

            log.info("[PoiCsvSync] POIs | lidos={} | criados={} | atualizados={} | ignorados={}",
                    total, created, updated, skipped);

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar POIs", e);
            throw new RuntimeException("Erro ao importar POIs", e);
        }

        return csvIdToPoi;
    }

    private void importImages(Resource imagesCsv, Map<Integer, Poi> poiByCsvId) {
        if (imagesCsv == null || !imagesCsv.exists()) {
            log.warn("[PoiCsvSync] CSV de imagens não encontrado, a ignorar. ({})", safeName(imagesCsv));
            return;
        }

        int total = 0;
        int attached = 0;
        int missingPoi = 0;
        int skipped = 0;

        Map<Long, LinkedHashSet<String>> imagesByPoiId = new HashMap<>();

        try (Reader reader = new BufferedReader(
                new InputStreamReader(imagesCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(reader);

            for (CSVRecord r : parser) {
                total++;

                Integer csvId = parseInt(get(r, "poi_id"));
                String imageUrl = get(r, "image_url");

                if (csvId == null || isBlank(imageUrl)) {
                    skipped++;
                    continue;
                }

                Poi poi = poiByCsvId.get(csvId);
                if (poi == null || poi.getId() == null) {
                    missingPoi++;
                    continue;
                }

                imagesByPoiId
                        .computeIfAbsent(poi.getId(), ignored -> new LinkedHashSet<>())
                        .add(imageUrl);
            }

            for (Map.Entry<Long, LinkedHashSet<String>> entry : imagesByPoiId.entrySet()) {
                Long poiId = entry.getKey();

                List<String> existing = mediaItemService.getStorageKeys(
                        MediaItemService.ENTITY_POI,
                        poiId,
                        MediaItemService.MEDIA_IMAGE,
                        MAX_CSV_IMAGES_PER_POI
                );

                LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
                merged.addAll(entry.getValue());

                List<String> finalImages = merged.stream()
                        .filter(v -> !isBlank(v))
                        .limit(MAX_CSV_IMAGES_PER_POI)
                        .toList();

                mediaItemService.replaceMedia(
                        MediaItemService.ENTITY_POI,
                        poiId,
                        MediaItemService.MEDIA_IMAGE,
                        finalImages,
                        MediaItemService.PROVIDER_CSV
                );

                attached += entry.getValue().size();
            }

            log.info("[PoiCsvSync] Imagens | lidas={} | anexadas={} | poi_nao_encontrado={} | ignoradas={}",
                    total, attached, missingPoi, skipped);

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar imagens", e);
            throw new RuntimeException("Erro ao importar imagens", e);
        }
    }

    private static String safeName(Resource r) {
        return r == null ? "null" : r.getDescription();
    }

    private static String get(CSVRecord r, String header) {
        try {
            String v = r.get(header);
            return v != null ? v.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static Integer parseInt(String v) {
        if (isBlank(v)) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String v) {
        if (isBlank(v)) return null;
        try {
            return Double.parseDouble(v.trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}