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

    // Índices do CSV pois.csv
    // id,name,name_pt,category,subcategory,architect,year,
    // description,wikipedia_url,sipa_id,lat,lon,source
    private static final int IDX_ID          = 0;
    private static final int IDX_NAME        = 1;
    private static final int IDX_NAME_PT     = 2;
    private static final int IDX_CATEGORY    = 3;
    private static final int IDX_SUBCATEGORY = 4;
    private static final int IDX_ARCHITECT   = 5;
    private static final int IDX_YEAR        = 6;
    private static final int IDX_DESC        = 7;
    private static final int IDX_WIKI        = 8;
    private static final int IDX_SIPA        = 9;
    private static final int IDX_LAT         = 10;
    private static final int IDX_LON         = 11;
    private static final int IDX_SOURCE      = 12;

    public PoiCsvSyncService(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    /**
     * Pequeno helper para obter campo por índice, já com trim.
     */
    private String field(CSVRecord record, int idx) {
        try {
            String v = record.get(idx);
            return v != null ? v.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void syncFromCsv(Resource poisCsv, Resource imagesCsv) {
        if (poisCsv == null || !poisCsv.exists()) {
            throw new IllegalArgumentException("CSV de POIs não encontrado: " + poisCsv);
        }

        log.info("[PoiCsvSync] Iniciar sync CSV={} imagens={}", poisCsv, imagesCsv);

        Map<Integer, Poi> poiByCsvId = importPois(poisCsv);

        importImages(imagesCsv, poiByCsvId);

        log.info("[PoiCsvSync] Concluído. POIs na BD: {}", poiRepository.count());
    }

    /**
     * Importa/atualiza POIs a partir de pois.csv.
     * - Usa o ID do CSV apenas como chave lógica (para o segundo CSV de imagens).
     * - Faz matching por SIPA ID (se existir) para atualizar em vez de duplicar.
     */
    private Map<Integer, Poi> importPois(Resource poisCsv) {
        Map<Integer, Poi> map = new HashMap<>();

        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;

        try (Reader reader = new BufferedReader(
                new InputStreamReader(poisCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .parse(reader);

            for (CSVRecord record : parser) {
                total++;

                Integer csvId       = parseInt(field(record, IDX_ID));
                String name         = field(record, IDX_NAME);
                String namePt       = field(record, IDX_NAME_PT);
                String category     = field(record, IDX_CATEGORY);
                String subcategory  = field(record, IDX_SUBCATEGORY);
                String architect    = field(record, IDX_ARCHITECT);
                String yearText     = field(record, IDX_YEAR);
                String description  = field(record, IDX_DESC);
                String wikipediaUrl = field(record, IDX_WIKI);
                String sipaId       = field(record, IDX_SIPA);
                Double lat          = parseDouble(field(record, IDX_LAT));
                Double lon          = parseDouble(field(record, IDX_LON));
                String source       = field(record, IDX_SOURCE);

                if (csvId == null) {
                    skipped++;
                    continue;
                }

                // Campos mínimos obrigatórios
                if (name == null || name.isBlank() || lat == null || lon == null) {
                    skipped++;
                    continue;
                }

                Poi poi = null;

                // MATCHING: SIPA ID → se existir, tentamos atualizar em vez de criar
                if (sipaId != null && !sipaId.isBlank()) {
                    poi = poiRepository.findBySipaId(sipaId).orElse(null);
                }

                if (poi == null) {
                    poi = new Poi();
                    created++;
                } else {
                    updated++;
                }

                poi.setName(name);
                poi.setNamePt(namePt != null && !namePt.isBlank() ? namePt : name);
                poi.setCategory(category);
                poi.setSubcategory(subcategory);
                poi.setDescription(description);
                poi.setLat(lat);
                poi.setLon(lon);
                poi.setWikipediaUrl(wikipediaUrl);
                poi.setSipaId(sipaId);
                poi.setSource(source != null && !source.isBlank() ? source : "csv:pois");

                // estes setters assumem que já tens os campos em Poi
                poi.setArchitect(architect);
                poi.setYearText(yearText);

                poi = poiRepository.save(poi);

                // ligamos o ID lógico do CSV a este POI (para o CSV de imagens)
                map.put(csvId, poi);
            }

            log.info(
                    "[PoiCsvSync] Linhas lidas={} criados={} atualizados={} ignorados={}",
                    total, created, updated, skipped
            );

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar POIs", e);
            throw new RuntimeException("Erro ao importar POIs", e);
        }

        return map;
    }

    /**
     * Importa imagens extra (poi_images.csv) e anexa a cada POI com base no id lógico do CSV.
     */
    private void importImages(Resource imagesCsv, Map<Integer, Poi> poiByCsvId) {
        if (imagesCsv == null || !imagesCsv.exists()) {
            log.warn("[PoiCsvSync] CSV de imagens não encontrado ({}), a ignorar.", imagesCsv);
            return;
        }

        int total = 0;
        int attached = 0;
        int missingPoi = 0;

        try (Reader reader = new BufferedReader(
                new InputStreamReader(imagesCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .parse(reader);

            for (CSVRecord record : parser) {
                total++;

                Integer csvId   = parseInt(trim(record, "poi_id"));
                String imageUrl = trim(record, "image_url");

                if (csvId == null || imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }

                Poi poi = poiByCsvId.get(csvId);
                if (poi == null) {
                    missingPoi++;
                    continue;
                }

                List<String> images = poi.getImages() != null
                        ? new ArrayList<>(poi.getImages())
                        : new ArrayList<>();

                if (!images.contains(imageUrl)) {
                    images.add(imageUrl);
                    poi.setImages(images);
                    poiRepository.save(poi);
                    attached++;
                }
            }

            log.info(
                    "[PoiCsvSync] Imagens: lidas={} anexadas={} poi_nao_encontrado={}",
                    total, attached, missingPoi
            );

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar imagens", e);
            throw new RuntimeException("Erro ao importar imagens", e);
        }
    }

    // Helpers genéricos

    private String trim(CSVRecord record, String header) {
        try {
            String v = record.get(header);
            return v != null ? v.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Double.parseDouble(v.trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}