package pt.dot.application.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.auth.CurrentUserDto;
import pt.dot.application.api.dto.auth.LoginRequestDto;
import pt.dot.application.api.dto.auth.RegisterRequestDto;
import pt.dot.application.api.dto.auth.UpdateCurrentUserRequestDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.enums.UserRole;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.security.JwtService;

import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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

    @PatchMapping("/me")
    public ResponseEntity<CurrentUserDto> updateCurrentUser(@RequestBody UpdateCurrentUserRequestDto body) {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId)) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> {
                    applyUserPatch(user, body);
                    AppUser saved = userRepository.save(user);
                    return ResponseEntity.ok(toDto(saved));
                })
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

    private void applyUserPatch(AppUser user, UpdateCurrentUserRequestDto body) {
        if (body == null) return;

        if (body.getDisplayName() != null) {
            String displayName = trimOrNull(body.getDisplayName());
            user.setDisplayName(displayName != null ? displayName : buildDisplayName(
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail()
            ));
        }

        if (body.getAvatarUrl() != null) {
            user.setAvatarUrl(trimOrNull(body.getAvatarUrl()));
        }

        if (body.getFirstName() != null) {
            user.setFirstName(trimOrNull(body.getFirstName()));
        }

        if (body.getLastName() != null) {
            user.setLastName(trimOrNull(body.getLastName()));
        }

        if (body.getAge() != null) {
            Integer age = body.getAge();
            if (age < 0 || age > 130) {
                throw new ResponseStatusException(BAD_REQUEST, "Idade inválida");
            }
            user.setAge(age);
        }

        if (body.getNationality() != null) {
            user.setNationality(trimOrNull(body.getNationality()));
        }

        if (body.getPhone() != null) {
            user.setPhone(trimOrNull(body.getPhone()));
        }

        if (body.getDisplayName() == null) {
            String currentDisplayName = trimOrNull(user.getDisplayName());
            if (currentDisplayName == null) {
                user.setDisplayName(buildDisplayName(
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail()
                ));
            }
        }
    }

    private CurrentUserDto toDto(AppUser u) {
        return new CurrentUserDto(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole().name(),
                u.getFirstName(),
                u.getLastName(),
                u.getAge(),
                u.getNationality(),
                u.getPhone()
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