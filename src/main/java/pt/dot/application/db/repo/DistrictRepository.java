// src/main/java/pt/dot/application/db/repo/DistrictRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.dot.application.db.entity.District;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface DistrictRepository extends JpaRepository<District, Long> {

    List<District> findAllByOrderByNameAsc();

    Optional<District> findByNamePtIgnoreCase(String namePt);

    @Query(value = """
  select *
  from district d
  where unaccent(lower(coalesce(d.name_pt, d.name))) like concat('%%', unaccent(lower(:q)), '%%')
     or unaccent(lower(coalesce(d.name, ''))) like concat('%%', unaccent(lower(:q)), '%%')
  order by coalesce(d.name_pt, d.name) asc
  limit :limit
""", nativeQuery = true)
    List<District> searchByName(@Param("q") String q, @Param("limit") int limit);
}