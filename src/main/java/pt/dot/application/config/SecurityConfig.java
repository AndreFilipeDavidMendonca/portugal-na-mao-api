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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ✅ evita popup Basic Auth / Form login
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())

                // ✅ SPA + HttpSession (para já): desliga CSRF
                .csrf(csrf -> csrf.disable())

                // ✅ importante: isto faz o Spring Security respeitar a policy CORS global
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        // ✅ preflight SEMPRE permitido
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // públicos
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/register", "/api/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/me").permitAll()

                        // (por agora deixas tudo aberto; depois metes authenticated/role)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}