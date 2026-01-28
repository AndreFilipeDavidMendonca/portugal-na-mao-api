package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import pt.dot.application.api.dto.GeocodeRequestDto;
import pt.dot.application.api.dto.GeocodeResponseDto;
import pt.dot.application.service.GeocodingService;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        },
        allowCredentials = "true"
)
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