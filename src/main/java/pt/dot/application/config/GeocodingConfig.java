package pt.dot.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class GeocodingConfig {

    @Bean
    public RestClient nominatimRestClient(
            @Value("${app.geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.nominatim.user-agent:pt-dot-app/1.0 (contact: dev@pt-dot.local)}") String userAgent
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                // Nominatim pede User-Agent identificável
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Bean
    public CacheManager cacheManager(
            @Value("${app.geocoding.cache.ttl-seconds:604800}") long ttlSeconds // 7 dias
    ) {
        CaffeineCacheManager mgr = new CaffeineCacheManager("geocode");
        mgr.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                        .maximumSize(50_000)
        );
        return mgr;
    }
}