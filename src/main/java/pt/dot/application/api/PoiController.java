// src/main/java/pt/dot/application/api/PoiController.java
package pt.dot.application.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.CreatePoiRequestDto;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.service.PoiService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pois")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        },
        allowCredentials = "true"
)
public class PoiController {

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    /** Lista tudo (leve: sem galeria) */
    @GetMapping
    public List<PoiDto> listAll() {
        return poiService.findAll();
    }

    /** Lista “meus POIs” (leve: sem galeria) */
    @GetMapping("/mine")
    public List<PoiDto> mine(HttpSession session) {
        return poiService.findMine(session);
    }

    /** Detalhe por SIPA (inclui galeria) */
    @GetMapping("/by-sipa/{sipaId}")
    public ResponseEntity<PoiDto> getBySipaId(@PathVariable String sipaId) {
        return poiService.findBySipaId(sipaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Detalhe por ID (inclui galeria) */
    @GetMapping("/{id}")
    public ResponseEntity<PoiDto> getById(@PathVariable Long id) {
        return poiService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Criar POI comercial (resposta leve: devolve só o id) */
    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@RequestBody CreatePoiRequestDto req, HttpSession session) {
        Long id = poiService.createBusinessPoi(req, session);
        return ResponseEntity
                .created(URI.create("/api/pois/" + id))
                .body(Map.of("id", id));
    }

    /** Update (admin ou owner em comerciais) */
    @PutMapping("/{id}")
    public ResponseEntity<PoiDto> update(
            @PathVariable Long id,
            @RequestBody PoiDto dto,
            HttpSession session
    ) {
        return poiService.updatePoi(id, dto, session)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpSession session) {
        poiService.deleteBusinessPoi(id, session);
        return ResponseEntity.noContent().build(); // ✅ 204
    }
}