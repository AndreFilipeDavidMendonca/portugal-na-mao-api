// src/main/java/pt/dot/application/sipa/PoiCsvSyncRunner.java
package pt.dot.application.sipa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pt.dot.application.service.PoiCsvSyncService;

@Component
public class PoiCsvSyncRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PoiCsvSyncRunner.class);

    @Value("${ptdot.bootstrap.sync-pois-from-csv:false}")
    private boolean syncPoisFromCsv;

    @Value("${ptdot.sipa.pois-csv-path:classpath:/sipa/pois_full.csv}")
    private String poisCsvPath;

    @Value("${ptdot.sipa.poi-images-csv-path:classpath:/sipa/poi_images.csv}")
    private String poiImagesCsvPath;

    private final PoiCsvSyncService poiCsvSyncService;
    private final ResourceLoader resourceLoader;

    public PoiCsvSyncRunner(PoiCsvSyncService poiCsvSyncService,
                            ResourceLoader resourceLoader) {
        this.poiCsvSyncService = poiCsvSyncService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) {
        if (!syncPoisFromCsv) {
            log.info("[POI-CSV] Flag ptdot.bootstrap.sync-pois-from-csv=false, a saltar sync.");
            return;
        }

        try {
            Resource poisResource = resourceLoader.getResource(poisCsvPath);
            Resource imagesResource = resourceLoader.getResource(poiImagesCsvPath);

            log.info("[POI-CSV] A carregar POIs de '{}'", poisCsvPath);
            log.info("[POI-CSV] A carregar imagens extra de '{}'", poiImagesCsvPath);

            poiCsvSyncService.syncFromCsv(poisResource, imagesResource);
        } catch (Exception e) {
            log.error("[POI-CSV] Erro no runner de sync de POIs a partir dos CSVs", e);
        }
    }
}