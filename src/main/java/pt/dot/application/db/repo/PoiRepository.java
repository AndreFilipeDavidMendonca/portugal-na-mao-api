// src/main/java/pt/dot/application/db/repo/PoiRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.Poi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    // ✅ Comerciais: “os meus”
    List<Poi> findByOwner_Id(UUID ownerId);

    // ✅ Não-comerciais: SIPA
    Optional<Poi> findBySipaId(String sipaId);

    // (opcional, mas recomendo)
    Optional<Poi> findByExternalOsmId(String externalOsmId);
}