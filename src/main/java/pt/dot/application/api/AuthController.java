package pt.dot.application.api;

import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequestDto body) {
        if (body.getEmail() == null || body.getPassword() == null) return ResponseEntity.badRequest().build();

        final String email = body.getEmail().trim();
        if (email.isEmpty()) return ResponseEntity.badRequest().build();

        if (userRepository.existsByEmailIgnoreCase(email)) return ResponseEntity.status(409).build();

        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());

        user.setFirstName(trimOrNull(body.getFirstName()));
        user.setLastName(trimOrNull(body.getLastName()));
        user.setAge(body.getAge());
        user.setNationality(trimOrNull(body.getNationality()));
        user.setPhone(trimOrNull(body.getPhone()));

        user.setPasswordHash(passwordEncoder.encode(body.getPassword()));

        UserRole role = UserRole.USER;
        if (body.getRole() != null && !body.getRole().trim().isEmpty()) {
            String r = body.getRole().trim().toUpperCase();
            if ("BUSINESS".equals(r)) role = UserRole.BUSINESS;
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

        Optional<AppUser> opt = userRepository.findByEmailIgnoreCase(body.getEmail());
        if (opt.isEmpty()) return ResponseEntity.status(401).build();

        AppUser user = opt.get();
        if (!passwordEncoder.matches(body.getPassword(), user.getPasswordHash())) return ResponseEntity.status(401).build();

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return ResponseEntity.ok(new AuthResponse(token, toDto(user)));
    }

    private CurrentUserDto toDto(AppUser u) {
        return new CurrentUserDto(
                u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getRole().name(),
                u.getFirstName(), u.getLastName(), u.getAge(), u.getNationality(), u.getPhone()
        );
    }

    private static String trimOrNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty()? null : t; }

    private static String buildDisplayName(String first, String last, String email) {
        String a = first != null ? first.trim() : "";
        String b = last != null ? last.trim() : "";
        String name = (a + " " + b).trim();
        if (!name.isEmpty()) return name;
        if (email != null) { int at = email.indexOf("@"); if (at > 0) return email.substring(0, at); }
        return "Utilizador";
    }
}