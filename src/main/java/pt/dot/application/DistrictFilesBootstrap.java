// src/main/java/pt/dot/application/DistrictFilesBootstrap.java
package pt.dot.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pt.dot.application.service.DistrictFilesCsvImportService;

@Component
public class DistrictFilesBootstrap implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DistrictFilesBootstrap.class);

    private final DistrictFilesCsvImportService importService;
    private final boolean importDistrictFiles;

    public DistrictFilesBootstrap(
            DistrictFilesCsvImportService importService,
            @Value("${ptdot.bootstrap.import-district-files:false}")
            boolean importDistrictFiles
    ) {
        this.importService = importService;
        this.importDistrictFiles = importDistrictFiles;
    }

    @Override
    public void run(String... args) {
        if (!importDistrictFiles) {
            log.info("[DistrictFilesBootstrap] Import de district_files.csv desativado (ptdot.bootstrap.import-district-files=false)");
            return;
        }

        log.info("[DistrictFilesBootstrap] A importar ficheiros de distritos a partir do CSV…");
        importService.importFromCsv();
        log.info("[DistrictFilesBootstrap] Done.");
    }
}