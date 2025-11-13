package pt.dot.monumentosproxy.monumentos;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MonumentWfsService {

    private final MonumentService monumentService;

    public MonumentWfsService(MonumentService monumentService) {
        this.monumentService = monumentService;
    }

    /**
     * Resolve o "melhor" monumento dado um nome + coordenadas do clique.
     */
    public MonumentDto findBestMatch(String name, double lat, double lon) {
        System.out.println("[MonumentWfsService] findBestMatch name=" + name +
                " lat=" + lat + " lon=" + lon);

        // 1) tentar de verdade no SIPA
        List<MonumentDto> candidates = monumentService.searchByName(name);

        if (candidates != null && !candidates.isEmpty()) {
            MonumentDto best = candidates.get(0);

            // se o SIPA não tiver coords, usamos as do click
            if (best.getLat() == null || best.getLon() == null) {
                best.setLat(lat);
                best.setLon(lon);
            }

            System.out.println("[MonumentWfsService] bestMatch vindo do SIPA: " + best.getOriginalName());
            return best;
        }

        // 2) Fallback: dummy rico só para garantir fluxo
        System.out.println("[MonumentWfsService] Nenhum candidato – usar DUMMY fallback");

        return MonumentDto.builder()
                .id("dummy-sipa-123")
                .slug("castelo-de-sao-jorge")
                .originalName(name != null ? name : "Monumento desconhecido")
                .normalizedName(name != null ? name.toLowerCase() : "monumento-desconhecido")
                .locality("Lisboa")
                .district("Lisboa")
                .concelho("Lisboa")
                .freguesia("Santa Maria Maior")
                .lat(lat)
                .lon(lon)
                .shortDescription("Dummy SIPA description apenas para testar o fluxo.")
                .fullDescriptionHtml("<p>Descrição longa dummy do monumento. (Fallback)</p>")
                .heritageCategory("Monumento Nacional")
                .propertyType("Propriedade pública")
                .protectionStatus("Proteção genérica")
                .imageUrls(List.of(
                        "https://example.com/img/castelo1.jpg",
                        "https://example.com/img/castelo2.jpg"
                ))
                .sourceUrl("https://www.example.com/sipa/castelo-s-jorge")
                .extraAttributes(Map.of(
                        "Estado", "Fallback dummy (sem dados reais do SIPA)",
                        "Origem dos dados", "Dummy local no MonumentWfsService"
                ))
                .build();
    }

    /**
     * Stub para o endpoint /api/monumentos (bounding box).
     * Mantemos a assinatura para o MonumentController não rebentar.
     */
    public List<MonumentDto> searchByBbox(double minX, double minY, double maxX, double maxY) {
        System.out.println("[MonumentWfsService] searchByBbox minX=" + minX +
                " minY=" + minY + " maxX=" + maxX + " maxY=" + maxY);

        // Por agora devolvemos lista vazia – depois ligamos isto a um WFS de verdade.
        return List.of();
    }
}