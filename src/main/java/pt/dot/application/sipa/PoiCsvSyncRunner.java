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

    private final PoiCsvSyncService poiCsvSyncService;
    private final ResourceLoader resourceLoader;

    private final boolean syncPoisFromCsv;
    private final String poisCsvPath;
    private final String poiImagesCsvPath;

    public PoiCsvSyncRunner(
            PoiCsvSyncService poiCsvSyncService,
            ResourceLoader resourceLoader,
            @Value("${ptdot.bootstrap.sync-pois-from-csv:false}") boolean syncPoisFromCsv,
            @Value("${ptdot.sipa.pois-csv-path:classpath:/sipa/pois.csv}") String poisCsvPath,
            @Value("${ptdot.sipa.poi-images-csv-path:classpath:/sipa/poi_images.csv}") String poiImagesCsvPath
    ) {
        this.poiCsvSyncService = poiCsvSyncService;
        this.resourceLoader = resourceLoader;
        this.syncPoisFromCsv = syncPoisFromCsv;
        this.poisCsvPath = poisCsvPath;
        this.poiImagesCsvPath = poiImagesCsvPath;
    }

    @Override
    public void run(String... args) {
        if (!syncPoisFromCsv) {
            log.info("[POI-CSV] sync desativado (ptdot.bootstrap.sync-pois-from-csv=false).");
            return;
        }

        Resource poisResource = resourceLoader.getResource(poisCsvPath);
        Resource imagesResource = resourceLoader.getResource(poiImagesCsvPath);

        if (!poisResource.exists()) {
            log.error("[POI-CSV] CSV de POIs não encontrado: {}", poisCsvPath);
            return;
        }

        log.info("[POI-CSV] Sync a partir de POIs='{}' e imagens='{}'", poisCsvPath, poiImagesCsvPath);

        try {
            poiCsvSyncService.syncFromCsv(poisResource, imagesResource);
        } catch (Exception e) {
            log.error("[POI-CSV] Falha no sync a partir de CSV", e);
        }
    }
}