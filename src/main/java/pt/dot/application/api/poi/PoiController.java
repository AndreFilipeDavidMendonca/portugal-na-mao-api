package pt.dot.application.api.poi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.poi.CreatePoiRequestDto;
import pt.dot.application.api.dto.poi.PoiDto;
import pt.dot.application.service.poi.PoiService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pois")
public class PoiController {

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping
    public List<PoiDto> listAll() {
        return poiService.findAll();
    }

    @GetMapping("/mine")
    public List<PoiDto> mine() {
        return poiService.findMine();
    }

    @GetMapping("/by-sipa/{sipaId}")
    public ResponseEntity<PoiDto> getBySipaId(@PathVariable String sipaId) {
        return poiService.findBySipaId(sipaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PoiDto> getById(@PathVariable Long id) {
        return poiService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@RequestBody CreatePoiRequestDto req) {
        Long id = poiService.createBusinessPoi(req);
        return ResponseEntity
                .created(URI.create("/api/pois/" + id))
                .body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PoiDto> update(
            @PathVariable Long id,
            @RequestBody PoiDto dto
    ) {
        return poiService.updatePoi(id, dto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        poiService.deletePoi(id);
        return ResponseEntity.noContent().build();
    }
}