// src/main/java/pt/dot/application/osm/OverpassClient.java
package pt.dot.application.osm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${osm.overpass.url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    public OverpassClient(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * Faz POST para Overpass com a query dada e devolve o JSON como JsonNode.
     */
    public JsonNode executeQuery(String query) {
        try {
            String body = "data=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            String response = restTemplate.postForObject(overpassUrl, body, String.class);
            if (response == null || response.isBlank()) {
                log.warn("[OverpassClient] resposta vazia");
                return null;
            }
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("[OverpassClient] erro a chamar Overpass", e);
            return null;
        }
    }
}