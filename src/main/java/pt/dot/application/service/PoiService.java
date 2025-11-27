// src/main/java/pt/dot/application/service/PoiService.java
package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PoiService {

    private final PoiRepository poiRepository;
    private final DistrictRepository districtRepository;

    public PoiService(PoiRepository poiRepository,
                      DistrictRepository districtRepository) {
        this.poiRepository = poiRepository;
        this.districtRepository = districtRepository;
    }

    // ---------- LISTAGENS / LEITURA ----------

    public Optional<PoiDto> findByExternalOsmId(String externalOsmId) {
        return poiRepository.findByExternalOsmId(externalOsmId)
                .map(this::toDto);
    }

    // ---------- CRIAÇÃO A PARTIR DO OSM / MANUAL ----------

    public PoiDto createFromOsmSnapshot(CreatePoiFromOsmRequest req) {
        District district = districtRepository.findById(req.getDistrictId())
                .orElseThrow(() -> new IllegalArgumentException("Distrito não encontrado: " + req.getDistrictId()));

        Poi poi = new Poi();
        poi.setDistrict(district);
        poi.setName(req.getName());
        poi.setNamePt(req.getNamePt());
        poi.setCategory(req.getCategory());
        poi.setSubcategory(req.getSubcategory());
        poi.setDescription(req.getDescription());
        poi.setLat(req.getLat());
        poi.setLon(req.getLon());
        poi.setWikipediaUrl(req.getWikipediaUrl());
        poi.setSipaId(req.getSipaId());
        poi.setExternalOsmId(req.getOsmId());
        poi.setSource(req.getSource() != null ? req.getSource() : "osm");

        Poi saved = poiRepository.save(poi);
        return toDto(saved);
    }

    // ---------- FUTURO: lookup por nome+coords ----------

    public List<PoiDto> lookupByNameAndCoords(String name, Double lat, Double lon, Double maxDistanceKm) {
        // versão simples: só por nome (depois refinamos com coordenadas)
        List<Poi> results = poiRepository.findByNameIgnoreCase(name);

        return results.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ---------- MAPPER ENTITY -> DTO ----------

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