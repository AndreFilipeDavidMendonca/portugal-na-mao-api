// src/main/java/pt/dot/application/api/PoiController.java
package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.PoiDto;
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

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    // GET /api/pois  → TODOS os POIs
    @GetMapping
    public ResponseEntity<List<PoiDto>> listPois() {
        List<PoiDto> pois = poiService.findAll();
        return ResponseEntity.ok(pois);
    }

    // GET /api/pois/{id} → 1 POI
    @GetMapping("/{id}")
    public ResponseEntity<PoiDto> getById(@PathVariable Long id) {
        return poiService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT /api/pois/{id} → update parcial (PATCH) usando PoiDto
    @PutMapping("/{id}")
    public ResponseEntity<PoiDto> updatePoi(
            @PathVariable Long id,
            @RequestBody PoiDto body
    ) {
        return poiService.updatePoi(id, body)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}