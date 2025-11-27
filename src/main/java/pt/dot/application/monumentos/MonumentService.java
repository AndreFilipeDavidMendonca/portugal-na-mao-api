// src/main/java/pt/dot/application/monumentos/MonumentService.java
package pt.dot.application.monumentos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import org.w3c.dom.*;

@Service
public class MonumentService {

    private static final Logger log = LoggerFactory.getLogger(MonumentService.class);

    private final WebClient monumentosWebClient;

    @Value("${monumentos.wfs.type-name}")
    private String typeName;

    @Value("${monumentos.wfs.srs-name:EPSG:4326}")
    private String srsName;

    public MonumentService(WebClient monumentosWebClient) {
        this.monumentosWebClient = monumentosWebClient;
    }

    /* =========================================================
       Helpers básicos
       ========================================================= */

    private String norm(String value) {
        if (value == null) return null;

        return Normalizer.normalize(value, Normalizer.Form.NFD)
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
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String escapeSqlLiteral(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    /* =========================================================
       Search por nome (WFS GetFeature → XML)
       ========================================================= */

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

        String trimmed = name.trim();
        String escaped = escapeSqlLiteral(trimmed);

        // tentativa de filtro do lado do WFS (se suportar CQL_FILTER)
        String cqlFilter = "INF_NOME ILIKE '%" + escaped + "%'";
        log.debug("[MonumentService] searchByName CQL_FILTER='{}'", cqlFilter);

        try {
            String xml = monumentosWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("service", "WFS")
                            .queryParam("request", "GetFeature")
                            .queryParam("version", "1.1.0")
                            .queryParam("typeName", typeName)
                            .queryParam("srsName", srsName)
                            .queryParam("CQL_FILTER", cqlFilter) // se não suportar, ele ignora
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(ex -> {
                        log.error("[MonumentService] HTTP error ao chamar WFS em searchByName: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (xml == null || xml.isBlank()) {
                log.warn("[MonumentService] searchByName: XML vazio para name='{}'.", name);
                return List.of();
            }

            GeoJsonFeatureCollection fc = parseWfsResponse(xml);
            if (fc.getFeatures() == null || fc.getFeatures().isEmpty()) {
                log.warn("[MonumentService] searchByName: FeatureCollection vazia para name='{}'.", name);
                return List.of();
            }

            log.info("[MonumentService] searchByName: Nº de features recebidas do WFS = {}",
                    fc.getFeatures().size());

            String normQuery = norm(name);

            List<MonumentDto> result = fc.getFeatures().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .filter(dto -> {
                        String n = norm(dto.getOriginalName());
                        return n != null && n.contains(normQuery);
                    })
                    .collect(Collectors.toList());

            log.info("[MonumentService] searchByName: Nº de candidatos após filtro local='{}' = {}",
                    name, result.size());

            return result;

        } catch (Exception e) {
            log.error("[MonumentService] Exception em searchByName", e);
            return List.of();
        }
    }

    /* =========================================================
       Search por bounding box (WFS GetFeature + bbox)
       ========================================================= */

    @Cacheable(
            cacheNames = "monumentsByBbox",
            key = "T(String).valueOf(#minX) + ',' + #minY + ',' + #maxX + ',' + #maxY",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<MonumentDto> searchByBbox(double minX, double minY, double maxX, double maxY) {
        log.info("[MonumentService] searchByBbox() minX={}, minY={}, maxX={}, maxY={}",
                minX, minY, maxX, maxY);

        String bbox = minX + "," + minY + "," + maxX + "," + maxY + "," + srsName;

        try {
            String xml = monumentosWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("service", "WFS")
                            .queryParam("request", "GetFeature")
                            .queryParam("version", "1.1.0")
                            .queryParam("typeName", typeName)
                            .queryParam("srsName", srsName)
                            .queryParam("bbox", bbox)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(ex -> {
                        log.error("[MonumentService] HTTP error em searchByBbox: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (xml == null || xml.isBlank()) {
                log.info("[MonumentService] searchByBbox: XML vazio.");
                return List.of();
            }

            GeoJsonFeatureCollection fc = parseWfsResponse(xml);
            if (fc.getFeatures() == null || fc.getFeatures().isEmpty()) {
                log.info("[MonumentService] searchByBbox: sem features.");
                return List.of();
            }

            log.info("[MonumentService] searchByBbox: {} features recebidas.", fc.getFeatures().size());

            return fc.getFeatures().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[MonumentService] Exception em searchByBbox", e);
            return List.of();
        }
    }

    /* =========================================================
       "Best match" – já estava ok (reusa searchByBbox/searchByName)
       ========================================================= */

    public MonumentDto findBestMatch(String approxName, Double lat, Double lon) {
        try {
            if (lat != null && lon != null) {
                double delta = 0.01; // ~1km aprox.
                double minX = lon - delta;
                double maxX = lon + delta;
                double minY = lat - delta;
                double maxY = lat + delta;

                List<MonumentDto> nearby = searchByBbox(minX, minY, maxX, maxY);
                if (nearby == null || nearby.isEmpty()) {
                    log.info("[MonumentService] findBestMatch: searchByBbox vazio (lat={}, lon={})",
                            lat, lon);
                } else {
                    log.info("[MonumentService] findBestMatch: {} candidatos via bbox.", nearby.size());
                    MonumentDto best = chooseBestByNameWithinBbox(approxName, nearby);
                    if (best != null) {
                        log.info("[MonumentService] findBestMatch: escolhido dentro do BBOX -> id={} | nome='{}'",
                                best.getId(), best.getOriginalName());
                        return best;
                    }
                    log.info("[MonumentService] findBestMatch: nenhum candidato aceitável dentro do BBOX.");
                }
            }

            if (StringUtils.hasText(approxName)) {
                log.info("[MonumentService] findBestMatch: sem coords ou BBOX vazio, fallback para searchByName(name='{}')",
                        approxName);
                List<MonumentDto> byName = searchByName(approxName);
                if (byName == null || byName.isEmpty()) {
                    log.info("[MonumentService] searchByName: nenhum candidato para '{}'", approxName);
                    return null;
                }
                MonumentDto best = chooseBestByNameWithinBbox(approxName, byName);
                if (best != null) {
                    log.info("[MonumentService] findBestMatch: melhor candidato via nome -> id={} | nome='{}'",
                            best.getId(), best.getOriginalName());
                } else {
                    log.info("[MonumentService] findBestMatch: sem match aceitável via nome para '{}'", approxName);
                }
                return best;
            }

            log.info("[MonumentService] findBestMatch: nenhum candidato encontrado (sem nome e sem coords).");
            return null;

        } catch (Exception e) {
            log.error("[MonumentService] Erro em findBestMatch", e);
            return null;
        }
    }

    private MonumentDto chooseBestByNameWithinBbox(String approxName, List<MonumentDto> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (!StringUtils.hasText(approxName)) {
            return candidates.get(0);
        }

        String normQuery = norm(approxName);
        List<String> queryTokens = tokenise(normQuery);
        if (queryTokens.isEmpty()) {
            return null;
        }

        MonumentDto best = null;
        double bestScore = 0.0;

        for (MonumentDto cand : candidates) {
            String candName = cand.getOriginalName();
            if (candName == null || candName.isBlank()) continue;

            String normCand = norm(candName);
            List<String> candTokens = tokenise(normCand);
            if (candTokens.isEmpty()) continue;

            int common = countCommonTokens(queryTokens, candTokens);
            boolean shortName = queryTokens.size() <= 2;

            if (common < 2 && !(shortName && common >= 1)) {
                continue;
            }

            double score = scoreNameMatch(queryTokens, candTokens);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }

        if (best != null) {
            log.info("[MonumentService] chooseBestByNameWithinBbox: melhor candidato para '{}' -> '{}' (score={})",
                    approxName, best.getOriginalName(), bestScore);
        }

        return best;
    }

    // ------------------------------------------------------------
    // Helpers de matching de nome
    // ------------------------------------------------------------

    private List<String> tokenise(String s) {
        if (s == null || s.isBlank()) return List.of();

        String[] parts = s.split("\\s+");
        Set<String> tokens = new HashSet<>();

        for (String p : parts) {
            String token = p.trim();
            if (token.length() <= 2) continue;
            tokens.add(token);
        }

        return new ArrayList<>(tokens);
    }

    private int countCommonTokens(List<String> a, List<String> b) {
        Set<String> setB = new HashSet<>(b);
        int common = 0;
        for (String x : a) {
            if (setB.contains(x)) common++;
        }
        return common;
    }

    private double scoreNameMatch(List<String> queryTokens, List<String> candTokens) {
        int common = countCommonTokens(queryTokens, candTokens);
        if (common == 0) return 0.0;

        int lenDiff = Math.abs(queryTokens.size() - candTokens.size());
        double lengthPenalty = 1.0 / (1.0 + lenDiff);

        return common * lengthPenalty;
    }

    /* =========================================================
       Parse WFS XML → FeatureCollection
       ========================================================= */

    private GeoJsonFeatureCollection parseWfsResponse(String xml) {
        GeoJsonFeatureCollection fc = new GeoJsonFeatureCollection();
        List<GeoJsonFeature> features = new ArrayList<>();
        fc.setFeatures(features);

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            // WFS 1.1.0 → gml:featureMember
            NodeList memberNodes = doc.getElementsByTagNameNS("*", "featureMember");
            if (memberNodes.getLength() == 0) {
                // Alguns servidores usam wfs:member (WFS 2.0)
                memberNodes = doc.getElementsByTagNameNS("*", "member");
            }

            for (int i = 0; i < memberNodes.getLength(); i++) {
                Node member = memberNodes.item(i);
                if (member.getNodeType() != Node.ELEMENT_NODE) continue;

                Element memberEl = (Element) member;

                // feature é o primeiro elemento-filho “real”
                Element featureEl = findFirstElementChild(memberEl);
                if (featureEl == null) continue;

                GeoJsonFeature feature = new GeoJsonFeature();
                feature.setType("Feature");
                feature.setId(featureEl.getAttribute("gml:id"));

                Map<String, Object> props = new LinkedHashMap<>();
                feature.setProperties(props);

                // campos de atributos SIPA (ajustar se os nomes diferirem)
                props.put("COD_SIG", getChildText(featureEl, "COD_SIG"));
                props.put("INF_NOME", getChildText(featureEl, "INF_NOME"));
                props.put("INF_MORADA", getChildText(featureEl, "INF_MORADA"));
                props.put("FREGUESIA", getChildText(featureEl, "FREGUESIA"));
                props.put("INF_DESCRICAO", getChildText(featureEl, "INF_DESCRICAO"));
                props.put("INF_SITE", getChildText(featureEl, "INF_SITE"));
                props.put("INF_EMAIL", getChildText(featureEl, "INF_EMAIL"));
                props.put("INF_TELEFONE", getChildText(featureEl, "INF_TELEFONE"));

                // geometria: gml:Point/gml:pos
                GeoJsonGeometry geom = new GeoJsonGeometry();
                geom.setType("Point");

                List<Double> coords = extractPointCoords(featureEl);
                geom.setCoordinates(coords);

                feature.setGeometry(geom);

                features.add(feature);
            }

            log.debug("[MonumentService] parseWfsResponse: extraídos {} features do XML.", features.size());

        } catch (Exception e) {
            log.error("[MonumentService] Erro a parsear XML WFS", e);
        }

        return fc;
    }

    private Element findFirstElementChild(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) n;
            }
        }
        return null;
    }

    private String getChildText(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS("*", localName);
        if (list.getLength() == 0) return null;
        Node n = list.item(0);
        return n.getTextContent().trim();
    }

    private List<Double> extractPointCoords(Element featureEl) {
        // tenta gml:Point/gml:pos
        NodeList pointNodes = featureEl.getElementsByTagNameNS("*", "Point");
        if (pointNodes.getLength() > 0) {
            Element pointEl = (Element) pointNodes.item(0);
            NodeList posNodes = pointEl.getElementsByTagNameNS("*", "pos");
            if (posNodes.getLength() > 0) {
                String[] parts = posNodes.item(0).getTextContent().trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        // assumimos x=lon, y=lat (EPSG:4326)
                        return Arrays.asList(x, y);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // fallback: lista vazia → depois o toDto trata de null
        return new ArrayList<>();
    }

    /* =========================================================
       Mapping Feature → MonumentDto
       ========================================================= */

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
                .locality(null)
                .district(null)
                .concelho(null)
                .freguesia(freguesia)
                .lat(lat)
                .lon(lon)
                .shortDescription(shortDesc)
                .fullDescriptionHtml(descricao != null ? "<p>" + descricao + "</p>" : null)
                .heritageCategory(null)
                .propertyType(null)
                .protectionStatus(null)
                .imageUrls(List.of())
                .sourceUrl(site)
                .extraAttributes(extra)
                .build();
    }

    /* =========================================================
       Helper classes internas (mesmas que tinhas)
       ========================================================= */

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
}