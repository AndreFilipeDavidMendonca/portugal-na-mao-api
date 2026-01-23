package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.FavoriteDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Favorite;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.FavoriteRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, Long poiId) {
        return favoriteRepository.existsByUser_IdAndPoi_Id(userId, poiId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> list(UUID userId) {
        return favoriteRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FavoriteDto add(UUID userId, Long poiId) {
        // idempotente
        var existing = favoriteRepository.findByUser_IdAndPoi_Id(userId, poiId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User não encontrado"));

        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new IllegalArgumentException("POI não encontrado"));

        Favorite f = new Favorite();
        f.setUser(user);
        f.setPoi(poi);

        Favorite saved = favoriteRepository.save(f);
        return toDto(saved);
    }

    @Transactional
    public void remove(UUID userId, Long poiId) {
        favoriteRepository.deleteByUser_IdAndPoi_Id(userId, poiId);
    }

    @Transactional
    public boolean toggle(UUID userId, Long poiId) {
        var existing = favoriteRepository.findByUser_IdAndPoi_Id(userId, poiId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }

        add(userId, poiId);
        return true;
    }

    // =========================
    // mapping helpers
    // =========================
    private FavoriteDto toDto(Favorite f) {
        Poi p = f.getPoi();

        String name = pickBestName(p);
        String image = pickBestImage(p);

        return new FavoriteDto(
                p.getId(),
                name,
                image,
                f.getCreatedAt()
        );
    }

    private String pickBestName(Poi p) {
        try {
            String namePt = p.getNamePt();
            if (namePt != null && !namePt.isBlank()) return namePt.trim();
        } catch (Exception ignored) { }

        try {
            String name = p.getName();
            if (name != null && !name.isBlank()) return name.trim();
        } catch (Exception ignored) { }

        return "POI";
    }

    private String pickBestImage(Poi p) {
        try {
            String image = p.getImage();
            if (image != null && !image.isBlank()) return image.trim();
        } catch (Exception ignored) { }

        try {
            var images = p.getImages(); // List<String> (assumido)
            if (images != null && !images.isEmpty()) {
                String first = images.get(0);
                if (first != null && !first.isBlank()) return first.trim();
            }
        } catch (Exception ignored) { }

        return null;
    }
}