// src/main/java/pt/dot/application/api/DistrictPoiController.java
package pt.dot.application.api;

import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.PoiLiteDto;
import pt.dot.application.service.DistrictPoiQueryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/districts")
public class DistrictPoiController {

    private final DistrictPoiQueryService service;

    public DistrictPoiController(DistrictPoiQueryService service) {
        this.service = service;
    }

    @GetMapping("/{districtId}/pois/lite")
    public List<PoiLiteDto> lite(
            @PathVariable Long districtId,
            @RequestParam String bbox,
            @RequestParam(defaultValue = "2000") int limit
    ) {
        return service.findLite(districtId, bbox, limit);
    }

    @GetMapping("/{districtId}/pois/facets")
    public Map<String, Long> facets(
            @PathVariable Long districtId,
            @RequestParam String bbox
    ) {
        return service.countByCategory(districtId, bbox);
    }
}