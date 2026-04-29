package pt.dot.application.service.poi;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.poi.CreatePoiRequestDto;
import pt.dot.application.api.dto.poi.PoiDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.enums.UserRole;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.security.SecurityUtil;
import pt.dot.application.service.media.LazyWikimediaMediaService;
import pt.dot.application.service.media.MediaItemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@Transactional
public class PoiService {

    private static final String SOURCE_BUSINESS = "business";
    private static final int MAX_IMAGES = 5;

    private final PoiRepository poiRepository;
    private final AppUserRepository userRepository;
    private final MediaItemService mediaItemService;
    private final LazyWikimediaMediaService lazyWikimediaMediaService;

    public PoiService(
            PoiRepository poiRepository,
            AppUserRepository userRepository,
            MediaItemService mediaItemService,
            LazyWikimediaMediaService lazyWikimediaMediaService
    ) {
        this.poiRepository = poiRepository;
        this.userRepository = userRepository;
        this.mediaItemService = mediaItemService;
        this.lazyWikimediaMediaService = lazyWikimediaMediaService;
    }

    @Transactional(readOnly = true)
    public List<PoiDto> findAll() {
        return poiRepository.findAll().stream().map(this::toDtoList).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PoiDto> findById(Long id) {
        if (id == null) return Optional.empty();
        return poiRepository.findById(id).map(this::toDtoDetail);
    }

    @Transactional(readOnly = true)
    public Optional<PoiDto> findBySipaId(String sipaId) {
        String sid = safe(sipaId);
        if (sid.isBlank()) return Optional.empty();
        return poiRepository.findBySipaId(sid).map(this::toDtoDetail);
    }

    @Transactional(readOnly = true)
    public List<PoiDto> findMine() {
        AppUser me = requireMe();
        return poiRepository.findByOwner_Id(me.getId()).stream().map(this::toDtoList).toList();
    }

    public Long createBusinessPoi(CreatePoiRequestDto req) {
        AppUser me = requireBusinessOrAdmin();
        if (req == null) throw new ResponseStatusException(BAD_REQUEST, "Body em falta");

        String name = safe(req.getName());
        String category = safe(req.getCategory());

        if (name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Nome é obrigatório");
        if (category.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Categoria é obrigatória");
        if (req.getLat() == null || req.getLon() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Lat/Lon são obrigatórios");
        }

        List<String> images = normalizeImages(req.getImages());
        String primary = safeNull(req.getImage());

        if (images.isEmpty() && primary != null) {
            images = List.of(primary);
        }

        Poi p = new Poi();
        p.setOwner(me);
        p.setSource(SOURCE_BUSINESS);
        p.setName(name);
        p.setCategory(category);
        p.setDescription(safeNull(req.getDescription()));
        p.setLat(req.getLat());
        p.setLon(req.getLon());

        Poi saved = poiRepository.saveAndFlush(p);

        mediaItemService.replaceMedia(
                MediaItemService.ENTITY_POI,
                saved.getId(),
                MediaItemService.MEDIA_IMAGE,
                images,
                MediaItemService.PROVIDER_MANUAL
        );

        return saved.getId();
    }

    public Optional<PoiDto> updatePoi(Long id, PoiDto dto) {
        if (id == null) return Optional.empty();

        return poiRepository.findById(id).map(poi -> {
            requireOwnerOrAdmin(poi);
            applyPatch(poi, dto);

            Poi saved = poiRepository.saveAndFlush(poi);
            return toDtoDetail(saved);
        });
    }

    public void deletePoi(Long id) {
        if (id == null) throw new ResponseStatusException(BAD_REQUEST, "ID em falta");

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "POI não encontrado"));

        requireDeletePermission(poi);

        mediaItemService.deleteMediaAndStorage(
                MediaItemService.ENTITY_POI,
                poi.getId()
        );

        poiRepository.delete(poi);
    }

    private void applyPatch(Poi poi, PoiDto dto) {
        if (dto == null) return;

        if (dto.getName() != null) poi.setName(dto.getName());
        if (dto.getNamePt() != null) poi.setNamePt(dto.getNamePt());
        if (dto.getDescription() != null) poi.setDescription(dto.getDescription());
        if (dto.getCategory() != null) poi.setCategory(dto.getCategory());

        if (poi.getOwner() != null) {
            if (dto.getLat() != null) poi.setLat(dto.getLat());
            if (dto.getLon() != null) poi.setLon(dto.getLon());
        }

        if (dto.getImages() != null) {
            mediaItemService.replaceMedia(
                    MediaItemService.ENTITY_POI,
                    poi.getId(),
                    MediaItemService.MEDIA_IMAGE,
                    normalizeImages(dto.getImages()),
                    MediaItemService.PROVIDER_MANUAL
            );
            return;
        }

        if (dto.getImage() != null) {
            String img0 = safeNull(dto.getImage());
            if (img0 == null) return;

            mediaItemService.replaceMedia(
                    MediaItemService.ENTITY_POI,
                    poi.getId(),
                    MediaItemService.MEDIA_IMAGE,
                    List.of(img0),
                    MediaItemService.PROVIDER_MANUAL
            );
        }
    }

    private PoiDto toDtoList(Poi p) {
        IdPair ids = idsOf(p);

        return new PoiDto(
                p.getId(),
                ids.districtId(),
                ids.ownerId(),
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
                null,
                List.of()
        );
    }

    private PoiDto toDtoDetail(Poi p) {
        IdPair ids = idsOf(p);

        List<String> lazyUrls = lazyWikimediaMediaService.ensurePoiImages(p);

        List<String> finalGallery = mediaItemService.getResolvedUrls(
                MediaItemService.ENTITY_POI,
                p.getId(),
                MediaItemService.MEDIA_IMAGE,
                MAX_IMAGES
        );

        if (finalGallery.isEmpty() && !lazyUrls.isEmpty()) {
            finalGallery = lazyUrls.stream().limit(MAX_IMAGES).toList();
        }

        String primary = finalGallery.isEmpty() ? null : finalGallery.get(0);

        return new PoiDto(
                p.getId(),
                ids.districtId(),
                ids.ownerId(),
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
                primary,
                finalGallery
        );
    }

    private void requireDeletePermission(Poi poi) {
        AppUser me = requireMe();

        boolean isAdmin = me.getRole() == UserRole.ADMIN;
        boolean isBusinessOwner =
                poi.getOwner() != null &&
                        poi.getOwner().getId() != null &&
                        poi.getOwner().getId().equals(me.getId());

        if (!isAdmin && !isBusinessOwner) {
            throw new ResponseStatusException(FORBIDDEN, "Sem permissão para eliminar este POI");
        }
    }

    private void requireOwnerOrAdmin(Poi poi) {
        AppUser me = requireMe();

        boolean isAdmin = me.getRole() == UserRole.ADMIN;
        boolean isOwner =
                poi.getOwner() != null &&
                        poi.getOwner().getId() != null &&
                        poi.getOwner().getId().equals(me.getId());

        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(FORBIDDEN, "Sem permissão para editar este POI");
        }
    }

    private AppUser requireMe() {
        UUID userId = SecurityUtil.getUserIdOrNull();
        if (userId == null) throw new ResponseStatusException(UNAUTHORIZED);

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private AppUser requireBusinessOrAdmin() {
        AppUser me = requireMe();

        if (me.getRole() == UserRole.ADMIN || me.getRole() == UserRole.BUSINESS) {
            return me;
        }

        throw new ResponseStatusException(FORBIDDEN, "Apenas contas comerciais podem criar POIs");
    }

    private record IdPair(Long districtId, UUID ownerId) {
    }

    private static IdPair idsOf(Poi p) {
        Long districtId = p.getDistrict() != null ? p.getDistrict().getId() : null;
        UUID ownerId = p.getOwner() != null ? p.getOwner().getId() : null;
        return new IdPair(districtId, ownerId);
    }

    private static List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) return new ArrayList<>();

        List<String> out = new ArrayList<>();

        for (String it : images) {
            String v = safeNull(it);
            if (v == null) continue;

            if (!out.contains(v)) out.add(v);
            if (out.size() >= MAX_IMAGES) break;
        }

        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeNull(String s) {
        String t = safe(s);
        return t.isBlank() ? null : t;
    }
}