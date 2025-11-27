package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.OsmPoiSnapshot;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.service.PoiEnrichmentService;
import pt.dot.application.service.PoiService;

import java.util.List;

@RestController
@RequestMapping("/api/pois")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        }
)
public class PoiController {

    private final PoiEnrichmentService poiEnrichmentService;
    private final PoiService poiService;

    public PoiController(
            PoiEnrichmentService poiEnrichmentService,
            PoiService poiService
            ) {
        this.poiService = poiService;
        this.poiEnrichmentService = poiEnrichmentService;
    }

    @PostMapping("/from-osm")
    public ResponseEntity<PoiDto> createFromOsm(@RequestBody OsmPoiSnapshot snapshot) {
        PoiDto dto = poiEnrichmentService.createOrEnrichFromOsm(snapshot);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<PoiDto>> getPois(
            @RequestParam(required = false) Integer districtId,
            @RequestParam(required = false) String category
    ) {
        List<PoiDto> list = poiEnrichmentService.findPois(districtId, category);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/by-osm-id")
    public ResponseEntity<PoiDto> getByExternalOsmId(@RequestParam String externalOsmId) {
        return poiService.findByExternalOsmId(externalOsmId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}