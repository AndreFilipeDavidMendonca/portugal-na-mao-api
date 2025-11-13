package pt.dot.monumentosproxy.monumentos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monumentos")
public class MonumentResolveController {

    private final MonumentWfsService wfsService;

    public MonumentResolveController(MonumentWfsService wfsService) {
        this.wfsService = wfsService;
    }

    @GetMapping("/resolve")
    public ResponseEntity<MonumentDto> resolve(
            @RequestParam(required = false) String name,
            @RequestParam double lat,
            @RequestParam double lon
    ) {
        System.out.println(">>> [MonumentResolveController] resolve() called");
        System.out.println("    name=" + name + " lat=" + lat + " lon=" + lon);

        MonumentDto bestMatch = wfsService.findBestMatch(name, lat, lon);

        if (bestMatch == null) {
            System.out.println("    -> bestMatch = null → 404");
            return ResponseEntity.notFound().build();
        }

        System.out.println("    -> bestMatch.id = " + bestMatch.getId()
                + " | originalName = " + bestMatch.getOriginalName());

        return ResponseEntity.ok(bestMatch);
    }
}