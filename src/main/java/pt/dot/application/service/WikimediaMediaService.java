package pt.dot.application.service;

import org.springframework.stereotype.Service;
import pt.dot.application.api.dto.CommonsMediaDto;
import pt.dot.application.util.TextNorm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WikimediaMediaService {

    private static final int MAX_IMAGES = 5;

    private static final String[] DISTRICT_REJECT_TITLE_KEYWORDS = {
            "surname",
            "esquerda.net",
            "campaign",
            "election",
            "candidate",
            "politics",
            "political",
            "florianopolis",
            "brasil",
            "brazil",
            "ghana",
            "football",
            "club",
            "people",
            "person",
            "actor",
            "actress",
            "writer",
            "album",
            "song",
            "newspaper",
            "journal",
            "party",
            "poster",
            "flyer",
            "manifesto"
    };

    private static final String[] DISTRICT_ACCEPT_TITLE_HINTS = {
            "portugal",
            "district",
            "distrito",
            "city",
            "cidade",
            "municipality",
            "concelho"
    };

    private static final String[] GENERIC_ARCHITECTURE_HINTS = {
            "castle", "castelo", "church", "igreja", "palace", "mosteiro", "convento"
    };

    private static final String[] HERO_VIEW_HINTS = {
            "panoramic", "panorama", "overview", "vista", "facade", "fachada",
            "exterior", "cityscape", "skyline"
    };

    private static final String[] HERO_DISTRICT_HINTS = {
            "panorama", "panoramic", "skyline", "cityscape", "overview",
            "vista", "view", "aerial", "seen from", "from ", "riverfront",
            "historic centre", "centro historico", "centro histórico"
    };

    private static final String[] DETAIL_PENALTY_HINTS = {
            "interior", "inside", "detail", "detalhe", "closeup", "close-up", "window", "door"
    };

    private static final String[] LOW_LIGHT_PENALTY_HINTS = {
            "night", "noite", "dark", "shadow"
    };

    private static final String[] POLITICAL_PENALTY_HINTS = {
            "poster", "campaign", "election", "flyer", "banner", "manifesto"
    };

    private static final String[] DISTRICT_SPECIFIC_POI_PENALTY_HINTS = {
            "casino", "hotel", "shopping", "mall", "stadium", "church", "igreja",
            "castle", "castelo", "museum", "museu", "monument", "tower", "torre",
            "statue", "airport", "station", "teatro", "theatre", "palace", "palacio", "palácio"
    };

    private final WikimediaCommonsClient commonsClient;
    private final WikimediaMediaCache cache;

    public WikimediaMediaService(
            WikimediaCommonsClient commonsClient,
            WikimediaMediaCache cache
    ) {
        this.commonsClient = commonsClient;
        this.cache = cache;
    }

    public List<String> getPoiMedia5(
            String namePt,
            String name,
            String source,
            List<String> baseUrls
    ) {
        List<String> base = uniq(baseUrls).stream().limit(MAX_IMAGES).toList();
        if (base.size() >= MAX_IMAGES) return base;

        List<String> queries = buildPoiQueries(namePt, name, source);
        if (queries.isEmpty()) return base;

        List<String> merged = new ArrayList<>(base);

        for (String query : queries) {
            if (merged.size() >= MAX_IMAGES) break;

            List<String> fetched = cache.getOrFetch(
                    cacheKey("commons-poi", query, MAX_IMAGES),
                    () -> fetchPoiImagesForQuery(query, source)
            );

            merged = new ArrayList<>(mergeToLimit(merged, fetched, MAX_IMAGES));
        }

        return merged.stream().limit(MAX_IMAGES).toList();
    }

    public List<String> getDistrictMedia5(String districtName, List<String> baseUrls) {
        List<String> base = uniq(baseUrls).stream().limit(MAX_IMAGES).toList();

        if (!base.isEmpty()) {
            return base;
        }

        String district = safe(districtName);
        if (district.isBlank()) return base;

        List<String> queries = buildDistrictQueries(district);
        List<String> merged = new ArrayList<>(base);

        for (String query : queries) {
            if (merged.size() >= MAX_IMAGES) break;

            List<String> fetched = cache.getOrFetch(
                    cacheKey("commons-district", query, MAX_IMAGES),
                    () -> fetchDistrictImagesForQuery(query, district)
            );

            merged = new ArrayList<>(mergeToLimit(merged, fetched, MAX_IMAGES));
        }

        return merged.stream().limit(MAX_IMAGES).toList();
    }

    private List<String> fetchPoiImagesForQuery(String query, String source) {
        System.out.println("[COMMONS][POI] trying query=" + query);

        String pageTitle = findBestCategoryTitle(query);
        if (pageTitle == null || pageTitle.isBlank()) {
            System.out.println("[COMMONS][POI] no category found for query=" + query);
            return List.of();
        }

        System.out.println("[COMMONS][POI] category found=" + pageTitle);

        List<CommonsMediaDto> media = commonsClient.fetchImagesFromCommonsPageTitle(pageTitle, MAX_IMAGES);
        List<String> best = rankAndPickPoi(media, query, extractLocality(source), MAX_IMAGES);

        System.out.println("[COMMONS][POI] images fetched=" + best.size() + " for category=" + pageTitle);
        return best;
    }

    private List<String> fetchDistrictImagesForQuery(String query, String districtName) {
        System.out.println("[COMMONS][DISTRICT] trying query=" + query);

        String pageTitle = findBestCategoryTitle(query);
        if (pageTitle == null || pageTitle.isBlank()) {
            System.out.println("[COMMONS][DISTRICT] no category found for query=" + query);
            return List.of();
        }

        if (!isGoodDistrictCategoryTitle(pageTitle, districtName)) {
            System.out.println("[COMMONS][DISTRICT] rejected category for query=" + query + " -> " + pageTitle);
            return List.of();
        }

        System.out.println("[COMMONS][DISTRICT] category found=" + pageTitle);

        List<CommonsMediaDto> media = commonsClient.fetchImagesFromCommonsPageTitle(pageTitle, MAX_IMAGES);
        List<String> best = rankAndPickDistrict(media, query, districtName, MAX_IMAGES);

        System.out.println("[COMMONS][DISTRICT] images fetched=" + best.size() + " for category=" + pageTitle);
        return best;
    }

    private String findBestCategoryTitle(String query) {
        String pageTitle = commonsClient.searchTopCategoryTitleExact(query);
        if (pageTitle == null) {
            pageTitle = commonsClient.searchTopCategoryTitleLoose(query);
        }
        return pageTitle;
    }

    private boolean isGoodDistrictCategoryTitle(String pageTitle, String districtName) {
        String title = safe(pageTitle).toLowerCase(Locale.ROOT);
        String district = safe(districtName).toLowerCase(Locale.ROOT);

        if (title.isBlank() || district.isBlank()) return false;

        String normTitle = TextNorm.normalize(title);
        String normDistrict = TextNorm.normalize(district);

        if (!normTitle.contains(normDistrict)) return false;
        if (containsAny(title, DISTRICT_REJECT_TITLE_KEYWORDS)) return false;

        return containsAny(title, DISTRICT_ACCEPT_TITLE_HINTS) || title.contains(district);
    }

    private List<String> rankAndPickPoi(
            List<CommonsMediaDto> media,
            String label,
            String locality,
            int limit
    ) {
        return rankAndPick(
                media,
                limit,
                m -> poiScore(m, label, locality)
        );
    }

    private List<String> rankAndPickDistrict(
            List<CommonsMediaDto> media,
            String label,
            String locality,
            int limit
    ) {
        return rankAndPick(
                media,
                limit,
                m -> districtScore(m, label, locality)
        );
    }

    private List<String> rankAndPick(
            List<CommonsMediaDto> media,
            int limit,
            java.util.function.ToDoubleFunction<CommonsMediaDto> scorer
    ) {
        if (media == null || media.isEmpty()) return List.of();

        List<CommonsMediaDto> sorted = media.stream()
                .sorted(Comparator.comparingDouble(scorer).reversed())
                .toList();

        List<String> out = new ArrayList<>();
        Set<String> families = new LinkedHashSet<>();

        for (CommonsMediaDto item : sorted) {
            if (item.url() == null || item.url().isBlank()) continue;

            String family = familyKey(item.title(), item.url());
            if (!family.isBlank() && families.contains(family) && out.size() < Math.max(2, limit - 1)) {
                continue;
            }

            out.add(item.url());
            if (!family.isBlank()) families.add(family);

            if (out.size() >= limit) break;
        }

        return out;
    }

    private double poiScore(CommonsMediaDto media, String label, String locality) {
        String hay = mediaHaystack(media);

        double score = baseImageScore(media);
        score += titleMatchScore(hay, label) * 2.5;

        if (hasText(locality)) {
            score += titleMatchScore(hay, locality) * 1.5;
        }

        if (containsAny(hay, GENERIC_ARCHITECTURE_HINTS)) {
            score += 1.0;
        }

        if (containsAny(hay, HERO_VIEW_HINTS)) {
            score += 2.5;
        }

        if (containsAny(hay, DETAIL_PENALTY_HINTS)) {
            score -= 2.5;
        }

        if (containsAny(hay, LOW_LIGHT_PENALTY_HINTS)) {
            score -= 1.0;
        }

        if (containsAny(hay, POLITICAL_PENALTY_HINTS)) {
            score -= 8.0;
        }

        if (looksLikeBurstSeries(media.title())) {
            score -= 1.5;
        }

        return score;
    }

    private double districtScore(CommonsMediaDto media, String label, String locality) {
        String hay = mediaHaystack(media);

        double score = baseImageScore(media);
        score += titleMatchScore(hay, label) * 2.0;

        if (hasText(locality)) {
            score += titleMatchScore(hay, locality) * 1.2;
        }

        if (containsAny(hay, HERO_DISTRICT_HINTS)) {
            score += 5.0;
        }

        if (containsAny(hay, "district", "distrito", "city", "cidade")) {
            score += 1.0;
        }

        if (containsAny(hay, DISTRICT_SPECIFIC_POI_PENALTY_HINTS)) {
            score -= 6.0;
        }

        if (containsAny(hay, POLITICAL_PENALTY_HINTS)) {
            score -= 10.0;
        }

        if (containsAny(hay, DETAIL_PENALTY_HINTS)) {
            score -= 3.0;
        }

        if (looksLikeBurstSeries(media.title())) {
            score -= 1.0;
        }

        return score;
    }

    private double baseImageScore(CommonsMediaDto media) {
        int width = media.width() != null ? media.width() : 0;
        int height = media.height() != null ? media.height() : 0;

        double score = 0;

        if (width > 0 && height > 0) {
            if (width >= height) score += 2.0;
            score += Math.min((width * (double) height) / 1_500_000.0, 4.0);
        }

        return score;
    }

    private String mediaHaystack(CommonsMediaDto media) {
        return safe(media.title()).toLowerCase(Locale.ROOT) + " " +
                safe(media.url()).toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeBurstSeries(String title) {
        String t = safe(title);
        int digits = 0;
        for (char c : t.toCharArray()) {
            if (Character.isDigit(c)) digits++;
        }
        return digits >= 8;
    }

    private boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) return true;
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

    private String familyKey(String title, String url) {
        String base = safe(title);
        if (base.isBlank()) base = safe(url);

        String n = TextNorm.normalize(base);
        n = n.replaceAll("\\b\\d{4,}\\b", " ");
        n = n.replaceAll("\\b[a-z]{0,3}\\d+[a-z\\d]*\\b", " ");
        n = n.replaceAll("\\s+", " ").trim();

        String[] parts = n.split(" ");
        if (parts.length <= 4) return n;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, parts.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private List<String> buildDistrictQueries(String districtName) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String name = safe(districtName);
        if (name.isBlank()) return List.of();

        out.add(name + " Portugal");
        out.add("Distrito de " + name);
        out.add("Distrito de " + name + " Portugal");
        out.add("District of " + name);
        out.add("District of " + name + " Portugal");
        out.add(name);

        return new ArrayList<>(out);
    }

    private List<String> buildPoiQueries(String namePt, String name, String source) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String locality = extractLocality(source);

        addQueryVariants(out, namePt, locality);
        addQueryVariants(out, namePt, null);
        addQueryVariants(out, name, locality);
        addQueryVariants(out, name, null);

        return new ArrayList<>(out);
    }

    private void addQueryVariants(Set<String> out, String rawName, String locality) {
        List<String> variants = simplifyNameVariants(rawName);

        for (String variant : variants) {
            if (variant.isBlank()) continue;

            if (hasText(locality) && !containsLocality(variant, locality)) {
                out.add(variant + " " + locality);
            }

            out.add(variant);
        }
    }

    private List<String> simplifyNameVariants(String raw) {
        if (!hasText(raw)) return List.of();

        Set<String> out = new LinkedHashSet<>();
        String value = raw.trim();

        out.add(value);

        String noParens = value
                .replaceAll("\\s*\\(.*?\\)\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        addIfNotBlank(out, noParens);

        for (String p : extractParenthesesContents(value)) {
            addIfNotBlank(out, p);
        }

        for (String piece : value.split("/")) {
            String p = piece
                    .replaceAll("\\(.*?\\)", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            addIfNotBlank(out, p);
        }

        String beforeComma = noParens.split(",")[0].trim();
        addIfNotBlank(out, beforeComma);

        String beforeColon = beforeComma.split(":")[0].trim();
        addIfNotBlank(out, beforeColon);

        return new ArrayList<>(out);
    }

    private List<String> extractParenthesesContents(String value) {
        List<String> out = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("\\((.*?)\\)").matcher(value);
        while (matcher.find()) {
            String inside = matcher.group(1);
            if (inside != null) {
                for (String piece : inside.split("/")) {
                    String p = piece.replaceAll("\\s+", " ").trim();
                    if (!p.isBlank()) out.add(p);
                }
            }
        }
        return out;
    }

    private void addIfNotBlank(Set<String> out, String value) {
        if (hasText(value)) {
            out.add(value.trim());
        }
    }

    private boolean containsLocality(String text, String locality) {
        return TextNorm.normalize(text).contains(TextNorm.normalize(locality));
    }

    private String extractLocality(String source) {
        if (!hasText(source)) return null;

        int idx = source.lastIndexOf(':');
        if (idx >= 0 && idx + 1 < source.length()) {
            String tail = source.substring(idx + 1).trim();
            return tail.isBlank() ? null : tail;
        }

        return null;
    }

    private List<String> mergeToLimit(List<String> base, List<String> extra, int limit) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(base);
        merged.addAll(extra);
        return merged.stream().limit(limit).toList();
    }

    private List<String> uniq(List<String> arr) {
        if (arr == null || arr.isEmpty()) return List.of();
        return arr.stream()
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String cacheKey(String kind, String name, int limit) {
        return kind + ":" + limit + ":" + TextNorm.normalize(name);
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}