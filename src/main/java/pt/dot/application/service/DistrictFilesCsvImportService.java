// src/main/java/pt/dot/application/service/DistrictFilesCsvImportService.java
package pt.dot.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Importa ficheiros (URLs/paths) para District.files a partir de um CSV.
 *
 * Formato esperado (mínimo 2 colunas):
 *   districtNamePt ; fileUrl
 * ou
 *   districtNamePt , fileUrl
 *
 * - ignora linhas vazias e comentários (#...)
 * - ignora o header (primeira linha) se detetar "district" ou "name" etc.
 * - tolera colunas extra (usa só as 2 primeiras)
 */
@Service
public class DistrictFilesCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(DistrictFilesCsvImportService.class);

    private final DistrictRepository districtRepository;
    private final ResourceLoader resourceLoader;
    private final String csvPath;

    public DistrictFilesCsvImportService(
            DistrictRepository districtRepository,
            ResourceLoader resourceLoader,
            @Value("${ptdot.sipa.district-files-csv-path:classpath:/db/sipa/district_files.csv}") String csvPath
    ) {
        this.districtRepository = districtRepository;
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

        // guarda as alterações por distrito e faz saveAll no fim
        Map<Long, District> dirty = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            boolean maybeHeader = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                // detetar header na primeira linha "real"
                if (maybeHeader) {
                    maybeHeader = false;
                    String l = line.toLowerCase(Locale.ROOT);
                    if (l.contains("district") || l.contains("name") || l.contains("ficheiro") || l.contains("file")) {
                        // header detectado -> skip
                        continue;
                    }
                }

                totalLines++;

                ParsedLine parsed = parseLine(line);
                if (parsed == null) {
                    invalidLines++;
                    log.debug("[DistrictFilesCsvImport] Linha inválida: '{}'", line);
                    continue;
                }

                String districtNamePt = parsed.districtNamePt();
                String fileUrl = parsed.fileUrl();

                Optional<District> opt = districtRepository.findByNamePtIgnoreCase(districtNamePt);
                if (opt.isEmpty()) {
                    missingDistricts++;
                    log.debug("[DistrictFilesCsvImport] Distrito '{}' não encontrado (linha='{}')", districtNamePt, line);
                    continue;
                }

                District d = opt.get();
                if (d.getFiles() == null) d.setFiles(new ArrayList<>());

                if (!d.getFiles().contains(fileUrl)) {
                    d.getFiles().add(fileUrl);
                    attachedFiles++;
                    dirty.put(d.getId(), d);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("[DistrictFilesCsvImport] Erro a ler CSV", e);
        }

        if (!dirty.isEmpty()) {
            districtRepository.saveAll(dirty.values());
        }

        log.info("[DistrictFilesCsvImport] Concluído (lines={}, updatedDistricts={}, attached={}, missingDistricts={}, invalidLines={})",
                totalLines, dirty.size(), attachedFiles, missingDistricts, invalidLines);

        return new ImportResult(totalLines, dirty.size(), attachedFiles, missingDistricts, invalidLines);
    }

    private record ParsedLine(String districtNamePt, String fileUrl) {}

    /**
     * Aceita:
     *  - "Aveiro;https://..." ou "Aveiro,https://..."
     *  - tolera espaços
     *  - tolera colunas extra (divide em 2)
     */
    private ParsedLine parseLine(String line) {
        // split por ; ou , mas só nas 2 primeiras colunas
        String[] parts = line.split("[;,]", 2);
        if (parts.length < 2) return null;

        String district = parts[0].trim();
        String file = parts[1].trim();

        if (district.isEmpty() || file.isEmpty()) return null;

        // normalização mínima
        district = normalizeDistrictName(district);
        file = normalizeFileUrl(file);

        if (district.isEmpty() || file.isEmpty()) return null;

        return new ParsedLine(district, file);
    }

    private String normalizeDistrictName(String s) {
        return s.trim();
    }

    private String normalizeFileUrl(String s) {
        // remove aspas simples/duplas de CSVs manhosos
        String out = s.trim();
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }
}