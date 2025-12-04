// src/main/java/pt/dot/application/db/repo/PoiRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.dot.application.db.entity.Poi;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoiRepository extends JpaRepository<Poi, Long> {

    Optional<Poi> findBySipaId(String sipaId);

}