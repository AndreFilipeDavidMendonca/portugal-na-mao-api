// src/main/java/pt/dot/application/api/DistrictController.java
package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.DistrictDto;
import pt.dot.application.api.dto.DistrictUpdateRequest;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")

public class DistrictController {

    private final DistrictRepository districtRepository;

    public DistrictController(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    // 1) Lista básica (sem files para ficar leve)
    @GetMapping("/districts")
    public List<DistrictDto> listDistricts() {
        List<District> entities = districtRepository.findAllByOrderByNameAsc();
        return entities.stream()
                .map(this::toDistrictDtoWithoutFiles)
                .collect(Collectors.toList());
    }

    // 2) Detalhe com files
    @GetMapping("/districts/{id}")
    public ResponseEntity<DistrictDto> getDistrict(@PathVariable Long id) {
        Optional<District> opt = districtRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        District d = opt.get();
        return ResponseEntity.ok(toDistrictDtoWithFiles(d));
    }

    // 3) PUT – atualiza meta + files (usando DistrictUpdateRequest)
    @PutMapping("/districts/{id}")
    public ResponseEntity<DistrictDto> updateDistrict(
            @PathVariable Long id,
            @RequestBody DistrictUpdateRequest payload
    ) {
        Optional<District> opt = districtRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        District d = opt.get();

        // Texto / nomes (só se não forem null)
        if (payload.getName() != null) {
            d.setName(payload.getName());
        }
        if (payload.getNamePt() != null) {
            d.setNamePt(payload.getNamePt());
        }
        if (payload.getDescription() != null) {
            d.setDescription(payload.getDescription());
        }
        if (payload.getHistory() != null) {
            d.setHistory(payload.getHistory());
        }
        if (payload.getInhabitedSince() != null) {
            d.setInhabitedSince(payload.getInhabitedSince());
        }

        // Numéricos (podem ser null para limpar)
        if (payload.getPopulation() != null) {
            d.setPopulation(payload.getPopulation());
        }
        if (payload.getMunicipalitiesCount() != null) {
            d.setMunicipalitiesCount(payload.getMunicipalitiesCount());
        }
        if (payload.getParishesCount() != null) {
            d.setParishesCount(payload.getParishesCount());
        }

        if (payload.getFiles() != null) {
            d.setFiles(payload.getFiles());
        }
        if (payload.getSources() != null) {
            d.setSources(payload.getSources());
        }

        District saved = districtRepository.save(d);
        return ResponseEntity.ok(toDistrictDtoWithFiles(saved));
    }

    // -------- mapeadores privados --------

    // usado na lista (sem files)
    private DistrictDto toDistrictDtoWithoutFiles(District d) {
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
                d.getParishesCount(),
                null,
                null
        );
    }

    // usado no detalhe (GET/PUT)
    private DistrictDto toDistrictDtoWithFiles(District d) {
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
                d.getParishesCount(),
                d.getFiles(),
                d.getSources()
        );
    }
}