package pt.dot.application.api.geocoding;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import pt.dot.application.api.dto.geocoding.GeocodeRequestDto;
import pt.dot.application.api.dto.geocoding.GeocodeResponseDto;
import pt.dot.application.service.geocoding.GeocodingService;

@RestController
@RequestMapping("/api")

public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @PostMapping("/geocode")
    public ResponseEntity<GeocodeResponseDto> geocode(@RequestBody GeocodeRequestDto body) {
        GeocodeResponseDto dto = geocodingService.geocode(body);
        return ResponseEntity.ok(dto);
    }
}