// src/main/java/pt/dot/application/security/SecurityConfig.java
package pt.dot.application.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                )
                .authorizeHttpRequests(auth -> auth
                        // preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // auth público
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/register").permitAll()

                        // público
                        .requestMatchers(HttpMethod.GET, "/api/pois/**", "/api/districts/**").permitAll()

                        // comments:
                        // GET público
                        .requestMatchers(HttpMethod.GET, "/api/pois/*/comments").permitAll()
                        // POST autenticado
                        .requestMatchers(HttpMethod.POST, "/api/pois/*/comments").authenticated()
                        // DELETE autenticado
                        .requestMatchers(HttpMethod.DELETE, "/api/comments/*").authenticated()

                        // autenticado
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .requestMatchers("/api/favorites/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "https://portugal-na-mao.vercel.app",
                "https://*.vercel.app"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // alguns browsers mandam "authorization" em minúsculas no preflight
        cfg.setAllowedHeaders(List.of("Authorization", "authorization", "Content-Type"));
        cfg.setExposedHeaders(List.of("Authorization"));

        // token = sem cookies
        cfg.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}