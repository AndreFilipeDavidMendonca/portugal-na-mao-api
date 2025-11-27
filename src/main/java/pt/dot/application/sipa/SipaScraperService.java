package pt.dot.application.sipa;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz fetch + parse de páginas do SIPA/Monumentos.
 *
 * Equivalente backend do ficheiro frontend src/lib/sipa.ts:
 *  - fetchFromSIPA({ sipaId | url })
 *  - extrai blocos de História / Descrição / Cronologia
 *  - devolve texto limpo + ano/datação.
 */
@Service
public class SipaScraperService {

    private static final Logger log = LoggerFactory.getLogger(SipaScraperService.class);

    private final WebClient webClient;

    public SipaScraperService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://www.monumentos.gov.pt")
                .build();
    }

    /* =========================================
       DTO de resultado
       ========================================= */

    public static class SipaResult {
        private final String historyText;
        private final String architectureText;
        private final String constructedYear;
        private final String sourceUrl;

        public SipaResult(String historyText, String architectureText,
                          String constructedYear, String sourceUrl) {
            this.historyText = historyText;
            this.architectureText = architectureText;
            this.constructedYear = constructedYear;
            this.sourceUrl = sourceUrl;
        }

        public String getHistoryText() {
            return historyText;
        }

        public String getArchitectureText() {
            return architectureText;
        }

        public String getConstructedYear() {
            return constructedYear;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        @Override
        public String toString() {
            return "SipaResult{" +
                    "historyText='" + preview(historyText) + '\'' +
                    ", architectureText='" + preview(architectureText) + '\'' +
                    ", constructedYear='" + constructedYear + '\'' +
                    ", sourceUrl='" + sourceUrl + '\'' +
                    '}';
        }

        private String preview(String s) {
            if (s == null) return null;
            return s.length() > 80 ? s.substring(0, 80) + "..." : s;
        }
    }

    /* =========================================
       API pública
       ========================================= */

    public SipaResult fetchFromSipaId(String sipaId) {
        if (sipaId == null || sipaId.isBlank()) {
            return new SipaResult(null, null, null, null);
        }
        String url = buildSipaUrlFromId(sipaId);
        return fetchFromUrl(url);
    }

    public SipaResult fetchFromUrl(String url) {
        try {
            String html = fetchHtml(url);
            if (html == null || html.isBlank()) {
                log.warn("[SIPA] HTML vazio para URL {}", url);
                return new SipaResult(null, null, null, url);
            }

            Document doc = Jsoup.parse(html, url);

            String history = extractBlockAfterLabel(
                    doc,
                    List.of("História", "Historial", "Enquadramento histórico", "Dados históricos")
            );

            String architecture = extractBlockAfterLabel(
                    doc,
                    List.of("Descrição", "Descrição arquitectónica", "Arquitectura",
                            "Características arquitectónicas", "Caracterização")
            );

            String chrono = extractBlockAfterLabel(
                    doc,
                    List.of("Cronologia", "Datação", "Época", "Época de construção")
            );

            String constructedYear = extractDatingFromText(history, architecture, chrono);

            SipaResult result = new SipaResult(
                    nullIfBlank(history),
                    nullIfBlank(architecture),
                    nullIfBlank(constructedYear),
                    url
            );

            log.info("[SIPA] Parsed result for {} => {}", url, result);
            return result;

        } catch (Exception e) {
            log.error("[SIPA] Erro ao processar URL {}", url, e);
            return new SipaResult(null, null, null, url);
        }
    }

    /* =========================================
       Helpers HTTP
       ========================================= */

    private String fetchHtml(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String query = uri.getQuery();

            return webClient
                    .get()
                    .uri(builder -> {
                        builder.path(path);
                        if (query != null && !query.isBlank()) {
                            // query já vem pronta tipo "?id=7075"
                            builder.query(uri.getQuery());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorResume(ex -> {
                        log.error("[SIPA] HTTP error {} para {}", ex.getMessage(), url);
                        return Mono.empty();
                    })
                    .block();

        } catch (Exception e) {
            log.error("[SIPA] Erro ao fazer fetchHtml {}", url, e);
            return null;
        }
    }

    private String buildSipaUrlFromId(String id) {
        // igual ao sipaUrlFromId do TS
        return "https://www.monumentos.gov.pt/Site/APP_PagesUser/SIPA.aspx?id=" + id;
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /* =========================================
       Parsing de blocos (equivalente ao TS)
       ========================================= */

    private String extractBlockAfterLabel(Document doc, List<String> labels) {
        // tenta headings <h2>/<h3> e bold <strong>/<b> com aqueles textos
        for (Element el : doc.getAllElements()) {
            String tag = el.tagName().toLowerCase();
            if (!tag.matches("h2|h3|strong|b")) continue;

            String text = el.text().trim();
            if (text.isEmpty()) continue;

            for (String label : labels) {
                if (equalsIgnoreCaseAndTrim(text, label)) {
                    // apanha os irmãos seguintes até ao próximo heading forte ou fim
                    StringBuilder block = new StringBuilder();
                    Element sibling = el.nextElementSibling();
                    while (sibling != null) {
                        String st = sibling.tagName().toLowerCase();
                        if (st.matches("h1|h2|h3|strong|b")) {
                            break;
                        }
                        block.append("\n").append(sibling.text());
                        sibling = sibling.nextElementSibling();
                    }
                    String cleaned = clampStr(block.toString());
                    return cleaned.isEmpty() ? null : cleaned;
                }
            }
        }
        return null;
    }

    private boolean equalsIgnoreCaseAndTrim(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceAll("\\s+", " ").trim()
                .equalsIgnoreCase(b.replaceAll("\\s+", " ").trim());
    }

    private String clampStr(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t;
    }

    /* =========================================
       Heurística de datação (igual ao TS)
       ========================================= */

    private String extractDatingFromText(String... chunks) {
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) {
            if (c != null && !c.isBlank()) {
                sb.append(c).append("\n");
            }
        }
        String txt = sb.toString();
        if (txt.isBlank()) return null;

        Pattern p = Pattern.compile(
                "\\b(?:(?:s[eé]c(?:\\.|ulo)?)\\s*[ªº]*([ivxlcdm]{1,4}))\\b" +
                        "|\\b(1[0-9]{3}|20[0-9]{2})(?:\\s*[-–]\\s*(1[0-9]{3}|20[0-9]{2}))?",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = p.matcher(txt);
        if (!m.find()) return null;

        if (m.group(1) != null) {
            String roman = m.group(1).toUpperCase();
            return "séc. " + roman;
        }

        if (m.group(2) != null && m.group(3) != null) {
            return m.group(2) + "–" + m.group(3);
        }
        if (m.group(2) != null) {
            return m.group(2);
        }
        return null;
    }
}