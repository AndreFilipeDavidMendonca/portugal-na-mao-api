// src/main/java/pt/dot/application/service/DistrictPoiQueryService.java
package pt.dot.application.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.PoiLiteDto;
import pt.dot.application.api.dto.PoiLiteResponseDto;
import pt.dot.application.db.repo.PoiLiteView;
import pt.dot.application.db.repo.PoiRepository;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@Transactional(readOnly = true)
public class DistrictPoiQueryService {

    private final PoiRepository poiRepository;

    public DistrictPoiQueryService(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    public PoiLiteResponseDto findLiteWithFacets(
            String bboxRaw,
            String category,
            int limit
    ) {

        Bbox b = Bbox.parse(bboxRaw);

        int safeLimit = Math.max(1, Math.min(limit, 5000));

        // -------- FETCH LITE --------
        List<PoiLiteView> views = poiRepository.findLiteByBbox(
                b.minLat,
                b.maxLat,
                b.minLon,
                b.maxLon,
                category,
                PageRequest.of(0, safeLimit)
        );

        List<PoiLiteDto> pois = new ArrayList<>(views.size());

        for (PoiLiteView v : views) {
            pois.add(new PoiLiteDto(
                    v.getId(),
                    null, // districtId opcional para futuro
                    v.getOwnerId(),
                    v.getName(),
                    v.getNamePt(),
                    v.getCategory(),
                    v.getLat(),
                    v.getLon()
            ));
        }

        // -------- FETCH FACETS (sempre sem filtro category) --------
        Map<String, Long> counts = new LinkedHashMap<>();

        for (Object[] row : poiRepository.countByCategoryInBbox(
                b.minLat,
                b.maxLat,
                b.minLon,
                b.maxLon
        )) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }

        return new PoiLiteResponseDto(pois, counts);
    }

    // -------- BBOX parser --------
    private static class Bbox {
        final double minLon, minLat, maxLon, maxLat;

        private Bbox(double minLon, double minLat, double maxLon, double maxLat) {
            this.minLon = minLon;
            this.minLat = minLat;
            this.maxLon = maxLon;
            this.maxLat = maxLat;
        }

        static Bbox parse(String raw) {
            if (raw == null || raw.isBlank())
                throw new ResponseStatusException(BAD_REQUEST, "bbox em falta");

            String[] parts = raw.split(",");
            if (parts.length != 4)
                throw new ResponseStatusException(BAD_REQUEST, "bbox inválido");

            try {
                double minLon = Double.parseDouble(parts[0].trim());
                double minLat = Double.parseDouble(parts[1].trim());
                double maxLon = Double.parseDouble(parts[2].trim());
                double maxLat = Double.parseDouble(parts[3].trim());

                if (minLon > maxLon) { double t = minLon; minLon = maxLon; maxLon = t; }
                if (minLat > maxLat) { double t = minLat; minLat = maxLat; maxLat = t; }

                return new Bbox(minLon, minLat, maxLon, maxLat);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(BAD_REQUEST, "bbox inválido");
            }
        }
    }
}