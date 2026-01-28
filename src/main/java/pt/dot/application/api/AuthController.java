package pt.dot.application.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import pt.dot.application.api.dto.CurrentUserDto;
import pt.dot.application.api.dto.LoginRequestDto;
import pt.dot.application.api.dto.RegisterRequestDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.UserRole;
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

    @PostMapping("/register")
    public ResponseEntity<CurrentUserDto> register(
            @RequestBody RegisterRequestDto body,
            HttpSession session
    ) {
        if (body.getEmail() == null || body.getPassword() == null) {
            return ResponseEntity.badRequest().build();
        }

        final String email = body.getEmail().trim();
        if (email.isEmpty()) return ResponseEntity.badRequest().build();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(409).build();
        }

        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());

        user.setFirstName(trimOrNull(body.getFirstName()));
        user.setLastName(trimOrNull(body.getLastName()));
        user.setAge(body.getAge());
        user.setNationality(trimOrNull(body.getNationality()));
        user.setPhone(trimOrNull(body.getPhone()));

        user.setPasswordHash(passwordEncoder.encode(body.getPassword()));

        // role: default USER; accepts BUSINESS
        UserRole role = UserRole.USER;
        if (body.getRole() != null && !body.getRole().trim().isEmpty()) {
            String r = body.getRole().trim().toUpperCase();
            if ("BUSINESS".equals(r)) role = UserRole.BUSINESS;
        }
        user.setRole(role);

        // displayName: "First Last" fallback email prefix
        String display = buildDisplayName(user.getFirstName(), user.getLastName(), user.getEmail());
        user.setDisplayName(display);

        AppUser saved = userRepository.save(user);

        // login automático
        session.setAttribute(AuthSession.SESSION_USER_ID, saved.getId());

        return ResponseEntity.ok(toDto(saved));
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
        String a = first != null ? first.trim() : "";
        String b = last != null ? last.trim() : "";
        String name = (a + " " + b).trim();
        if (!name.isEmpty()) return name;

        if (email != null) {
            int at = email.indexOf("@");
            if (at > 0) return email.substring(0, at);
        }
        return "Utilizador";
    }
}