package pt.dot.application.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.FavoriteDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Favorite;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.FavoriteRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AppUserRepository appUserRepository;
    private final PoiRepository poiRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            AppUserRepository appUserRepository,
            PoiRepository poiRepository
    ) {
        this.favoriteRepository = favoriteRepository;
        this.appUserRepository = appUserRepository;
        this.poiRepository = poiRepository;
    }

    private UUID requireUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return (UUID) auth.getPrincipal();
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long poiId) {
        return favoriteRepository.existsByUser_IdAndPoi_Id(requireUserId(), poiId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> list() {
        return favoriteRepository.findAllByUser_IdOrderByCreatedAtDesc(requireUserId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FavoriteDto add(Long poiId) {

        UUID userId = requireUserId();

        var existing = favoriteRepository.findByUser_IdAndPoi_Id(userId, poiId);
        if (existing.isPresent()) return toDto(existing.get());

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User não encontrado"));

        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new IllegalArgumentException("POI não encontrado"));

        Favorite f = new Favorite();
        f.setUser(user);
        f.setPoi(poi);

        return toDto(favoriteRepository.save(f));
    }

    @Transactional
    public void remove(Long poiId) {
        favoriteRepository.deleteByUser_IdAndPoi_Id(requireUserId(), poiId);
    }

    @Transactional
    public boolean toggle(Long poiId) {
        UUID userId = requireUserId();

        var existing = favoriteRepository.findByUser_IdAndPoi_Id(userId, poiId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }

        add(poiId);
        return true;
    }

    private FavoriteDto toDto(Favorite f) {
        Poi p = f.getPoi();
        return new FavoriteDto(
                p.getId(),
                p.getNamePt() != null ? p.getNamePt() : p.getName(),
                p.getImage(),
                f.getCreatedAt()
        );
    }
}