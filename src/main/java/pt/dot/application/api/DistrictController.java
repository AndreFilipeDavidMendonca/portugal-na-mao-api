package pt.dot.application.api;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.DistrictDetailDto;
import pt.dot.application.api.dto.DistrictDto;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        }
)
public class DistrictController {

    private final DistrictRepository districtRepository;
    private final PoiRepository poiRepository;

    public DistrictController(DistrictRepository districtRepository,
                              PoiRepository poiRepository) {
        this.districtRepository = districtRepository;
        this.poiRepository = poiRepository;
    }

    // 1) Lista básica de distritos
    @GetMapping("/districts")
    public List<DistrictDto> listDistricts() {
        List<District> entities = districtRepository.findAllByOrderByNameAsc();
        return entities.stream()
                .map(this::toDistrictDto)
                .collect(Collectors.toList());
    }

    // 2) Detalhe simples de distrito
    @GetMapping("/districts/{id}")
    public ResponseEntity<DistrictDto> getDistrict(@PathVariable Long id) {
        Optional<District> opt = districtRepository.findById(id);
        return opt.map(district -> ResponseEntity.ok(toDistrictDto(district)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 3) só POIs de um distrito
    @GetMapping("/districts/{id}/pois")
    public ResponseEntity<List<PoiDto>> getPoisForDistrict(@PathVariable Long id) {
        if (!districtRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<Poi> pois = poiRepository.findByDistrict_IdOrderByNameAsc(id);
        List<PoiDto> dto = pois.stream()
                .map(this::toPoiDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dto);
    }

    // 4) distrito + pois num só payload
    @GetMapping("/districts/{id}/detail")
    public ResponseEntity<DistrictDetailDto> getDistrictDetail(@PathVariable Long id) {
        Optional<District> opt = districtRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        District district = opt.get();
        List<Poi> pois = poiRepository.findByDistrict_IdOrderByNameAsc(id);

        DistrictDetailDto dto = new DistrictDetailDto(
                toDistrictDto(district),
                pois.stream().map(this::toPoiDto).collect(Collectors.toList())
        );

        return ResponseEntity.ok(dto);
    }

    // ---- mappers ----

    private DistrictDto toDistrictDto(District d) {
        return new DistrictDto(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getNamePt(),
                d.getPopulation(),
                d.getFoundedYear(),
                d.getLat(),
                d.getLon(),
                d.getDescription(),
                d.getInhabitedSince(),
                d.getHistory(),
                d.getMunicipalitiesCount(),
                d.getParishesCount()
        );
    }

    // src/main/java/pt/dot/application/api/DistrictController.java
// ... resto igual ...

    private PoiDto toPoiDto(Poi p) {
        Long districtId = (p.getDistrict() != null ? p.getDistrict().getId() : null);

        return new PoiDto(
                p.getId(),
                districtId,
                p.getName(),
                p.getNamePt(),
                p.getCategory(),
                p.getSubcategory(),
                p.getDescription(),
                p.getLat(),
                p.getLon(),
                p.getWikipediaUrl(),
                p.getSipaId(),
                p.getExternalOsmId(),
                p.getSource(),
                p.getImage(),
                p.getImages()
        );
    }
}