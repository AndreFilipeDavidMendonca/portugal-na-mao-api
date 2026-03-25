package pt.dot.application.service.wikimedia;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.dot.application.api.dto.common.CommonsMediaDto;
import pt.dot.application.util.TextNorm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class WikimediaCommonsClient {

    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";

    private static final Set<String> BAD_KEYWORDS = Set.of(
            ".svg",
            "logo",
            "openstreetmap",
            "gnd",
            "pencil",
            "icon",
            "symbol",
            "map",
            "locator",
            "blank",
            "disambig",
            "wikidata",
            "commons-logo",
            "edit icon",
            "powered by",
            "flag",
            "coat of arms",
            "brasao",
            "brasão",
            "seal",
            "diagram",
            "plan",
            "floorplan",
            "drawing",
            "sketch"
    );

    private final RestClient restClient;

    public WikimediaCommonsClient() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(8000);

        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .baseUrl(COMMONS_API)
                .defaultHeader("User-Agent", "pt-dot-dev/1.0 (local development; contact: andre.mendonca@example.com)")
                .defaultHeader("Api-User-Agent", "pt-dot-dev/1.0 (local development; contact: andre.mendonca@example.com)")
                .build();
    }

    public String searchTopCategoryTitleExact(String query) {
        if (!hasText(query)) return null;

        String q = query.trim();
        String title = tryCategorySearch("intitle:\"" + q + "\"");
        if (title == null) {
            title = tryCategorySearch("\"" + q + "\"");
        }
        return title;
    }

    public String searchTopCategoryTitleLoose(String query) {
        if (!hasText(query)) return null;

        String q = query.trim();
        String title = tryCategorySearch("intitle:\"" + q + "\"");
        if (title == null) {
            title = tryCategorySearch(q);
        }
        return title;
    }

    public List<CommonsMediaDto> fetchImagesFromCommonsPageTitle(String pageTitle, int limit) {
        if (!hasText(pageTitle)) return List.of();

        try {
            JsonNode json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("generator", "categorymembers")
                            .queryParam("gcmtitle", pageTitle.trim())
                            .queryParam("gcmtype", "file")
                            .queryParam("gcmlimit", String.valueOf(Math.max(30, limit * 10)))
                            .queryParam("prop", "imageinfo")
                            .queryParam("iiprop", "url|size")
                            .queryParam("iiurlwidth", "1600")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            return extractAndFilterMedia(json, Math.max(30, limit * 6));
        } catch (Exception e) {
            System.out.println("[COMMONS] fetchImagesFromCommonsPageTitle exception for title=" + pageTitle + " -> " + e.getMessage());
            return List.of();
        }
    }

    private String tryCategorySearch(String srsearch) {
        try {
            JsonNode json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("list", "search")
                            .queryParam("srsearch", srsearch)
                            .queryParam("srnamespace", "14")
                            .queryParam("srlimit", "5")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode error = json.path("error");
            if (!error.isMissingNode() && !error.isEmpty()) {
                System.out.println("[COMMONS] search error for srsearch=" + srsearch + " -> " + error);
                return null;
            }

            JsonNode arr = json.path("query").path("search");
            if (!arr.isArray() || arr.isEmpty()) return null;

            String cleanedQuery = srsearch
                    .replace("\"", "")
                    .replace("intitle:", "");
            String normQ = TextNorm.normalize(cleanedQuery);

            String bestTitle = null;
            int bestScore = Integer.MIN_VALUE;

            for (JsonNode node : arr) {
                String title = node.path("title").asText(null);
                if (!hasText(title)) continue;

                int score = 0;
                if (TextNorm.normalize(title).equals(normQ)) {
                    score += 100;
                }
                score += titleMatchScore(title, cleanedQuery) * 10;

                if (score > bestScore) {
                    bestScore = score;
                    bestTitle = title;
                }
            }

            return bestTitle;
        } catch (Exception e) {
            System.out.println("[COMMONS] exception for srsearch=" + srsearch + " -> " + e.getMessage());
            return null;
        }
    }

    private List<CommonsMediaDto> extractAndFilterMedia(JsonNode json, int limit) {
        JsonNode pages = json.path("query").path("pages");
        if (pages.isMissingNode() || !pages.isObject()) return List.of();

        List<CommonsMediaDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Iterator<JsonNode> it = pages.elements();

        while (it.hasNext()) {
            JsonNode page = it.next();

            String title = page.path("title").asText("");
            JsonNode infos = page.path("imageinfo");
            JsonNode info = (infos.isArray() && !infos.isEmpty()) ? infos.get(0) : null;
            if (info == null || info.isMissingNode()) continue;

            String url = info.path("thumburl").asText(null);
            if (!hasText(url)) {
                url = info.path("url").asText(null);
            }

            if (!hasText(url)) continue;
            if (isBadImage(title, url)) continue;
            if (!seen.add(url)) continue;

            Integer width = info.path("width").isNumber() ? info.path("width").asInt() : null;
            Integer height = info.path("height").isNumber() ? info.path("height").asInt() : null;

            out.add(new CommonsMediaDto(url, title, width, height));
            if (out.size() >= limit) break;
        }

        return out;
    }

    private boolean isBadImage(String title, String url) {
        String hay = (safe(title) + " " + safe(url)).toLowerCase(Locale.ROOT);

        for (String bad : BAD_KEYWORDS) {
            if (hay.contains(bad)) return true;
        }

        return false;
    }

    private int titleMatchScore(String titleOrUrl, String query) {
        var tokens = TextNorm.tokensOf(query);
        if (tokens.isEmpty()) return 0;

        String hay = TextNorm.normalize(titleOrUrl);
        long hits = tokens.stream().filter(hay::contains).count();
        double ratio = (double) hits / tokens.size();

        if (ratio >= 0.85) return 7;
        if (ratio >= 0.60) return 4;
        if (ratio >= 0.34) return 1;
        return -3;
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}