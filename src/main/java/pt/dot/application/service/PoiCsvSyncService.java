// src/main/java/pt/dot/application/service/PoiCsvSyncService.java
package pt.dot.application.service;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PoiCsvSyncService {

    private static final Logger log = LoggerFactory.getLogger(PoiCsvSyncService.class);

    private final PoiRepository poiRepository;

    public PoiCsvSyncService(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    @Transactional
    public void syncFromCsv(Resource poisCsv, Resource imagesCsv) {
        if (poisCsv == null || !poisCsv.exists()) {
            throw new IllegalArgumentException("CSV de POIs não encontrado: " + poisCsv);
        }

        log.info("[PoiCsvSync] Início | poisCsv={} | imagesCsv={}", safeName(poisCsv), safeName(imagesCsv));

        Map<Integer, Poi> poiByCsvId = importPois(poisCsv);
        importImages(imagesCsv, poiByCsvId);

        log.info("[PoiCsvSync] Fim | total POIs na BD={}", poiRepository.count());
    }

    private Map<Integer, Poi> importPois(Resource poisCsv) {
        Map<Integer, Poi> csvIdToPoi = new HashMap<>();

        int total = 0, created = 0, updated = 0, skipped = 0;

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

                // mínimos
                if (isBlank(name) || lat == null || lon == null) {
                    skipped++;
                    continue;
                }

                Poi poi = null;

                // MATCHING primário: SIPA ID
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

                // extra
                poi.setArchitect(architect);
                poi.setYearText(yearText);

                toSave.add(poi);

                // associação csvId -> poi (depois de salvar, fazemos map com o "saved")
                // por enquanto guardamos o csvId num map auxiliar
                csvIdToPoi.put(csvId, poi);
            }

            // save batch (1 roundtrip grande)
            List<Poi> saved = poiRepository.saveAll(toSave);

            // IMPORTANT: o map csvIdToPoi já aponta para os objetos que foram saved (mesmas referências),
            // mas para ser super explícito, garantimos que estão “managed”.
            // (Não precisamos de re-map por índice; o JPA mantém identidade no mesmo contexto)
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

        int total = 0, attached = 0, missingPoi = 0, skipped = 0;

        // batch: acumulamos alterações por POI e fazemos saveAll no fim
        Set<Poi> touched = new HashSet<>();

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
                if (poi == null) {
                    missingPoi++;
                    continue;
                }

                // mantém ordem e evita duplicados
                LinkedHashSet<String> set = new LinkedHashSet<>();
                if (poi.getImages() != null) set.addAll(poi.getImages());
                set.add(imageUrl);

                int before = poi.getImages() != null ? poi.getImages().size() : 0;
                List<String> newList = new ArrayList<>(set);

                if (newList.size() != before) {
                    poi.setImages(newList);
                    touched.add(poi);
                    attached++;
                }
            }

            if (!touched.isEmpty()) {
                poiRepository.saveAll(touched);
            }

            log.info("[PoiCsvSync] Imagens | lidas={} | anexadas={} | poi_nao_encontrado={} | ignoradas={}",
                    total, attached, missingPoi, skipped);

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar imagens", e);
            throw new RuntimeException("Erro ao importar imagens", e);
        }
    }

    // ---------------- helpers ----------------

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
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return null; }
    }

    private static Double parseDouble(String v) {
        if (isBlank(v)) return null;
        try { return Double.parseDouble(v.trim().replace(",", ".")); } catch (Exception e) { return null; }
    }
}