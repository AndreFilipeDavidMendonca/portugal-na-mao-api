package pt.dot.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // usa o CorsConfig que criámos
                .cors(Customizer.withDefaults())

                // CSRF desligado (para fetch + cookies)
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // endpoints públicos
                        .requestMatchers(
                                "/api/districts/**",
                                "/api/pois/**",
                                "/api/login",
                                "/api/logout"
                        ).permitAll()

                        // /api/me pode existir mas pode devolver 401
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}