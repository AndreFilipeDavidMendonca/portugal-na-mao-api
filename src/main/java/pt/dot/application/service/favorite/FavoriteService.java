package pt.dot.application.service.favorite;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.favorite.FavoriteDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Favorite;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.FavoriteRepository;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.service.media.MediaItemService;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AppUserRepository appUserRepository;
    private final PoiRepository poiRepository;
    private final MediaItemService mediaItemService;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            AppUserRepository appUserRepository,
            PoiRepository poiRepository,
            MediaItemService mediaItemService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.appUserRepository = appUserRepository;
        this.poiRepository = poiRepository;
        this.mediaItemService = mediaItemService;
    }

    private UUID requireUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "A tua sessão já não é válida. Inicia sessão novamente para continuar."
            );
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
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Não foi possível validar a tua conta. Inicia sessão novamente e tenta de novo."
                ));

        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Não foi possível encontrar o POI que queres guardar nos favoritos."
                ));

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setPoi(poi);

        return toDto(favoriteRepository.save(favorite));
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

    private FavoriteDto toDto(Favorite favorite) {
        Poi poi = favorite.getPoi();

        List<String> images = mediaItemService.getResolvedUrls(
                MediaItemService.ENTITY_POI,
                poi.getId(),
                MediaItemService.MEDIA_IMAGE,
                1
        );

        String image = images.isEmpty() ? null : images.get(0);

        return new FavoriteDto(
                poi.getId(),
                poi.getNamePt() != null ? poi.getNamePt() : poi.getName(),
                image,
                favorite.getCreatedAt()
        );
    }
}