// src/main/java/pt/dot/application/db/repo/PoiRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.dot.application.db.entity.Poi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    Optional<Poi> findBySipaId(String sipaId);

    List<Poi> findByOwner_Id(UUID ownerId);

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
    // LITE markers (bbox + category opcional)
    // -------------------------
    @Query("""
        select
          p.id as id,
          o.id as ownerId,
          p.name as name,
          p.namePt as namePt,
          p.category as category,
          p.lat as lat,
          p.lon as lon
        from Poi p
        left join p.owner o
        where p.lat between :minLat and :maxLat
          and p.lon between :minLon and :maxLon
          and (:category is null or p.category = :category)
    """)
    List<PoiLiteView> findLiteByBbox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("category") String category,
            Pageable pageable
    );

    // -------------------------
    // FACETS (sempre sem filtro category)
    // -------------------------
    @Query("""
        select p.category, count(p.id)
        from Poi p
        where p.lat between :minLat and :maxLat
          and p.lon between :minLon and :maxLon
        group by p.category
    """)
    List<Object[]> countByCategoryInBbox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon
    );

    @Query("""
    select p from Poi p
    left join fetch p.images
    where p.id = :id
""")
    Optional<Poi> findByIdWithImages(@Param("id") Long id);
}