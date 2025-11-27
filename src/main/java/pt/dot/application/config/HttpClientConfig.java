// src/main/java/pt/dot/application/config/HttpClientConfig.java
package pt.dot.application.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        // <-- mete aqui algo teu, identificável
                        "ptdot-monumentos-proxy/1.0 (https://teu-site-ou-github; contacto: teu-email)"
                )
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}