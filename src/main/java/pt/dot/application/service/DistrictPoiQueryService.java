// src/main/java/pt/dot/application/service/DistrictPoiQueryService.java
package pt.dot.application.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.PoiLiteDto;
import pt.dot.application.db.repo.PoiRepository;
import pt.dot.application.db.repo.PoiLiteView;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@Transactional(readOnly = true)
public class DistrictPoiQueryService {

    private final PoiRepository poiRepository;

    public DistrictPoiQueryService(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    public List<PoiLiteDto> findLite(Long districtId, String bboxRaw, int limit) {
        if (districtId == null) throw new ResponseStatusException(BAD_REQUEST, "districtId em falta");

        Bbox b = Bbox.parse(bboxRaw);

        int safeLimit = Math.max(1, Math.min(limit, 5000)); // ajusta conforme performance
        var views = poiRepository.findLiteByDistrictAndBbox(
                districtId,
                b.minLat, b.maxLat,
                b.minLon, b.maxLon,
                PageRequest.of(0, safeLimit)
        );

        List<PoiLiteDto> out = new ArrayList<>(views.size());
        for (PoiLiteView v : views) {
            out.add(new PoiLiteDto(
                    v.getId(),
                    v.getDistrictId(),
                    v.getOwnerId(),
                    v.getName(),
                    v.getNamePt(),
                    v.getCategory(),
                    v.getLat(),
                    v.getLon()
            ));
        }
        return out;
    }

    public Map<String, Long> countByCategory(Long districtId, String bboxRaw) {
        if (districtId == null) throw new ResponseStatusException(BAD_REQUEST, "districtId em falta");
        Bbox b = Bbox.parse(bboxRaw);

        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : poiRepository.countByCategoryInDistrictAndBbox(
                districtId,
                b.minLat, b.maxLat,
                b.minLon, b.maxLon
        )) {
            String category = (String) row[0];
            Long cnt = ((Number) row[1]).longValue();
            out.put(category, cnt);
        }
        return out;
    }

    private static class Bbox {
        final double minLon, minLat, maxLon, maxLat;

        private Bbox(double minLon, double minLat, double maxLon, double maxLat) {
            this.minLon = minLon;
            this.minLat = minLat;
            this.maxLon = maxLon;
            this.maxLat = maxLat;
        }

        static Bbox parse(String raw) {
            // formato: "minLon,minLat,maxLon,maxLat"
            if (raw == null || raw.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "bbox em falta (minLon,minLat,maxLon,maxLat)");
            }

            String[] parts = raw.split(",");
            if (parts.length != 4) {
                throw new ResponseStatusException(BAD_REQUEST, "bbox inválido, esperado 4 valores");
            }

            try {
                double minLon = Double.parseDouble(parts[0].trim());
                double minLat = Double.parseDouble(parts[1].trim());
                double maxLon = Double.parseDouble(parts[2].trim());
                double maxLat = Double.parseDouble(parts[3].trim());

                if (minLon > maxLon) { double t = minLon; minLon = maxLon; maxLon = t; }
                if (minLat > maxLat) { double t = minLat; minLat = maxLat; maxLat = t; }

                return new Bbox(minLon, minLat, maxLon, maxLat);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(BAD_REQUEST, "bbox inválido: números inválidos");
            }
        }
    }
}