package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.dot.application.db.entity.Poi;

import java.util.List;
import java.util.Optional;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    @Query("""
        SELECT p FROM Poi p
        WHERE (:districtId IS NULL OR p.district.id = :districtId)
          AND (:category IS NULL OR p.category = :category)
    """)
    List<Poi> findPois(Integer districtId, String category);

    List<Poi> findByDistrict_Id(Long districtId);

    List<Poi> findByDistrict_IdOrderByNameAsc(Long districtId);

    List<Poi> findByNameIgnoreCase(String name);

    Optional<Poi> findByExternalOsmId(String externalOsmId);

    Optional<Poi> findByDistrict_IdAndExternalOsmId(Long districtId, String externalOsmId);
}