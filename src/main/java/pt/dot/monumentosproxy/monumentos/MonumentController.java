// src/main/java/pt/dot/monumentosproxy/monumentos/MonumentController.java
package pt.dot.monumentosproxy.monumentos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monumentos")
public class MonumentController {

    private final MonumentWfsService wfsService;

    public MonumentController(MonumentWfsService wfsService) {
        this.wfsService = wfsService;
    }

    @GetMapping
    public List<MonumentDto> byBoundingBox(
            @RequestParam double minX,
            @RequestParam double minY,
            @RequestParam double maxX,
            @RequestParam double maxY
    ) {
        return wfsService.searchByBbox(minX, minY, maxX, maxY);
    }
}