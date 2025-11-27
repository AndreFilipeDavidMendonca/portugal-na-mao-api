// src/main/java/pt/dot/application/DistrictMetadataBootstrap.java
package pt.dot.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pt.dot.application.service.DistrictMetadataImportService;

@Component
public class DistrictMetadataBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DistrictMetadataBootstrap.class);

    private final DistrictMetadataImportService importService;

    // lê de application.yml → ptdot.bootstrap.import-district-metadata
    private final boolean importDistrictMetadata;

    public DistrictMetadataBootstrap(
            DistrictMetadataImportService importService,
            @Value("${ptdot.bootstrap.import-district-metadata:true}")
            boolean importDistrictMetadata
    ) {
        this.importService = importService;
        this.importDistrictMetadata = importDistrictMetadata;
    }

    @Override
    public void run(String... args) {
        if (!importDistrictMetadata) {
            log.info("[DistrictMetadataBootstrap] Import de metadata desativado (ptdot.bootstrap.import-district-metadata=false)");
            return;
        }

        log.info("[DistrictMetadataBootstrap] A importar metadata de distritos...");
        importService.importMetadataFromJson();
        log.info("[DistrictMetadataBootstrap] Done.");
    }
}