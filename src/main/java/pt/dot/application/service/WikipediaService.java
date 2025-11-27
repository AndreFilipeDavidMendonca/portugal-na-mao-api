// src/main/java/pt/dot/application/service/WikipediaService.java
package pt.dot.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pt.dot.application.service.PoiEnrichmentService.WikipediaSummary;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WikipediaService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // RestTemplate passa a vir por injeção (usa o bean com User-Agent)
    public WikipediaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Tenta obter um resumo PT (Wikipédia) para um POI,
     * usando:
     *   1) pesquisa por nome
     *   2) fallback geosearch por coordenadas
     */
    public WikipediaSummary fetchSummary(String approxName, Double lat, Double lon) {
        if ((approxName == null || approxName.isBlank()) && (lat == null || lon == null)) {
            return null;
        }

        try {
            // 1) tentar encontrar título PT por nome
            WikiTag tag = null;
            if (approxName != null && !approxName.isBlank()) {
                tag = searchTitleByName(approxName, "pt");
            }

            // 2) se não encontrou por nome e temos coords → tentar geosearch
            if (tag == null && lat != null && lon != null) {
                tag = geosearchTitle(lat, lon, approxName, "pt");
            }

            if (tag == null) {
                log.info("[Wikipedia] Nenhum título encontrado para name='{}'", approxName);
                return null;
            }

            // 3) buscar summary
            return fetchStrictSummary(tag.lang, tag.title, lat, lon);

        } catch (Exception e) {
            log.error("[Wikipedia] Erro em fetchSummary(name='{}')", approxName, e);
            return null;
        }
    }

    /* =====================================================
       1) SEARCH POR NOME
       ===================================================== */

    private WikiTag searchTitleByName(String name, String lang) {
        try {
            String api = "https://" + lang +
                    ".wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8) +
                    "&format=json";

            String json = restTemplate.getForObject(api, String.class);
            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode first = root.path("query").path("search").path(0);
            if (first.isMissingNode()) return null;

            String title = first.path("title").asText(null);
            if (title == null || title.isBlank()) return null;

            return new WikiTag(lang, title);
        } catch (Exception e) {
            log.warn("[Wikipedia] searchTitleByName falhou: {}", e.getMessage());
            return null;
        }
    }

    /* =====================================================
       2) GEOSEARCH POR COORDENADAS
       ===================================================== */

    private WikiTag geosearchTitle(Double lat, Double lon, String name, String lang) {
        try {
            String api = "https://" + lang +
                    ".wikipedia.org/w/api.php?action=query&list=geosearch&" +
                    "gscoord=" + lat + "|" + lon +
                    "&gsradius=800&gslimit=20&format=json";

            String json = restTemplate.getForObject(api, String.class);
            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("query").path("geosearch");
            if (!arr.isArray() || arr.isEmpty()) return null;

            // melhor tentativa: se tiver nome, tenta encontrar algo que contenha a 1ª palavra normalizada
            String normQuery = normalizeName(name);
            WikiTag best = null;

            for (JsonNode node : arr) {
                String title = node.path("title").asText(null);
                if (title == null) continue;

                if (normQuery != null && !normQuery.isBlank()) {
                    String normTitle = normalizeName(title);
                    if (normTitle.contains(normQuery)) {
                        best = new WikiTag(lang, title);
                        break;
                    }
                }

                if (best == null) {
                    best = new WikiTag(lang, title);
                }
            }
            return best;
        } catch (Exception e) {
            log.warn("[Wikipedia] geosearchTitle falhou: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeName(String s) {
        if (s == null) return null;
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    /* =====================================================
       3) SUMMARY + VALIDAÇÃO DE DISTÂNCIA
       ===================================================== */

    private WikipediaSummary fetchStrictSummary(String lang, String title, Double lat, Double lon) {
        try {
            String url = "https://" + lang + ".wikipedia.org/w/api.php" +
                    "?action=query" +
                    "&prop=extracts|pageimages|coordinates" +
                    "&exintro=1" +
                    "&explaintext=1" +
                    "&format=json" +
                    "&piprop=thumbnail" +
                    "&pithumbsize=1600" +
                    "&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8);

            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode pages = root.path("query").path("pages");
            if (!pages.fields().hasNext()) return null;

            // primeiro page (a API devolve um map de ids -> page)
            JsonNode firstPage = pages.fields().next().getValue();

            String summary = firstPage.path("extract").asText(null);
            if (summary == null || summary.isBlank()) return null;

            // construir URL da página
            String pageTitle = firstPage.path("title").asText(title);
            String pageUrl = "https://" + lang + ".wikipedia.org/wiki/" +
                    URLEncoder.encode(pageTitle.replace(' ', '_'), StandardCharsets.UTF_8);

            // validar distância se vierem coordinates
            JsonNode coordNode = firstPage.path("coordinates");
            if (coordNode.isArray() && coordNode.size() > 0 && lat != null && lon != null) {
                JsonNode c0 = coordNode.get(0);
                double wlat = c0.path("lat").asDouble();
                double wlon = c0.path("lon").asDouble();
                double distKm = distanceKm(lat, lon, wlat, wlon);
                if (distKm > 60.0) {
                    log.info("[Wikipedia] summary encontrado mas a {} km do ponto (ignorando)", distKm);
                    return null;
                }
            }

            // thumbnail (se existir)
            String imageUrl = null;
            JsonNode thumb = firstPage.path("thumbnail").path("source");
            if (!thumb.isMissingNode() && !thumb.isNull()) {
                imageUrl = thumb.asText();
            }

            return new WikipediaSummary(summary, pageUrl, imageUrl);
        } catch (Exception e) {
            log.warn("[Wikipedia] fetchStrictSummary falhou: {}", e.getMessage());
            return null;
        }
    }

    /* =====================================================
       Helpers
       ===================================================== */

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /* DTO simples interno */

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WikiTag(String lang, String title) {}
}