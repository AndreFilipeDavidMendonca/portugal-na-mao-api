package pt.dot.monumentosproxy.monumentos;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonumentService {

    private final WebClient monumentosWebClient;

    public MonumentService(WebClient monumentosWebClient) {
        this.monumentosWebClient = monumentosWebClient;
    }

    /* ------------------------- DEBUG HTML RAW ------------------------- */

    public String fetchRawSipaPage(String idOrName) {
        try {
            String encoded = URLEncoder.encode(idOrName, StandardCharsets.UTF_8);
            String path = "/Site/APP_PagesUser/SIPA.aspx?id=" + encoded;

            return monumentosWebClient
                    .get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(ex -> {
                        System.err.println("[SIPA RAW] erro: " + ex.getMessage());
                        return Mono.empty();
                    })
                    .blockOptional()
                    .orElse("");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /* ------------------------ NORMALIZATION ------------------------ */

    private String norm(String value) {
        if (value == null) return null;

        String base = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        return base;
    }

    /* ------------------------ SEARCH BY NAME ------------------------ */

    @Cacheable(cacheNames = "monumentsByName", key = "#name.toLowerCase()")
    public List<MonumentDto> searchByName(String name) {
        if (!StringUtils.hasText(name)) return List.of();

        try {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String path = "/Site/APP_PagesUser/SIPA.aspx?id=" + encoded;

            String html = monumentosWebClient
                    .get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(ex -> Mono.empty())
                    .block();

            if (html == null || html.isBlank()) return List.of();

            Document doc = Jsoup.parse(html);

            // Tentativa: página de detalhe
            MonumentDto single = parseDetailPage(doc, null);
            if (single != null) return List.of(single);

            // Tentativa: lista
            List<MonumentDto> list = new ArrayList<>();
            for (Element card : doc.select(".resultado, .result, tr")) {
                MonumentDto dto = parseListCard(card);
                if (dto != null) list.add(dto);
            }

            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /* ------------------------ PARSERS ------------------------ */

    private String text(Element e) {
        return e != null ? e.text() : null;
    }

    private MonumentDto parseListCard(Element card) {
        String name = text(card.selectFirst("a, strong, h2, h3"));
        if (!StringUtils.hasText(name)) return null;

        String href = Optional.ofNullable(card.selectFirst("a"))
                .map(a -> a.absUrl("href"))
                .orElse(null);

        String slug = null;
        if (href != null && href.contains("id=")) {
            slug = href.substring(href.indexOf("id=") + 3);
        }

        return MonumentDto.builder()
                .id(slug)
                .slug(slug)
                .originalName(name)
                .normalizedName(norm(name))
                .shortDescription(text(card.selectFirst("p")))
                .sourceUrl(href)
                .build();
    }

    private MonumentDto parseDetailPage(Document doc, String slug) {
        Element titleEl = doc.selectFirst("h1, .titulo, .title");
        if (titleEl == null) return null;

        String name = titleEl.text();

        // campos básicos
        String shortDesc = text(doc.selectFirst(".descricao, .texto, p"));
        String fullHtml = Optional.ofNullable(doc.selectFirst(".descricao, .texto"))
                .map(Element::html)
                .orElse(null);

        // imagens
        List<String> images = doc.select("img").stream()
                .map(img -> img.absUrl("src"))
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(20)
                .toList();

        // tabelas
        Map<String, String> extra = new LinkedHashMap<>();

        for (Element row : doc.select("table tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th != null && td != null) extra.put(th.text(), td.text());
        }

        // <dl> estruturas
        for (Element dl : doc.select("dl")) {
            Elements dts = dl.select("dt");
            Elements dds = dl.select("dd");
            for (int i = 0; i < Math.min(dts.size(), dds.size()); i++) {
                extra.putIfAbsent(dts.get(i).text(), dds.get(i).text());
            }
        }

        return MonumentDto.builder()
                .id(slug)
                .slug(slug)
                .originalName(name)
                .normalizedName(norm(name))
                .shortDescription(shortDesc)
                .fullDescriptionHtml(fullHtml)
                .imageUrls(images)
                .extraAttributes(extra)
                .sourceUrl(doc.location())
                .build();
    }
}