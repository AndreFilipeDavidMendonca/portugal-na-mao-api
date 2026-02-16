// src/main/java/pt/dot/application/db/repo/PoiRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.db.repo.PoiLiteView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    // -------------------------
    // Usados por PoiService / CsvSync
    // -------------------------
    Optional<Poi> findBySipaId(String sipaId);

    List<Poi> findByOwner_Id(UUID ownerId);

    // -------------------------
    // Usado por SearchService
    // -------------------------
    @Query("""
        select p from Poi p
        where lower(coalesce(p.namePt, p.name)) like lower(concat('%', :q, '%'))
           or lower(p.name) like lower(concat('%', :q, '%'))
        order by
          case when lower(coalesce(p.namePt, p.name)) like lower(concat(:q, '%')) then 0 else 1 end,
          coalesce(p.namePt, p.name) asc
        """)
    List<Poi> searchByName(@Param("q") String q, Pageable pageable);

    // -------------------------
    // NOVO: district + bbox (lite markers)
    // -------------------------
    @Query("""
        select
          p.id as id,
          d.id as districtId,
          o.id as ownerId,
          p.name as name,
          p.namePt as namePt,
          p.category as category,
          p.lat as lat,
          p.lon as lon
        from Poi p
        left join p.district d
        left join p.owner o
        where d.id = :districtId
          and p.lat between :minLat and :maxLat
          and p.lon between :minLon and :maxLon
        """)
    List<PoiLiteView> findLiteByDistrictAndBbox(
            @Param("districtId") Long districtId,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            Pageable pageable
    );

    // -------------------------
    // NOVO: facets/counts por category
    // -------------------------
    @Query("""
        select p.category as category, count(p.id) as cnt
        from Poi p
        join p.district d
        where d.id = :districtId
          and p.lat between :minLat and :maxLat
          and p.lon between :minLon and :maxLon
        group by p.category
        """)
    List<Object[]> countByCategoryInDistrictAndBbox(
            @Param("districtId") Long districtId,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon
    );
}