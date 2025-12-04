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

    /**
     * Lê:
     *  - CSV de POIs (pois_full.csv)
     *  - CSV de imagens extra (poi_images.csv)
     *
     * Cria/actualiza a tabela poi com:
     *  - name/namePt/category/subcategory/description
     *  - wikipediaUrl, sipaId
     *  - image (main_image) + lista images (main_image + extra)
     *
     * O campo "id" do CSV é apenas um ID lógico para ligar ao CSV de imagens.
     */
    @Transactional
    public void syncFromCsv(Resource poisCsv, Resource imagesCsv) {
        if (poisCsv == null || !poisCsv.exists()) {
            throw new IllegalArgumentException("CSV de POIs não encontrado: " + poisCsv);
        }

        log.info("[PoiCsvSync] Iniciar sync a partir de POIs={} imagens={}", poisCsv, imagesCsv);

        // 1) importar POIs base e manter mapa csvId -> Poi
        Map<Integer, Poi> poiByCsvId = importPois(poisCsv);

        // 2) importar imagens extra e anexar a cada Poi
        importImages(imagesCsv, poiByCsvId);

        long count = poiRepository.count();
        log.info("[PoiCsvSync] Sincronização concluída. POIs na BD: {}", count);
    }

    // ---------------------------------------------------------
    // 1) Importar POIs base (pois_full.csv)
    // ---------------------------------------------------------
    private Map<Integer, Poi> importPois(Resource poisCsv) {
        Map<Integer, Poi> map = new HashMap<>();

        int total = 0;
        int created = 0;

        try (Reader reader = new BufferedReader(
                new InputStreamReader(poisCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .parse(reader);

            for (CSVRecord record : parser) {
                total++;

                Integer csvId = parseInt(trim(record, "id"));
                if (csvId == null) {
                    log.warn("[PoiCsvSync] Linha {} sem 'id' válido, a ignorar.", total);
                    continue;
                }

                String name = trim(record, "name");
                String namePt = trim(record, "name_pt");
                String category = trim(record, "category");
                String subcategory = trim(record, "subcategory");
                String description = trim(record, "description");
                String wikipediaUrl = trim(record, "wikipedia_url");
                String sipaId = trim(record, "sipa_id");
                Double lat = parseDouble(trim(record, "lat"));
                Double lon = parseDouble(trim(record, "lon"));
                String source = trim(record, "source");
                String mainImage = trim(record, "main_image");

                if (name == null || name.isBlank() || lat == null || lon == null) {
                    log.warn("[PoiCsvSync] Linha {} com campos obrigatórios em falta (name/lat/lon).", total);
                    continue;
                }

                // Para simplificar: assumimos que já limpaste a tabela poi antes.
                // Criamos sempre um novo POI. (Se quiseres matching, depois afinamos.)
                Poi poi = new Poi();
                poi.setName(name);
                poi.setNamePt((namePt != null && !namePt.isBlank()) ? namePt : name);
                poi.setCategory(category);
                poi.setSubcategory(subcategory);
                poi.setDescription(description);
                poi.setLat(lat);
                poi.setLon(lon);
                poi.setWikipediaUrl(wikipediaUrl);
                poi.setSipaId(sipaId);
                poi.setSource((source != null && !source.isBlank()) ? source : "csv:pois");

                // imagem principal
                List<String> images = new ArrayList<>();
                if (mainImage != null && !mainImage.isBlank()) {
                    poi.setImage(mainImage);
                    images.add(mainImage);
                }
                poi.setImages(images.isEmpty() ? null : images);

                Poi saved = poiRepository.save(poi);
                created++;

                map.put(csvId, saved);
            }

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar POIs do CSV", e);
            throw new RuntimeException("Erro ao importar POIs do CSV", e);
        }

        log.info("[PoiCsvSync] POIs base importados: totalLinhas={}, criados={}", total, created);
        return map;
    }

    // ---------------------------------------------------------
    // 2) Importar imagens extra (poi_images.csv)
    // ---------------------------------------------------------
    private void importImages(Resource imagesCsv, Map<Integer, Poi> poiByCsvId) {
        if (imagesCsv == null || !imagesCsv.exists()) {
            log.warn("[PoiCsvSync] CSV de imagens não encontrado ({}), a ignorar.", imagesCsv);
            return;
        }

        int total = 0;
        int attached = 0;

        try (Reader reader = new BufferedReader(
                new InputStreamReader(imagesCsv.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withDelimiter(',')
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .parse(reader);

            for (CSVRecord record : parser) {
                total++;

                Integer csvId = parseInt(trim(record, "poi_id"));
                String imageUrl = trim(record, "image_url");

                if (csvId == null || imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }

                Poi poi = poiByCsvId.get(csvId);
                if (poi == null) {
                    // Se não encontrar, é porque o id do CSV não bate certo com o 1º ficheiro
                    log.warn("[PoiCsvSync] Imagem com poi_id={} mas POI não encontrado (linha {}).", csvId, total);
                    continue;
                }

                List<String> images = poi.getImages() != null
                        ? new ArrayList<>(poi.getImages())
                        : new ArrayList<>();

                if (!images.contains(imageUrl)) {
                    images.add(imageUrl);
                    poi.setImages(images);
                    // não mexemos em poi.image aqui (main_image já tratada antes)
                    poiRepository.save(poi);
                    attached++;
                }
            }

        } catch (Exception e) {
            log.error("[PoiCsvSync] Erro ao importar imagens extra do CSV", e);
            throw new RuntimeException("Erro ao importar imagens extra do CSV", e);
        }

        log.info("[PoiCsvSync] Imagens extra: linhasLidas={}, anexadas={}", total, attached);
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------
    private String trim(CSVRecord record, String header) {
        try {
            String v = record.get(header);
            return v != null ? v.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        String norm = value.replace(",", ".");
        try {
            return Double.parseDouble(norm);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}