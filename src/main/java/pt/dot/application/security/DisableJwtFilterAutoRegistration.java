
package pt.dot.application.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DisableJwtFilterAutoRegistration {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        // Isto evita que o Spring registe o filtro automaticamente como um Bean de Servlet global.
        // Assim, o filtro só corre dentro da SecurityFilterChain na ordem correta.
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
