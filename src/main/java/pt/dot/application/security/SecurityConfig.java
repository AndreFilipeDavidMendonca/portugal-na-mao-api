package pt.dot.application.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())
                .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .authorizeHttpRequests(auth -> auth
                        // preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // auth pública
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/register").permitAll()

                        // públicos reais
                        .requestMatchers(HttpMethod.GET, "/api/districts", "/api/districts/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pois").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pois/lite").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pois/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pois/*/comments").permitAll()

                        // autenticados
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/me").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/pois").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/pois/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/pois/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/pois/mine").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/pois/*/comments").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/comments/*").authenticated()

                        .requestMatchers("/api/favorites/**").authenticated()
                        .requestMatchers("/api/friendships/**").authenticated()

                        // geocode, se quiseres só users autenticados
                        .requestMatchers("/api/geocode").authenticated()

                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/media/upload").authenticated()
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

        cfg.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "authorization",
                "Content-Type"
        ));

        cfg.setExposedHeaders(List.of(
                "Authorization",
                "X-Debug-JWT"
        ));

        cfg.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}