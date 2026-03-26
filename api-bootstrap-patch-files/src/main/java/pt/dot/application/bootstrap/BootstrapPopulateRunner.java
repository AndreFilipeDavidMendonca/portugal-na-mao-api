package pt.dot.application.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pt.dot.application.config.BootstrapFlywayConfig;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.service.district.DistrictFilesCsvImportService;
import pt.dot.application.service.district.DistrictsCsvImportService;
import pt.dot.application.service.poi.PoiCsvSyncService;

import javax.sql.DataSource;

@Component
@Order(5)
public class BootstrapPopulateRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapPopulateRunner.class);

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final DistrictRepository districtRepository;
    private final PoiRepository poiRepository;
    private final DistrictsCsvImportService districtsCsvImportService;
    private final DistrictFilesCsvImportService districtFilesCsvImportService;
    private final PoiCsvSyncService poiCsvSyncService;

    private final boolean populate;
    private final boolean populateIfEmpty;
    private final boolean populateDistricts;
    private final boolean populateDistrictFiles;
    private final boolean populatePois;
    private final String populateScriptPath;
    private final String poisCsvPath;
    private final String poiImagesCsvPath;

    public BootstrapPopulateRunner(
            DataSource dataSource,
            ResourceLoader resourceLoader,
            DistrictRepository districtRepository,
            PoiRepository poiRepository,
            DistrictsCsvImportService districtsCsvImportService,
            DistrictFilesCsvImportService districtFilesCsvImportService,
            PoiCsvSyncService poiCsvSyncService,
            @Value("${ptdot.bootstrap.populate:false}") boolean populate,
            @Value("${ptdot.bootstrap.populate-if-empty:true}") boolean populateIfEmpty,
            @Value("${ptdot.bootstrap.populate-districts:true}") boolean populateDistricts,
            @Value("${ptdot.bootstrap.populate-district-files:true}") boolean populateDistrictFiles,
            @Value("${ptdot.bootstrap.populate-pois:true}") boolean populatePois,
            @Value("${ptdot.bootstrap.populate-script-path:classpath:/db/bootstrap/populate_pt_dot.sql}") String populateScriptPath,
            @Value("${ptdot.sipa.pois-csv-path:classpath:/sipa/pois.csv}") String poisCsvPath,
            @Value("${ptdot.sipa.poi-images-csv-path:classpath:/sipa/poi_images.csv}") String poiImagesCsvPath
    ) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.districtRepository = districtRepository;
        this.poiRepository = poiRepository;
        this.districtsCsvImportService = districtsCsvImportService;
        this.districtFilesCsvImportService = districtFilesCsvImportService;
        this.poiCsvSyncService = poiCsvSyncService;
        this.populate = populate;
        this.populateIfEmpty = populateIfEmpty;
        this.populateDistricts = populateDistricts;
        this.populateDistrictFiles = populateDistrictFiles;
        this.populatePois = populatePois;
        this.populateScriptPath = populateScriptPath;
        this.poisCsvPath = poisCsvPath;
        this.poiImagesCsvPath = poiImagesCsvPath;
    }

    @Override
    public void run(String... args) {
        if (!populate) {
            log.info("[BootstrapPopulate] Skip (ptdot.bootstrap.populate=false)");
            return;
        }

        runOptionalPopulateScript();
        runDistrictBootstrap();
        runDistrictFilesBootstrap();
        runPoiBootstrap();
    }

    private void runOptionalPopulateScript() {
        Resource resource = resourceLoader.getResource(populateScriptPath);
        if (!resource.exists()) {
            log.info("[BootstrapPopulate] Script opcional não encontrado: {}", populateScriptPath);
            return;
        }

        BootstrapFlywayConfig.applyTrackedScript(dataSource, resourceLoader, populateScriptPath, "bootstrap-populate");
    }

    private void runDistrictBootstrap() {
        if (!populateDistricts) {
            log.info("[BootstrapPopulate] Skip districts (ptdot.bootstrap.populate-districts=false)");
            return;
        }

        long districtCount = districtRepository.count();
        if (populateIfEmpty && districtCount > 0) {
            log.info("[BootstrapPopulate] Districts já existem (count={}). Skip import.", districtCount);
            return;
        }

        var result = districtsCsvImportService.importFromCsv();
        log.info("[BootstrapPopulate] Districts import: rows={}, created={}, updated={}, invalid={}",
                result.totalRows(), result.created(), result.updated(), result.invalidRows());
    }

    private void runDistrictFilesBootstrap() {
        if (!populateDistrictFiles) {
            log.info("[BootstrapPopulate] Skip district files (ptdot.bootstrap.populate-district-files=false)");
            return;
        }

        long districtCount = districtRepository.count();
        if (districtCount == 0) {
            log.info("[BootstrapPopulate] Sem distritos para anexar ficheiros. Skip district files.");
            return;
        }

        var result = districtFilesCsvImportService.importFromCsv();
        log.info("[BootstrapPopulate] District files import: lines={}, updatedDistricts={}, attached={}, missingDistricts={}, invalid={}",
                result.totalLines(), result.updatedDistricts(), result.attachedFiles(), result.missingDistricts(), result.invalidLines());
    }

    private void runPoiBootstrap() {
        if (!populatePois) {
            log.info("[BootstrapPopulate] Skip POIs (ptdot.bootstrap.populate-pois=false)");
            return;
        }

        long poiCount = poiRepository.count();
        if (populateIfEmpty && poiCount > 0) {
            log.info("[BootstrapPopulate] POIs já existem (count={}). Skip import.", poiCount);
            return;
        }

        Resource poisResource = resourceLoader.getResource(poisCsvPath);
        Resource imagesResource = resourceLoader.getResource(poiImagesCsvPath);

        if (!poisResource.exists()) {
            log.warn("[BootstrapPopulate] CSV de POIs não encontrado: {}", poisCsvPath);
            return;
        }

        poiCsvSyncService.syncFromCsv(poisResource, imagesResource);
        log.info("[BootstrapPopulate] POI sync concluído.");
    }
}
