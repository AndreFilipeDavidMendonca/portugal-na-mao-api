// src/main/java/pt/dot/application/monumentos/MonumentWfsService.java
package pt.dot.application.monumentos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class MonumentWfsService {

    private static final Logger log = LoggerFactory.getLogger(MonumentWfsService.class);

    private final MonumentService monumentService;

    public MonumentWfsService(MonumentService monumentService) {
        this.monumentService = monumentService;
    }

    /**
     * Resolve o "melhor" monumento dado um nome + coordenadas do clique.
     * Estratégia:
     *  - procura por nome via WFS (MonumentService.searchByName)
     *  - entre os candidatos com lat/lon, escolhe o mais próximo do clique
     *  - se ninguém tiver coords, usa o primeiro como está e aplica coords do clique
     *  - se não houver candidatos, usa fallback dummy
     */
    public MonumentDto findBestMatch(String name, double lat, double lon) {
        log.info("[MonumentWfsService] findBestMatch() called. name='{}', lat={}, lon={}", name, lat, lon);

        List<MonumentDto> candidates = monumentService.searchByName(name);
        int count = (candidates == null) ? 0 : candidates.size();
        log.info("[MonumentWfsService] Candidates size = {}", count);

        if (candidates == null || candidates.isEmpty()) {
            log.warn("[MonumentWfsService] No candidates found – using DUMMY fallback.");
            return createDummy(name, lat, lon);
        }

        // Tenta escolher o candidato com coordenadas mais próximas
        MonumentDto bestWithCoords = candidates.stream()
                .filter(c -> c.getLat() != null && c.getLon() != null)
                .min(Comparator.comparingDouble(c ->
                        distanceKm(lat, lon, c.getLat(), c.getLon())
                ))
                .orElse(null);

        MonumentDto best;
        if (bestWithCoords != null) {
            best = bestWithCoords;
            log.info("[MonumentWfsService] Best candidate WITH coords: id={}, originalName='{}', lat={}, lon={}",
                    best.getId(), best.getOriginalName(), best.getLat(), best.getLon());
        } else {
            best = candidates.get(0);
            log.info("[MonumentWfsService] Best candidate WITHOUT coords (fallback to first): id={}, originalName='{}'",
                    best.getId(), best.getOriginalName());
        }

        if (best.getLat() == null || best.getLon() == null) {
            log.debug("[MonumentWfsService] Best candidate sem coords – aplicar coords do clique.");
            best.setLat(lat);
            best.setLon(lon);
        }

        return best;
    }

    /**
     * Pesquisa por bounding box – delega para MonumentService (WFS + bbox).
     */
    public List<MonumentDto> searchByBbox(double minX, double minY, double maxX, double maxY) {
        log.info("[MonumentWfsService] searchByBbox() called. minX={}, minY={}, maxX={}, maxY={}",
                minX, minY, maxX, maxY);

        List<MonumentDto> result = monumentService.searchByBbox(minX, minY, maxX, maxY);
        log.info("[MonumentWfsService] searchByBbox() → {} resultados", result.size());
        return result;
    }

    /* ----------------- Helpers ----------------- */

    private MonumentDto createDummy(String name, double lat, double lon) {
        MonumentDto dummy = MonumentDto.builder()
                .id("dummy-sipa-123")
                .slug("dummy")
                .originalName(name != null ? name : "Monumento desconhecido")
                .normalizedName(name != null ? name.toLowerCase() : "monumento-desconhecido")
                .locality("Desconhecida")
                .district("Desconhecida")
                .concelho("Desconhecido")
                .freguesia("Desconhecida")
                .lat(lat)
                .lon(lon)
                .shortDescription("Dummy description apenas para testar o fluxo.")
                .fullDescriptionHtml("<p>Descrição longa dummy do monumento. (Fallback)</p>")
                .heritageCategory("Desconhecido")
                .propertyType("Desconhecido")
                .protectionStatus("Desconhecido")
                .imageUrls(List.of())
                .sourceUrl(null)
                .extraAttributes(Map.of(
                        "Estado", "Fallback dummy (sem dados reais)",
                        "Origem dos dados", "Dummy local no MonumentWfsService"
                ))
                .build();

        log.info("[MonumentWfsService] Dummy created: id={}, originalName='{}'",
                dummy.getId(), dummy.getOriginalName());
        return dummy;
    }

    /**
     * Distância aproximada em km usando fórmula de Haversine.
     */
    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // raio da Terra em km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}