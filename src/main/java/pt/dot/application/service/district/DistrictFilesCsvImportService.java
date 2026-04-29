// src/main/java/pt/dot/application/service/DistrictFilesCsvImportService.java
package pt.dot.application.service.district;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.service.media.MediaItemService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DistrictFilesCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(DistrictFilesCsvImportService.class);
    private static final int MAX_DISTRICT_FILES = 20;

    private final DistrictRepository districtRepository;
    private final MediaItemService mediaItemService;
    private final ResourceLoader resourceLoader;
    private final String csvPath;

    public DistrictFilesCsvImportService(
            DistrictRepository districtRepository,
            MediaItemService mediaItemService,
            ResourceLoader resourceLoader,
            @Value("${ptdot.sipa.district-files-csv-path:classpath:/sipa/district_files.csv}") String csvPath
    ) {
        this.districtRepository = districtRepository;
        this.mediaItemService = mediaItemService;
        this.resourceLoader = resourceLoader;
        this.csvPath = csvPath;
    }

    public record ImportResult(
            int totalLines,
            int updatedDistricts,
            int attachedFiles,
            int missingDistricts,
            int invalidLines
    ) {}

    /**
     * CSV simples 2 colunas:
     * districtNamePt ; fileUrl
     * ou
     * districtNamePt , fileUrl
     */
    @Transactional
    public ImportResult importFromCsv() {
        log.info("[DistrictFilesCsvImport] A carregar CSV de '{}'", csvPath);

        Resource res = resourceLoader.getResource(csvPath);
        if (!res.exists()) {
            log.warn("[DistrictFilesCsvImport] Recurso '{}' não encontrado", csvPath);
            return new ImportResult(0, 0, 0, 0, 0);
        }

        int totalLines = 0;
        int attachedFiles = 0;
        int missingDistricts = 0;
        int invalidLines = 0;

        Map<Long, LinkedHashSet<String>> filesByDistrictId = new LinkedHashMap<>();

        char delimiter = detectDelimiter(res);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setCommentMarker('#')
                .build();

        try (Reader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            boolean maybeHeader = true;

            for (CSVRecord r : parser) {
                if (r.size() == 0) continue;

                String dName = safeGet(r, 0);
                String fileUrl = safeGet(r, 1);

                if (isBlank(dName) && isBlank(fileUrl)) continue;

                if (maybeHeader) {
                    maybeHeader = false;
                    String l = (dName + " " + fileUrl).toLowerCase(Locale.ROOT);
                    if (l.contains("district") || l.contains("name") || l.contains("ficheiro") || l.contains("file")) {
                        continue;
                    }
                }

                totalLines++;

                if (isBlank(dName) || isBlank(fileUrl)) {
                    invalidLines++;
                    continue;
                }

                dName = dName.trim();
                fileUrl = stripQuotes(fileUrl);

                Optional<District> opt = districtRepository.findByNamePtIgnoreCase(dName);
                if (opt.isEmpty()) {
                    missingDistricts++;
                    continue;
                }

                District d = opt.get();
                if (d.getId() == null) {
                    missingDistricts++;
                    continue;
                }

                filesByDistrictId
                        .computeIfAbsent(d.getId(), ignored -> new LinkedHashSet<>())
                        .add(fileUrl);

                attachedFiles++;
            }

        } catch (Exception e) {
            throw new IllegalStateException("[DistrictFilesCsvImport] Erro a ler/importar CSV", e);
        }

        for (Map.Entry<Long, LinkedHashSet<String>> entry : filesByDistrictId.entrySet()) {
            Long districtId = entry.getKey();

            List<String> existing = mediaItemService.getStorageKeys(
                    MediaItemService.ENTITY_DISTRICT,
                    districtId,
                    MediaItemService.MEDIA_IMAGE,
                    MAX_DISTRICT_FILES
            );

            LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
            merged.addAll(entry.getValue());

            List<String> finalFiles = merged.stream()
                    .filter(v -> !isBlank(v))
                    .limit(MAX_DISTRICT_FILES)
                    .toList();

            mediaItemService.replaceMedia(
                    MediaItemService.ENTITY_DISTRICT,
                    districtId,
                    MediaItemService.MEDIA_IMAGE,
                    finalFiles,
                    MediaItemService.PROVIDER_CSV
            );
        }

        log.info("[DistrictFilesCsvImport] Concluído (lines={}, updatedDistricts={}, attached={}, missingDistricts={}, invalidLines={})",
                totalLines, filesByDistrictId.size(), attachedFiles, missingDistricts, invalidLines);

        return new ImportResult(totalLines, filesByDistrictId.size(), attachedFiles, missingDistricts, invalidLines);
    }

    private static String safeGet(CSVRecord r, int idx) {
        if (idx >= r.size()) return "";
        String v = r.get(idx);
        return v == null ? "" : v.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String stripQuotes(String s) {
        String out = s == null ? "" : s.trim();
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static char detectDelimiter(Resource res) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                long semi = line.chars().filter(ch -> ch == ';').count();
                long comma = line.chars().filter(ch -> ch == ',').count();
                return semi > comma ? ';' : ',';
            }
        } catch (Exception ignored) {}
        return ';';
    }
}
