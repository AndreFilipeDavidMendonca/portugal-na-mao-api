package pt.dot.application.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pt.dot.application.api.dto.CurrentUserDto;
import pt.dot.application.api.dto.LoginRequestDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.repo.AppUserRepository;

import java.util.Optional;
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
public class AuthController {

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUserDto> login(
            @RequestBody LoginRequestDto body,
            HttpSession session
    ) {
        if (body.getEmail() == null || body.getPassword() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<AppUser> opt = userRepository.findByEmailIgnoreCase(body.getEmail());
        if (opt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        AppUser user = opt.get();

        if (!passwordEncoder.matches(body.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }

        session.setAttribute(AuthSession.SESSION_USER_ID, user.getId());

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> me(HttpSession session) {
        UUID userId = (UUID) session.getAttribute(AuthSession.SESSION_USER_ID);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private CurrentUserDto toDto(AppUser u) {
        return new CurrentUserDto(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole().name()
        );
    }
}