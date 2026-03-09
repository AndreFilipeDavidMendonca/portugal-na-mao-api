package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.DistrictDto;
import pt.dot.application.api.dto.DistrictUpdateRequest;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DistrictService {

    private static final int MAX_FILES = 5;

    private final DistrictRepository districtRepository;
    private final WikimediaMediaService wikimediaMediaService;

    public DistrictService(
            DistrictRepository districtRepository,
            WikimediaMediaService wikimediaMediaService
    ) {
        this.districtRepository = districtRepository;
        this.wikimediaMediaService = wikimediaMediaService;
    }

    @Transactional(readOnly = true)
    public List<DistrictDto> listDistricts() {
        return districtRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDistrictDtoWithoutFiles)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DistrictDto> findById(Long id) {
        if (id == null) return Optional.empty();

        return districtRepository.findById(id)
                .map(this::toDistrictDtoWithFiles);
    }

    public Optional<DistrictDto> updateDistrict(Long id, DistrictUpdateRequest payload) {
        if (id == null) return Optional.empty();

        return districtRepository.findById(id).map(d -> {
            applyPatch(d, payload);
            District saved = districtRepository.saveAndFlush(d);
            return toDistrictDtoWithFiles(saved);
        });
    }

    private void applyPatch(District d, DistrictUpdateRequest payload) {
        if (payload == null) return;

        if (payload.getName() != null) d.setName(payload.getName());
        if (payload.getNamePt() != null) d.setNamePt(payload.getNamePt());
        if (payload.getDescription() != null) d.setDescription(payload.getDescription());
        if (payload.getHistory() != null) d.setHistory(payload.getHistory());
        if (payload.getInhabitedSince() != null) d.setInhabitedSince(payload.getInhabitedSince());

        if (payload.getPopulation() != null) d.setPopulation(payload.getPopulation());
        if (payload.getMunicipalitiesCount() != null) d.setMunicipalitiesCount(payload.getMunicipalitiesCount());
        if (payload.getParishesCount() != null) d.setParishesCount(payload.getParishesCount());

        if (payload.getFiles() != null) {
            d.setFiles(normalizeStrings(payload.getFiles(), MAX_FILES));
        }
        if (payload.getSources() != null) {
            d.setSources(normalizeStrings(payload.getSources(), MAX_FILES));
        }
    }

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

    private DistrictDto toDistrictDtoWithFiles(District d) {
        List<String> baseFiles = normalizeStrings(d.getFiles(), MAX_FILES);

        String districtLabel = firstNonBlank(d.getNamePt(), d.getName());
        List<String> finalFiles = (districtLabel == null || districtLabel.isBlank())
                ? baseFiles
                : wikimediaMediaService.getDistrictMedia5(districtLabel, baseFiles);

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
                finalFiles,
                normalizeStrings(d.getSources(), MAX_FILES)
        );
    }

    private static List<String> normalizeStrings(List<String> input, int limit) {
        if (input == null || input.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        for (String s : input) {
            if (s == null) continue;
            String v = s.trim();
            if (v.isBlank()) continue;
            if (!out.contains(v)) out.add(v);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isBlank()) return a.trim();
        if (b != null && !b.trim().isBlank()) return b.trim();
        return null;
    }
}