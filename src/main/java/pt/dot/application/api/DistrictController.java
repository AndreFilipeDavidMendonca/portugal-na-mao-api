package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.DistrictDto;
import pt.dot.application.api.dto.DistrictUpdateRequest;
import pt.dot.application.service.DistrictService;

import java.util.List;

@RestController
@RequestMapping("/api/districts")
public class DistrictController {

    private final DistrictService districtService;

    public DistrictController(DistrictService districtService) {
        this.districtService = districtService;
    }

    @GetMapping
    public List<DistrictDto> listDistricts() {
        return districtService.listDistricts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DistrictDto> getDistrict(@PathVariable Long id) {
        return districtService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DistrictDto> updateDistrict(
            @PathVariable Long id,
            @RequestBody DistrictUpdateRequest payload
    ) {
        return districtService.updateDistrict(id, payload)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}