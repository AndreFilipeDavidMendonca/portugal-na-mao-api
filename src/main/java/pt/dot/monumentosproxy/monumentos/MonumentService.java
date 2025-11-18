// src/main/java/pt/dot/monumentosproxy/monumentos/MonumentService.java
package pt.dot.monumentosproxy.monumentos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonumentService {

    private static final Logger log = LoggerFactory.getLogger(MonumentService.class);

    private final WebClient monumentosWebClient;

    public MonumentService(WebClient monumentosWebClient) {
        this.monumentosWebClient = monumentosWebClient;
    }

    /* ---------- Helpers ---------- */

    private String norm(String value) {
        if (value == null) return null;

        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private Double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /* ---------- Search by name (ArcGIS GeoJSON) ---------- */

    @Cacheable(
            cacheNames = "monumentsByName",
            key = "#name.toLowerCase()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<MonumentDto> searchByName(String name) {
        log.info("[MonumentService] searchByName() called with name='{}'", name);

        if (!StringUtils.hasText(name)) {
            log.info("[MonumentService] name vazio/null – devolver lista vazia.");
            return List.of();
        }

        String normQuery = norm(name);

        try {
            GeoJsonFeatureCollection fc = monumentosWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("f", "geojson")
                            .queryParam("where", "1=1")
                            .queryParam("outFields", "*")
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(GeoJsonFeatureCollection.class)
                    .onErrorResume(ex -> {
                        log.error("[MonumentService] HTTP error ao chamar ArcGIS: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (fc == null || fc.getFeatures() == null || fc.getFeatures().isEmpty()) {
                log.warn("[MonumentService] GeoJSON vazio ou sem features.");
                return List.of();
            }

            log.info("[MonumentService] Nº total de features recebidas = {}", fc.getFeatures().size());

            List<MonumentDto> result = fc.getFeatures().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .filter(dto -> {
                        String n = norm(dto.getOriginalName());
                        return n != null && n.contains(normQuery);
                    })
                    .collect(Collectors.toList());

            log.info("[MonumentService] Nº de candidatos após filtro por nome='{}' = {}", name, result.size());
            return result;

        } catch (Exception e) {
            log.error("[MonumentService] Exception em searchByName", e);
            return List.of();
        }
    }

    /* ---------- Mapping GeoJSON → MonumentDto ---------- */

    private MonumentDto toDto(GeoJsonFeature feature) {
        if (feature == null || feature.getProperties() == null) return null;

        Map<String, Object> p = feature.getProperties();

        String codSig    = asString(p.get("COD_SIG"));
        String nome      = asString(p.get("INF_NOME"));
        String morada    = asString(p.get("INF_MORADA"));
        String freguesia = asString(p.get("FREGUESIA"));
        String descricao = asString(p.get("INF_DESCRICAO"));
        String site      = asString(p.get("INF_SITE"));
        String email     = asString(p.get("INF_EMAIL"));
        String telefone  = asString(p.get("INF_TELEFONE"));

        Double lon = null;
        Double lat = null;
        if (feature.getGeometry() != null &&
                feature.getGeometry().getCoordinates() != null &&
                feature.getGeometry().getCoordinates().size() >= 2) {
            lon = asDouble(feature.getGeometry().getCoordinates().get(0));
            lat = asDouble(feature.getGeometry().getCoordinates().get(1));
        }

        String shortDesc = descricao;
        if (shortDesc != null && shortDesc.length() > 240) {
            shortDesc = shortDesc.substring(0, 240) + "...";
        }

        Map<String, String> extra = new LinkedHashMap<>();
        if (morada != null)   extra.put("Morada", morada);
        if (telefone != null) extra.put("Telefone", telefone);
        if (email != null)    extra.put("Email", email);
        if (site != null)     extra.put("Site", site);

        return MonumentDto.builder()
                .id(codSig != null ? codSig : asString(feature.getId()))
                .slug(codSig)
                .originalName(nome != null ? nome : "Monumento sem nome")
                .normalizedName(norm(nome))
                .locality("Lisboa")   // este dataset é só Lisboa
                .district("Lisboa")
                .concelho("Lisboa")
                .freguesia(freguesia)
                .lat(lat)
                .lon(lon)
                .shortDescription(shortDesc)
                .fullDescriptionHtml(descricao != null ? "<p>" + descricao + "</p>" : null)
                .heritageCategory("Monumento Nacional")
                .propertyType("Propriedade pública")
                .protectionStatus("Classificado")
                .imageUrls(List.of()) // dataset não traz fotos
                .sourceUrl(site)
                .extraAttributes(extra)
                .build();
    }

    /* ---------- GeoJSON helper classes ---------- */

    public static class GeoJsonFeatureCollection {
        private String type;
        private List<GeoJsonFeature> features;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<GeoJsonFeature> getFeatures() { return features; }
        public void setFeatures(List<GeoJsonFeature> features) { this.features = features; }
    }

    public static class GeoJsonFeature {
        private String type;
        private Object id;
        private GeoJsonGeometry geometry;
        private Map<String, Object> properties;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Object getId() { return id; }
        public void setId(Object id) { this.id = id; }

        public GeoJsonGeometry getGeometry() { return geometry; }
        public void setGeometry(GeoJsonGeometry geometry) { this.geometry = geometry; }

        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    }

    public static class GeoJsonGeometry {
        private String type;
        private List<Double> coordinates;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<Double> getCoordinates() { return coordinates; }
        public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
    }

    public List<MonumentDto> searchByBbox(double minX, double minY, double maxX, double maxY) {
        log.info("[MonumentService] searchByBbox() minX={}, minY={}, maxX={}, maxY={}",
                minX, minY, maxX, maxY);

        String geometry = minX + "," + minY + "," + maxX + "," + maxY;

        try {
            GeoJsonFeatureCollection fc = monumentosWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("f", "geojson")
                            .queryParam("geometry", geometry)
                            .queryParam("geometryType", "esriGeometryEnvelope")
                            .queryParam("spatialRel", "esriSpatialRelIntersects")
                            .queryParam("outFields", "*")
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(GeoJsonFeatureCollection.class)
                    .onErrorResume(ex -> {
                        log.error("[MonumentService] HTTP error em searchByBbox: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (fc == null || fc.getFeatures() == null || fc.getFeatures().isEmpty()) {
                log.info("[MonumentService] searchByBbox: sem features.");
                return List.of();
            }

            log.info("[MonumentService] searchByBbox: {} features recebidas.", fc.getFeatures().size());

            return fc.getFeatures().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            log.error("[MonumentService] Exception em searchByBbox", e);
            return List.of();
        }
    }
}