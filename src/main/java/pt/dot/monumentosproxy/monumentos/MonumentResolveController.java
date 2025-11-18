// src/main/java/pt/dot/monumentosproxy/monumentos/MonumentResolveController.java
package pt.dot.monumentosproxy.monumentos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monumentos")
public class MonumentResolveController {

    private static final Logger log = LoggerFactory.getLogger(MonumentResolveController.class);

    private final MonumentWfsService wfsService;

    public MonumentResolveController(MonumentWfsService wfsService) {
        this.wfsService = wfsService;
    }

    /**
     * Resolve o "melhor" monumento dado um nome (opcional) e coordenadas de clique.
     */
    @GetMapping("/resolve")
    public ResponseEntity<MonumentDto> resolve(
            @RequestParam(required = false) String name,
            @RequestParam double lat,
            @RequestParam double lon
    ) {
        log.info("Resolving monument. name='{}', lat={}, lon={}", name, lat, lon);

        MonumentDto bestMatch = wfsService.findBestMatch(name, lat, lon);

        if (bestMatch == null) {
            log.info("No bestMatch found for name='{}', lat={}, lon={}", name, lat, lon);
            return ResponseEntity.notFound().build();
        }

        log.info("Resolved monument id={} | originalName='{}'",
                bestMatch.getId(), bestMatch.getOriginalName());

        return ResponseEntity.ok(bestMatch);
    }
}