package pt.dot.application.service.district;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.district.DistrictDto;
import pt.dot.application.api.dto.district.DistrictUpdateRequest;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.service.media.LazyWikimediaMediaService;
import pt.dot.application.service.media.MediaItemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DistrictService {

    private static final int MAX_FILES = 5;

    private final DistrictRepository districtRepository;
    private final MediaItemService mediaItemService;
    private final LazyWikimediaMediaService lazyWikimediaMediaService;

    public DistrictService(
            DistrictRepository districtRepository,
            MediaItemService mediaItemService,
            LazyWikimediaMediaService lazyWikimediaMediaService
    ) {
        this.districtRepository = districtRepository;
        this.mediaItemService = mediaItemService;
        this.lazyWikimediaMediaService = lazyWikimediaMediaService;
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
            mediaItemService.replaceMedia(
                    MediaItemService.ENTITY_DISTRICT,
                    d.getId(),
                    MediaItemService.MEDIA_IMAGE,
                    normalizeStrings(payload.getFiles()),
                    MediaItemService.PROVIDER_MANUAL
            );
        }

        if (payload.getSources() != null) {
            d.setSources(normalizeStrings(payload.getSources()));
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
                List.of(),
                List.of()
        );
    }

    private DistrictDto toDistrictDtoWithFiles(District d) {
        List<String> lazyUrls = lazyWikimediaMediaService.ensureDistrictImages(d);

        List<String> files = mediaItemService.getResolvedUrls(
                MediaItemService.ENTITY_DISTRICT,
                d.getId(),
                MediaItemService.MEDIA_IMAGE,
                MAX_FILES
        );

        if (files.isEmpty() && !lazyUrls.isEmpty()) {
            files = lazyUrls.stream().limit(MAX_FILES).toList();
        }

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
                files,
                normalizeStrings(d.getSources())
        );
    }

    private static List<String> normalizeStrings(List<String> input) {
        if (input == null || input.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();

        for (String s : input) {
            if (s == null) continue;

            String v = s.trim();
            if (v.isBlank()) continue;

            if (!out.contains(v)) out.add(v);
            if (out.size() >= DistrictService.MAX_FILES) break;
        }

        return out;
    }
}