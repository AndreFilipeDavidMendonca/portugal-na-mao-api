// src/main/java/pt/dot/application/api/AdminOsmImportController.java
package pt.dot.application.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.service.OsmPoiImportService;

import java.util.Optional;

@RestController
@RequestMapping("/api/admin/osm")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
public class AdminOsmImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminOsmImportController.class);

    private final OsmPoiImportService osmPoiImportService;
    private final DistrictRepository districtRepository;

    public AdminOsmImportController(OsmPoiImportService osmPoiImportService,
                                    DistrictRepository districtRepository) {
        this.osmPoiImportService = osmPoiImportService;
        this.districtRepository = districtRepository;
    }

    // Importar TODOS os distritos
    @PostMapping("/import-all")
    public ResponseEntity<Void> importAll() {
        log.info("[AdminOsmImport] Importar todos os distritos");
        osmPoiImportService.importAllPortugal();
        return ResponseEntity.accepted().build();
    }

    // Importar só um distrito
//    @PostMapping("/import-district/{id}")
//    public ResponseEntity<Void> importDistrict(@PathVariable Long id) {
//        Optional<District> opt = districtRepository.findById(id);
//        if (opt.isEmpty()) {
//            return ResponseEntity.notFound().build();
//        }
//        osmPoiImportService.importForDistrict(opt.get());
//        return ResponseEntity.accepted().build();
//    }
}