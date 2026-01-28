package pt.dot.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // públicos
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/register", "/api/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/me").permitAll()

                        // favoritos: deixa preparado (por agora o controller faz 401 sem session)
                        .requestMatchers("/api/favorites/**").permitAll()

                        // resto (por agora)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}