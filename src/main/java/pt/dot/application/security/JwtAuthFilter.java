package pt.dot.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static String asRole(String role) {
        if (role == null || role.isBlank()) return "ROLE_USER";
        String r = role.toUpperCase();
        return r.startsWith("ROLE_") ? r : ("ROLE_" + r);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            response.setHeader("X-Debug-JWT", "missing");
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isBlank()) {
            response.setHeader("X-Debug-JWT", "blank");
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        try {
            var principal = jwtService.tryParse(token);

            if (principal != null) {
                var authorities = List.of(new SimpleGrantedAuthority(asRole(principal.role())));

                var auth = new UsernamePasswordAuthenticationToken(
                        principal.userId(),
                        null,
                        authorities
                );

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);

                response.setHeader("X-Debug-JWT", "ok");
            } else {
                response.setHeader("X-Debug-JWT", "invalid");
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            response.setHeader("X-Debug-JWT", "error");
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}