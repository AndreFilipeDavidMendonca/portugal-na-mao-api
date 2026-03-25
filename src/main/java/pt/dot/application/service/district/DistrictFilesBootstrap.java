// src/main/java/pt/dot/application/service/DistrictFilesBootstrap.java
package pt.dot.application.service.district;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class DistrictFilesBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DistrictFilesBootstrap.class);

    private final DistrictFilesCsvImportService importService;
    private final boolean enabled;

    public DistrictFilesBootstrap(
            DistrictFilesCsvImportService importService,
            @Value("${ptdot.bootstrap.import-district-files:false}") boolean enabled
    ) {
        this.importService = importService;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[DistrictFilesBootstrap] Skip (ptdot.bootstrap.import-district-files=false)");
            return;
        }

        log.info("[DistrictFilesBootstrap] Import district_files.csv: START");
        var result = importService.importFromCsv();
        log.info("[DistrictFilesBootstrap] Import district_files.csv: DONE (lines={}, updatedDistricts={}, attached={}, missingDistricts={}, invalidLines={})",
                result.totalLines(), result.updatedDistricts(), result.attachedFiles(), result.missingDistricts(), result.invalidLines());
    }
}