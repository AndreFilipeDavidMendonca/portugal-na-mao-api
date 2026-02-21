package pt.dot.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.SearchItemDto;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.DistrictRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final DistrictRepository districtRepository;
    private final PoiRepository poiRepository;

    public SearchService(DistrictRepository districtRepository,
                         PoiRepository poiRepository) {
        this.districtRepository = districtRepository;
        this.poiRepository = poiRepository;
    }

    public List<SearchItemDto> search(String qRaw, int limit) {
        String q = (qRaw == null ? "" : qRaw.trim());
        if (q.length() < 2) return List.of();

        int safeLimit = Math.max(1, Math.min(limit, 20)); // hard cap

        // split simples: metade distritos, metade POIs
        int limDistricts = Math.max(1, safeLimit / 2);
        int limPois = safeLimit - limDistricts;

        List<District> ds = districtRepository.searchByName(q, limDistricts);
        List<Poi> ps = poiRepository.searchByName(q, limPois);

        List<SearchItemDto> out = new ArrayList<>(safeLimit);

        for (District d : ds) {
            String name = (d.getNamePt() != null && !d.getNamePt().isBlank())
                    ? d.getNamePt()
                    : d.getName();

            out.add(new SearchItemDto("district", d.getId(), name, null));
        }

        for (Poi p : ps) {
            String name = (p.getNamePt() != null && !p.getNamePt().isBlank())
                    ? p.getNamePt()
                    : p.getName();

            Long districtId = (p.getDistrict() != null) ? p.getDistrict().getId() : null;

            out.add(new SearchItemDto("poi", p.getId(), name, districtId));
        }

        return out;
    }
}