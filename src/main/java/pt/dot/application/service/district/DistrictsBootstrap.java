// src/main/java/pt/dot/application/service/DistrictsBootstrap.java
package pt.dot.application.service.district;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class DistrictsBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DistrictsBootstrap.class);

    private final DistrictsCsvImportService importService;
    private final boolean enabled;

    public DistrictsBootstrap(
            DistrictsCsvImportService importService,
            @Value("${ptdot.bootstrap.import-districts:false}") boolean enabled
    ) {
        this.importService = importService;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[DistrictsBootstrap] Skip (ptdot.bootstrap.import-districts=false)");
            return;
        }

        log.info("[DistrictsBootstrap] Import districts.csv: START");
        var res = importService.importFromCsv();
        log.info("[DistrictsBootstrap] Import districts.csv: DONE (rows={}, created={}, updated={}, invalid={})",
                res.totalRows(), res.created(), res.updated(), res.invalidRows());
    }
}