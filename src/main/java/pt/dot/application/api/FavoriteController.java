package pt.dot.application.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.FavoriteDto;
import pt.dot.application.api.dto.FavoriteStatusDto;
import pt.dot.application.service.FavoriteService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        },
        allowCredentials = "true"
)
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    private UUID requireUserId(HttpSession session) {
        return (UUID) session.getAttribute(AuthSession.SESSION_USER_ID);
    }

    // ✅ Lista favoritos do user
    @GetMapping("/favorites")
    public ResponseEntity<List<FavoriteDto>> list(HttpSession session) {
        UUID userId = requireUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(favoriteService.list(userId));
    }

    // ✅ Estado: é favorito?
    @GetMapping("/favorites/{poiId}")
    public ResponseEntity<FavoriteStatusDto> isFavorite(
            @PathVariable Long poiId,
            HttpSession session
    ) {
        UUID userId = requireUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        boolean fav = favoriteService.isFavorite(userId, poiId);
        return ResponseEntity.ok(new FavoriteStatusDto(fav));
    }

    // ✅ Add (idempotente)
    @PostMapping("/favorites/{poiId}")
    public ResponseEntity<FavoriteDto> add(
            @PathVariable Long poiId,
            HttpSession session
    ) {
        UUID userId = requireUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        try {
            FavoriteDto dto = favoriteService.add(userId, poiId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ✅ Remove (idempotente)
    @DeleteMapping("/favorites/{poiId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long poiId,
            HttpSession session
    ) {
        UUID userId = requireUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        favoriteService.remove(userId, poiId);
        return ResponseEntity.noContent().build();
    }

    // ✅ Toggle
    @PostMapping("/favorites/{poiId}/toggle")
    public ResponseEntity<FavoriteStatusDto> toggle(
            @PathVariable Long poiId,
            HttpSession session
    ) {
        UUID userId = requireUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        try {
            boolean fav = favoriteService.toggle(userId, poiId);
            return ResponseEntity.ok(new FavoriteStatusDto(fav));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}