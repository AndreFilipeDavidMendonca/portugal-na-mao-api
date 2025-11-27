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
import java.util.Locale;

@Service
public class WikipediaService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // RestTemplate vem por injeção (usa o bean com User-Agent)
    public WikipediaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Tenta obter um resumo PT (Wikipédia) para um POI, usando:
     *   1) geosearch por coordenadas (mais preciso)
     *   2) fallback search por nome
     *
     * @param approxName nome aproximado do POI (pode ser null)
     * @param lat        latitude (pode ser null)
     * @param lon        longitude (pode ser null)
     * @param category   categoria do POI (church, castle, monument, cultural, ...)
     */
    public WikipediaSummary fetchSummary(String approxName, Double lat, Double lon, String category) {
        if ((approxName == null || approxName.isBlank()) && (lat == null || lon == null)) {
            return null;
        }

        try {
            WikiTag tag = null;

            // 1) Primeiro: geosearch (se tivermos coords)
            if (lat != null && lon != null) {
                log.debug("[Wikipedia] fetchSummary → tentar GEOSEARCH name='{}' lat={} lon={} category={}",
                        approxName, lat, lon, category);
                tag = geosearchTitle(lat, lon, approxName, "pt", category);
            }

            // 2) Se geosearch não deu nada → tentar por nome
            if (tag == null && approxName != null && !approxName.isBlank()) {
                log.debug("[Wikipedia] fetchSummary → fallback SEARCH BY NAME name='{}' category={}",
                        approxName, category);
                tag = searchTitleByName(approxName, "pt", category);
            }

            if (tag == null) {
                log.info("[Wikipedia] Nenhum título encontrado para name='{}'", approxName);
                return null;
            }

            log.info("[Wikipedia] Título escolhido lang={} title='{}'", tag.lang(), tag.title());

            // 3) Buscar summary + imagem
            return fetchStrictSummary(tag.lang, tag.title, lat, lon);

        } catch (Exception e) {
            log.error("[Wikipedia] Erro em fetchSummary(name='{}')", approxName, e);
            return null;
        }
    }

    /* =====================================================
       1) SEARCH POR NOME
       ===================================================== */

    private WikiTag searchTitleByName(String name, String lang, String category) {
        try {
            String api = "https://" + lang +
                    ".wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    URLEncoder.encode(name, StandardCharsets.UTF_8) +
                    "&format=json";

            log.debug("[Wikipedia][byName] URL={}", api);

            String json = restTemplate.getForObject(api, String.class);
            log.debug("[Wikipedia][byName] RAW response={}", json);

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("query").path("search");

            if (!arr.isArray() || arr.isEmpty()) return null;

            String normPoi = normalizeName(name);
            String cat = category != null ? category.toLowerCase(Locale.ROOT) : "";

            WikiTag fallback = null;

            for (JsonNode node : arr) {
                String title = node.path("title").asText(null);
                if (title == null) continue;

                String normTitle = normalizeName(title);

                boolean matchWord = false;
                for (String w : normPoi.split(" ")) {
                    if (w.length() > 2 && normTitle.contains(w)) {
                        matchWord = true;
                        break;
                    }
                }

                boolean matchCategory = false;
                switch (cat) {
                    case "church" -> matchCategory = normTitle.contains("igreja")
                            || normTitle.contains("capela")
                            || normTitle.contains("ermida")
                            || normTitle.contains("mosteiro")
                            || normTitle.contains("convento");
                    case "castle" -> matchCategory = normTitle.contains("castelo")
                            || normTitle.contains("fortaleza");
                    case "monument" -> matchCategory = normTitle.contains("monumento");
                    case "cultural" -> matchCategory = true; // cultural é flexível
                    default -> {
                        // cat vazia → não força
                    }
                }

                log.debug("[Wikipedia][byName] candidate='{}' norm='{}' matchWord={} matchCategory={}",
                        title, normTitle, matchWord, matchCategory);

                if (fallback == null) {
                    fallback = new WikiTag(lang, title);
                }

                // match mais forte
                if (matchWord || matchCategory) {
                    log.info("[Wikipedia][byName] match forte encontrado: {}", title);
                    return new WikiTag(lang, title);
                }
            }

            if (fallback != null) {
                log.info("[Wikipedia][byName] fallback usado: {}", fallback.title());
            }

            return fallback;

        } catch (Exception e) {
            log.warn("[Wikipedia] searchTitleByName falhou: {}", e.getMessage());
            return null;
        }
    }

    /* =====================================================
       2) GEOSEARCH POR COORDENADAS
       ===================================================== */

    private WikiTag geosearchTitle(Double lat, Double lon, String poiName, String lang, String poiCategory) {
        try {
            String api = "https://" + lang +
                    ".wikipedia.org/w/api.php?action=query&list=geosearch&" +
                    "gscoord=" + lat + "|" + lon +
                    "&gsradius=1500&gslimit=20&format=json";

            log.debug("[Wikipedia][geosearch] URL={}", api);

            String json = restTemplate.getForObject(api, String.class);
            log.debug("[Wikipedia][geosearch] RAW response={}", json);

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("query").path("geosearch");

            if (!arr.isArray() || arr.isEmpty()) return null;

            String normPoi = normalizeName(poiName);
            String cat = poiCategory != null ? poiCategory.toLowerCase(Locale.ROOT) : "";

            WikiTag best = null;

            for (JsonNode node : arr) {
                String title = node.path("title").asText(null);
                if (title == null) continue;

                String normTitle = normalizeName(title);

                // ---- MATCH: pelo menos 1 palavra igual ----
                boolean matchWord = false;
                if (normPoi != null) {
                    for (String word : normPoi.split(" ")) {
                        if (word.length() > 2 && normTitle.contains(word)) {
                            matchWord = true;
                            break;
                        }
                    }
                }

                // ---- Categoria no título (ex.: "igreja", "castelo") ----
                String lowerTitle = title.toLowerCase(Locale.ROOT);
                boolean matchCategory = false;
                switch (cat) {
                    case "church" -> matchCategory = lowerTitle.contains("igreja")
                            || lowerTitle.contains("capela")
                            || lowerTitle.contains("ermida")
                            || lowerTitle.contains("mosteiro")
                            || lowerTitle.contains("convento");
                    case "castle" -> matchCategory = lowerTitle.contains("castelo")
                            || lowerTitle.contains("fortaleza");
                    case "monument" -> matchCategory = lowerTitle.contains("monumento");
                    case "cultural" -> matchCategory = true; // cultural é flexível
                    default -> {
                    }
                }

                log.debug("[Wikipedia][geosearch] candidate title='{}' norm='{}' matchWord={} matchCategory={}",
                        title, normTitle, matchWord, matchCategory);

                // regra final → aceita se bate na categoria OU partilha palavras
                if (matchWord || matchCategory) {
                    best = new WikiTag(lang, title);
                    break;
                }

                if (best == null) best = new WikiTag(lang, title); // fallback se nada bater forte
            }

            log.info("[Wikipedia][geosearch] Escolhido: {}", best != null ? best.title() : "nenhum");
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
                .toLowerCase(Locale.ROOT);
    }

    /* =====================================================
       3) SUMMARY + LOG DETALHADO
       ===================================================== */

    private WikipediaSummary fetchStrictSummary(String lang, String title, Double lat, Double lon) {
        try {
            String url = "https://" + lang + ".wikipedia.org/w/api.php" +
                    "?action=query&prop=extracts|pageimages|coordinates" +
                    "&exintro=1&explaintext=1&piprop=thumbnail&pithumbsize=1600" +
                    "&format=json&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8);

            log.debug("[Wikipedia][summary] URL={}", url);

            String json = restTemplate.getForObject(url, String.class);
            log.debug("[Wikipedia][summary] RAW response={}", json);

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode pages = root.path("query").path("pages");
            if (!pages.fields().hasNext()) return null;

            JsonNode page = pages.fields().next().getValue();

            String summary = page.path("extract").asText(null);
            String image = page.path("thumbnail").path("source").asText(null);
            String pageTitle = page.path("title").asText(title);

            if (summary == null || summary.isBlank()) {
                log.info("[Wikipedia][summary] Sem summary para '{}'", title);
                return null;
            }

            // Se vierem coordenadas do artigo, podemos futuramente validar distância aqui.
            // (distanceKm(...) já existe se quisermos ativar este filtro mais tarde.)

            String trimmedSummary = summary.substring(0, Math.min(summary.length(), 200));

            log.info("""
                    [Wikipedia][summary]
                    Título: {}
                    Summary (200 chars): {}
                    Imagem: {}
                    """.formatted(pageTitle, trimmedSummary, image));

            String pageUrl = "https://" + lang + ".wikipedia.org/wiki/" + pageTitle.replace(" ", "_");

            return new WikipediaSummary(summary, pageUrl, image);

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