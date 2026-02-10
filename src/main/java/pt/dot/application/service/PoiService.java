package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.CreatePoiRequestDto;
import pt.dot.application.api.dto.PoiDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.entity.UserRole;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.security.SecurityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class PoiService {

    private final PoiRepository poiRepository;
    private final AppUserRepository userRepository;

    public PoiService(PoiRepository poiRepository, AppUserRepository userRepository) {
        this.poiRepository = poiRepository;
        this.userRepository = userRepository;
    }

    /* ============================
       READ
    ============================ */

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

    /* ============================
       CREATE (comercial)
    ============================ */

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
        String image = normalizePrimaryImage(req.getImage(), images);

        Poi p = new Poi();
        p.setOwner(me);
        p.setSource("business");
        p.setName(name);
        p.setCategory(category);

        p.setDescription(safeNull(req.getDescription()));
        p.setLat(req.getLat());
        p.setLon(req.getLon());

        p.setImage(image);
        p.setImages(images);

        return poiRepository.save(p).getId();
    }

    /* ============================
       UPDATE
    ============================ */

    public Optional<PoiDto> updatePoi(Long id, PoiDto dto) {
        if (id == null) return Optional.empty();

        return poiRepository.findById(id).map(poi -> {
            if (poi.getOwner() != null) {
                requireOwnerOrAdmin(poi);
            }
            applyPatch(poi, dto);
            return toDtoDetail(poiRepository.save(poi));
        });
    }

    /* ============================
       DELETE (comercial)
    ============================ */

    public void deleteBusinessPoi(Long id) {
        if (id == null) throw new ResponseStatusException(BAD_REQUEST, "ID em falta");

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "POI não encontrado"));

        if (poi.getOwner() == null) {
            throw new ResponseStatusException(FORBIDDEN, "Apenas POIs comerciais podem ser eliminados");
        }

        requireOwnerOrAdmin(poi);
        poiRepository.delete(poi);
    }

    /* ============================
       PATCH
    ============================ */

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
            List<String> images = normalizeImages(dto.getImages());
            poi.setImages(images);
            poi.setImage(normalizePrimaryImage(poi.getImage(), images));
        }

        if (dto.getImage() != null) {
            String image = safeNull(dto.getImage());
            poi.setImage(normalizePrimaryImage(image, poi.getImages()));
        }
    }

    /* ============================
       DTO MAPPERS
    ============================ */

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
                p.getImage(),
                null
        );
    }

    private PoiDto toDtoDetail(Poi p) {
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
                p.getImage(),
                p.getImages()
        );
    }

    private record IdPair(Long districtId, UUID ownerId) {}

    private static IdPair idsOf(Poi p) {
        Long districtId = (p.getDistrict() != null ? p.getDistrict().getId() : null);
        UUID ownerId = (p.getOwner() != null ? p.getOwner().getId() : null);
        return new IdPair(districtId, ownerId);
    }

    /* ============================
       AUTH HELPERS (JWT)
    ============================ */

    private AppUser requireMe() {
        UUID userId = SecurityUtil.getUserIdOrNull();
        if (userId == null) throw new ResponseStatusException(UNAUTHORIZED);

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private AppUser requireBusinessOrAdmin() {
        AppUser me = requireMe();
        if (me.getRole() == UserRole.ADMIN) return me;
        if (me.getRole() == UserRole.BUSINESS) return me;
        throw new ResponseStatusException(FORBIDDEN, "Apenas contas comerciais podem criar POIs");
    }

    private void requireOwnerOrAdmin(Poi poi) {
        AppUser me = requireMe();

        boolean isAdmin = me.getRole() == UserRole.ADMIN;
        boolean isOwner = poi.getOwner() != null
                && poi.getOwner().getId() != null
                && poi.getOwner().getId().equals(me.getId());

        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(FORBIDDEN, "Sem permissão para editar este POI");
        }
    }

    /* ============================
       UTIL
    ============================ */

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeNull(String s) {
        String t = safe(s);
        return t.isBlank() ? null : t;
    }

    private static List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) return new ArrayList<>();

        List<String> out = new ArrayList<>();
        for (String it : images) {
            String v = safeNull(it);
            if (v == null) continue;
            if (!out.contains(v)) out.add(v);
        }
        return out;
    }

    private static String normalizePrimaryImage(String primary, List<String> gallery) {
        String p = safeNull(primary);
        if (p != null) return p;

        if (gallery != null) {
            for (String it : gallery) {
                String v = safeNull(it);
                if (v != null) return v;
            }
        }
        return null;
    }
}