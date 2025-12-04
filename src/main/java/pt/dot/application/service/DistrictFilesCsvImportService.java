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
import java.util.Optional;

@Service
public class DistrictFilesCsvImportService {

    private static final Logger log =
            LoggerFactory.getLogger(DistrictFilesCsvImportService.class);

    private final DistrictRepository districtRepository;
    private final ResourceLoader resourceLoader;
    private final String csvPath;

    // path configurável em application.yml (ver em baixo)
    public DistrictFilesCsvImportService(
            DistrictRepository districtRepository,
            ResourceLoader resourceLoader,
            @Value("${ptdot.sipa.district-files-csv-path:classpath:/db/sipa/district_files.csv}")
            String csvPath
    ) {
        this.districtRepository = districtRepository;
        this.resourceLoader = resourceLoader;
        this.csvPath = csvPath;
    }

    @Transactional
    public void importFromCsv() {
        log.info("[DistrictFilesCsvImport] A carregar CSV de '{}'", csvPath);

        Resource res = resourceLoader.getResource(csvPath);
        if (!res.exists()) {
            log.warn("[DistrictFilesCsvImport] Recurso '{}' não encontrado", csvPath);
            return;
        }

        int totalLines = 0;
        int attachedFiles = 0;
        int missingDistricts = 0;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // salta o header
                if (first) {
                    first = false;
                    continue;
                }

                totalLines++;

                // suporta CSV separado por vírgula ou ponto-e-vírgula
                String[] parts = line.split("[;,]", 2);
                if (parts.length < 2) {
                    log.warn("[DistrictFilesCsvImport] Linha inválida (sem 2 colunas): '{}'", line);
                    continue;
                }

                String districtNamePt = parts[0].trim();
                String fileName = parts[1].trim();

                if (districtNamePt.isEmpty() || fileName.isEmpty()) {
                    continue;
                }

                Optional<District> opt =
                        districtRepository.findByNamePtIgnoreCase(districtNamePt);

                if (opt.isEmpty()) {
                    log.warn(
                            "[DistrictFilesCsvImport] Distrito '{}' não encontrado na BD (linha: '{}')",
                            districtNamePt, line
                    );
                    missingDistricts++;
                    continue;
                }

                District d = opt.get();

                if (d.getFiles() == null) {
                    d.setFiles(new java.util.ArrayList<>());
                }

                if (!d.getFiles().contains(fileName)) {
                    d.getFiles().add(fileName);
                    districtRepository.save(d);
                    attachedFiles++;
                    log.debug(
                            "[DistrictFilesCsvImport] + ficheiro '{}' -> distrito id={} ({})",
                            fileName, d.getId(), d.getNamePt()
                    );
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "[DistrictFilesCsvImport] Erro a ler CSV de distritos", e
            );
        }

        log.info(
                "[DistrictFilesCsvImport] Concluído. Linhas lidas: {}, ficheiros associados: {}, distritos não encontrados: {}",
                totalLines, attachedFiles, missingDistricts
        );
    }
}