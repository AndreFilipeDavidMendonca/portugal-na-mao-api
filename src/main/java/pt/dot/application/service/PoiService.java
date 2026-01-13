// src/main/java/pt/dot/application/service/PoiService.java
package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.PoiRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PoiService {

    private final PoiRepository poiRepository;

    public PoiService(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    // ---------- READ ----------

    public List<PoiDto> findAll() {
        return poiRepository.findAll().stream().map(this::toDto).toList();
    }

    public Optional<PoiDto> findById(Long id) {
        return poiRepository.findById(id).map(this::toDto);
    }

    // ---------- PATCH UPDATE ----------

    public Optional<PoiDto> updatePoi(Long id, PoiDto dto) {
        return poiRepository.findById(id).map(poi -> {
            applyPatch(poi, dto);
            Poi saved = poiRepository.save(poi);
            return toDto(saved);
        });
    }

    private void applyPatch(Poi poi, PoiDto dto) {
        if (dto.getName() != null) poi.setName(dto.getName());
        if (dto.getNamePt() != null) poi.setNamePt(dto.getNamePt());
        if (dto.getDescription() != null) poi.setDescription(dto.getDescription());
        if (dto.getImage() != null) poi.setImage(dto.getImage());
        if (dto.getImages() != null) poi.setImages(dto.getImages());
    }

    // ---------- MAPPER ----------

    private PoiDto toDto(Poi p) {
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