
package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.CurrentUserDto;
import pt.dot.application.api.dto.LoginRequestDto;
import pt.dot.application.api.dto.RegisterRequestDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.UserRole;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.security.JwtService;

import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public record AuthResponse(String token, CurrentUserDto user) {}

    public AuthController(AppUserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * ✅ NOVO: Endpoint para obter dados do utilizador logado.
     * Invocado pelo frontend no fetchCurrentUser().
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId)) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequestDto body) {
        if (body.getEmail() == null || body.getPassword() == null) return ResponseEntity.badRequest().build();

        String email = body.getEmail().trim().toLowerCase();
        if (email.isEmpty()) return ResponseEntity.badRequest().build();
        if (userRepository.existsByEmailIgnoreCase(email)) return ResponseEntity.status(409).build();

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName(trimOrNull(body.getFirstName()));
        user.setLastName(trimOrNull(body.getLastName()));
        user.setAge(body.getAge());
        user.setNationality(trimOrNull(body.getNationality()));
        user.setPhone(trimOrNull(body.getPhone()));
        user.setPasswordHash(passwordEncoder.encode(body.getPassword()));

        UserRole role = UserRole.USER;
        if (body.getRole() != null && "BUSINESS".equalsIgnoreCase(body.getRole().trim())) {
            role = UserRole.BUSINESS;
        }
        user.setRole(role);
        user.setDisplayName(buildDisplayName(user.getFirstName(), user.getLastName(), user.getEmail()));

        AppUser saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getId(), saved.getRole().name());

        return ResponseEntity.ok(new AuthResponse(token, toDto(saved)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequestDto body) {
        if (body.getEmail() == null || body.getPassword() == null) return ResponseEntity.badRequest().build();

        return userRepository.findByEmailIgnoreCase(body.getEmail().trim())
                .filter(u -> passwordEncoder.matches(body.getPassword(), u.getPasswordHash()))
                .map(u -> {
                    String token = jwtService.generateToken(u.getId(), u.getRole().name());
                    return ResponseEntity.ok(new AuthResponse(token, toDto(u)));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    private CurrentUserDto toDto(AppUser u) {
        return new CurrentUserDto(
                u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getRole().name(),
                u.getFirstName(), u.getLastName(), u.getAge(), u.getNationality(), u.getPhone()
        );
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String buildDisplayName(String first, String last, String email) {
        String name = ((first != null ? first.trim() : "") + " " + (last != null ? last.trim() : "")).trim();
        if (!name.isEmpty()) return name;
        if (email != null && email.contains("@")) return email.split("@")[0];
        return "Utilizador";
    }
}
