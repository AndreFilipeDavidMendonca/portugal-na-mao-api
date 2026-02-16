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

    @Query("""
        select d from District d
        where lower(coalesce(d.namePt, d.name)) like lower(concat('%', :q, '%'))
           or lower(d.name) like lower(concat('%', :q, '%'))
           or lower(coalesce(d.namePt, '')) like lower(concat('%', :q, '%'))
        """)
    List<District> searchByName(@Param("q") String q, Pageable pageable);
}