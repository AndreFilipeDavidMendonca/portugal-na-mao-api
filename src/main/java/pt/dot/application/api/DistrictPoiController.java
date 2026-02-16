// src/main/java/pt/dot/application/api/DistrictPoiController.java
package pt.dot.application.api;

import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.PoiLiteResponseDto;
import pt.dot.application.service.DistrictPoiQueryService;

@RestController
@RequestMapping("/api/pois")
public class DistrictPoiController {

    private final DistrictPoiQueryService service;

    public DistrictPoiController(DistrictPoiQueryService service) {
        this.service = service;
    }

    @GetMapping("/lite")
    public PoiLiteResponseDto lite(
            @RequestParam String bbox,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "2000") int limit
    ) {
        return service.findLiteWithFacets(bbox, category, limit);
    }
}