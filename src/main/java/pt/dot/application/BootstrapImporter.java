// src/main/java/pt/dot/application/BootstrapImporter.java
package pt.dot.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.service.DistrictMetadataImportService;
import pt.dot.application.service.OsmPoiImportService;
import pt.dot.application.service.PoiEnrichmentService;

@Component
public class BootstrapImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapImporter.class);

    private final DistrictMetadataImportService districtMetadataImportService;
    private final OsmPoiImportService osmPoiImportService;
    private final PoiEnrichmentService poiEnrichmentService;
    private final PoiRepository poiRepository;

    @Value("${ptdot.bootstrap.reset-pois:false}")
    private boolean resetPois;

    @Value("${ptdot.bootstrap.import-district-metadata:false}")
    private boolean importDistrictMetadata;

    @Value("${ptdot.bootstrap.import-osm-pois:false}")
    private boolean importOsmPois;

    @Value("${ptdot.bootstrap.enrich-pois:false}")
    private boolean enrichPois;

    public BootstrapImporter(DistrictMetadataImportService districtMetadataImportService,
                             OsmPoiImportService osmPoiImportService,
                             PoiEnrichmentService poiEnrichmentService,
                             PoiRepository poiRepository) {
        this.districtMetadataImportService = districtMetadataImportService;
        this.osmPoiImportService = osmPoiImportService;
        this.poiEnrichmentService = poiEnrichmentService;
        this.poiRepository = poiRepository;
    }

    @Override
    public void run(String... args) {
        log.info("[BootstrapImporter] Flags -> resetPois={}, distritos={}, osmPois={}, enrich={}",
                resetPois, importDistrictMetadata, importOsmPois, enrichPois);

        // 0) LIMPAR BD (POIs)
        if (resetPois) {
            log.warn("[BootstrapImporter] A limpar tabela de POIs (poi)...");
            poiRepository.deleteAll(); // se quiseres: deleteAllInBatch()
            log.warn("[BootstrapImporter] Tabela de POIs limpa.");
        } else {
            log.info("[BootstrapImporter] Limpeza de POIs desativada.");
        }

        // 1) distritos
        if (importDistrictMetadata) {
            log.info("[BootstrapImporter] 1/3 - Importar/atualizar metadata dos distritos...");
            districtMetadataImportService.importMetadataFromJson();
            log.info("[BootstrapImporter] Import dos distritos concluído.");
        } else {
            log.info("[BootstrapImporter] Import dos distritos desativado.");
        }

        // 2) POIs OSM brutos
        if (importOsmPois) {
            log.info("[BootstrapImporter] 2/3 - Importar POIs do OSM (Portugal inteiro)...");
            osmPoiImportService.importAllPortugal();
            log.info("[BootstrapImporter] Import de POIs OSM concluído.");
        } else {
            log.info("[BootstrapImporter] Import de POIs OSM desativado.");
        }

        // 3) Enriquecimento (SIPA + Wikipedia + atribuição de distrito)
        if (enrichPois) {
            log.info("[BootstrapImporter] 3/3 - Enriquecer POIs existentes (SIPA + Wikipedia)...");
            poiEnrichmentService.enrichAllExistingPois();
            log.info("[BootstrapImporter] Enriquecimento de POIs concluído.");
        } else {
            log.info("[BootstrapImporter] Enriquecimento de POIs desativado.");
        }
    }
}