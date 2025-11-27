// src/main/java/pt/dot/application/db/repo/DistrictRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.District;

import java.util.List;
import java.util.Optional;

public interface DistrictRepository extends JpaRepository<District, Long> {

    List<District> findAllByOrderByNameAsc();

    Optional<District> findByNamePtIgnoreCase(String namePt);

    Optional<District> findByNameIgnoreCase(String name);
}