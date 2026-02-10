// src/main/java/pt/dot/application/api/FavoriteController.java
package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.FavoriteDto;
import pt.dot.application.api.dto.FavoriteStatusDto;
import pt.dot.application.service.FavoriteService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<FavoriteDto>> list() {
        return ResponseEntity.ok(favoriteService.list());
    }

    @GetMapping("/favorites/{poiId}")
    public ResponseEntity<FavoriteStatusDto> isFavorite(@PathVariable Long poiId) {
        boolean fav = favoriteService.isFavorite(poiId);
        return ResponseEntity.ok(new FavoriteStatusDto(fav));
    }

    @PostMapping("/favorites/{poiId}")
    public ResponseEntity<FavoriteDto> add(@PathVariable Long poiId) {
        return ResponseEntity.ok(favoriteService.add(poiId));
    }

    @DeleteMapping("/favorites/{poiId}")
    public ResponseEntity<Void> remove(@PathVariable Long poiId) {
        favoriteService.remove(poiId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/favorites/{poiId}/toggle")
    public ResponseEntity<FavoriteStatusDto> toggle(@PathVariable Long poiId) {
        boolean fav = favoriteService.toggle(poiId);
        return ResponseEntity.ok(new FavoriteStatusDto(fav));
    }
}