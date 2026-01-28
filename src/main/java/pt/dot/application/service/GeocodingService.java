package pt.dot.application.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import pt.dot.application.api.dto.GeocodeRequestDto;
import pt.dot.application.api.dto.GeocodeResponseDto;

import java.util.*;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
public class GeocodingService {

    private final RestClient nominatim;

    public GeocodingService(RestClient nominatimRestClient) {
        this.nominatim = nominatimRestClient;
    }

    private static final Pattern WS = Pattern.compile("\\s+");

    @Cacheable(cacheNames = "geocode", key = "T(pt.dot.application.service.GeocodingService).cacheKey(#req)")
    public GeocodeResponseDto geocode(GeocodeRequestDto req) {
        if (req == null) throw new ResponseStatusException(BAD_REQUEST, "Body em falta");

        String street = norm(req.getStreet());
        String city = norm(req.getCity());
        if (street.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "street é obrigatório");
        if (city.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "city é obrigatório");

        String number = norm(req.getHouseNumber());
        String postal = norm(req.getPostalCode());
        String district = norm(req.getDistrict());
        String country = norm(req.getCountry());
        if (country.isBlank()) country = "Portugal";

        // Tentativas (do mais preciso para o mais permissivo)
        List<QueryAttempt> attempts = List.of(
                new QueryAttempt(street, number, postal, city, district, country),
                new QueryAttempt(street, number, "",    city, district, country),
                new QueryAttempt(street, "",     postal, city, district, country),
                new QueryAttempt(street, "",     "",    city, district, country)
        );

        for (int i = 0; i < attempts.size(); i++) {
            QueryAttempt a = attempts.get(i);

            List<NominatimResult> results = callNominatim(a);
            Optional<NominatimResult> best = pickBest(results, req);

            if (best.isPresent()) {
                NominatimResult r = best.get();
                double conf = confidence(r, req);

                // se foi preciso descer muito na precisão, baixa um bocado
                conf = Math.max(0, conf - (i * 0.08));

                return new GeocodeResponseDto(
                        safeDouble(r.lat),
                        safeDouble(r.lon),
                        r.display_name,
                        "nominatim",
                        clamp01(conf)
                );
            }
        }

        throw new ResponseStatusException(NOT_FOUND, "Morada não encontrada (geocode)");
    }

    /* =========================
       Nominatim HTTP
    ========================= */

    private List<NominatimResult> callNominatim(QueryAttempt a) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("format", "jsonv2");
        q.add("limit", "8");
        q.add("addressdetails", "1");
        q.add("countrycodes", "pt"); // força PT
        q.add("accept-language", "pt");

        // Monta um "q" simples (Nominatim funciona bem assim)
        q.add("q", a.toQueryString());

        try {
            NominatimResult[] arr = nominatim.get()
                    .uri(uriBuilder -> uriBuilder.path("/search").queryParams(q).build())
                    .retrieve()
                    .body(NominatimResult[].class);

            if (arr == null) return List.of();
            return Arrays.asList(arr);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Falha no serviço de geocoding");
        }
    }

    /* =========================
       Escolha do melhor resultado
    ========================= */

    private Optional<NominatimResult> pickBest(List<NominatimResult> results, GeocodeRequestDto req) {
        if (results == null || results.isEmpty()) return Optional.empty();

        // Filtra para PT e que tenha lat/lon
        List<NominatimResult> valid = results.stream()
                .filter(r -> r != null && r.lat != null && r.lon != null)
                .filter(r -> (r.address == null || r.address.country_code == null) || "pt".equalsIgnoreCase(r.address.country_code))
                .toList();

        if (valid.isEmpty()) return Optional.empty();

        // Scoring simples
        String city = norm(req.getCity());
        String postal = norm(req.getPostalCode());
        String street = norm(req.getStreet());
        String number = norm(req.getHouseNumber());

        record Scored(NominatimResult r, double score) {}

        return valid.stream()
                .map(r -> new Scored(r, score(r, street, number, postal, city)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .map(Scored::r)
                .findFirst();
    }

    private double score(NominatimResult r, String street, String number, String postal, String city) {
        double s = 0.0;

        // importância do Nominatim (quando existe)
        if (r.importance != null) s += clamp01(r.importance) * 0.6;

        String dn = norm(r.display_name);

        if (!city.isBlank() && containsLoose(dn, city)) s += 0.35;
        if (!postal.isBlank() && containsLoose(dn, postal)) s += 0.25;
        if (!street.isBlank() && containsLoose(dn, street)) s += 0.25;
        if (!number.isBlank() && containsLoose(dn, number)) s += 0.10;

        // addressdetails (quando existe)
        if (r.address != null) {
            String rc = norm(r.address.city);
            String rtown = norm(r.address.town);
            String rvillage = norm(r.address.village);
            String rpostcode = norm(r.address.postcode);

            if (!city.isBlank() && (containsLoose(rc, city) || containsLoose(rtown, city) || containsLoose(rvillage, city))) s += 0.30;
            if (!postal.isBlank() && containsLoose(rpostcode, postal)) s += 0.25;
        }

        // penaliza resultados muito vagos
        if (r.type != null) {
            String t = r.type.toLowerCase(Locale.ROOT);
            if (t.contains("city") || t.contains("administrative")) s -= 0.25;
        }

        return s;
    }

    private double confidence(NominatimResult r, GeocodeRequestDto req) {
        // Reusa score mas normaliza para 0..1
        double s = score(r, norm(req.getStreet()), norm(req.getHouseNumber()), norm(req.getPostalCode()), norm(req.getCity()));
        // score típico ~0..1.2 -> normaliza
        return clamp01(s / 1.1);
    }

    /* =========================
       Helpers
    ========================= */

    public static String cacheKey(GeocodeRequestDto req) {
        if (req == null) return "null";
        return String.join("|",
                norm(req.getStreet()),
                norm(req.getHouseNumber()),
                norm(req.getPostalCode()),
                norm(req.getCity()),
                norm(req.getDistrict()),
                norm(req.getCountry()).isBlank() ? "Portugal" : norm(req.getCountry())
        );
    }

    private static String norm(String s) {
        if (s == null) return "";
        String x = s.trim();
        x = WS.matcher(x).replaceAll(" ");
        return x;
    }

    private static boolean containsLoose(String hay, String needle) {
        if (hay == null || needle == null) return false;
        String h = hay.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return !n.isBlank() && h.contains(n);
    }

    private static Double safeDouble(String v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0;
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /* =========================
       Models (Nominatim)
    ========================= */

    static class NominatimResult {
        public String lat;
        public String lon;
        public String display_name;
        public Double importance;
        public String type;
        public NominatimAddress address;
    }

    static class NominatimAddress {
        public String country_code;
        public String postcode;

        public String city;
        public String town;
        public String village;
    }

    static class QueryAttempt {
        final String street, number, postal, city, district, country;

        QueryAttempt(String street, String number, String postal, String city, String district, String country) {
            this.street = street;
            this.number = number;
            this.postal = postal;
            this.city = city;
            this.district = district;
            this.country = country;
        }

        String toQueryString() {
            // Ex: "Rua X 12, 1000-001 Lisboa, Lisboa, Portugal"
            List<String> parts = new ArrayList<>();
            String streetLine = street;
            if (!number.isBlank()) streetLine = streetLine + " " + number;
            parts.add(streetLine);

            if (!postal.isBlank()) parts.add(postal);
            parts.add(city);
            if (!district.isBlank() && !district.equalsIgnoreCase(city)) parts.add(district);
            parts.add(country);

            return String.join(", ", parts);
        }
    }
}