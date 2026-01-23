package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.Favorite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUser_IdAndPoi_Id(UUID userId, Long poiId);

    @EntityGraph(attributePaths = {"poi"})
    Optional<Favorite> findByUser_IdAndPoi_Id(UUID userId, Long poiId);

    @EntityGraph(attributePaths = {"poi"})
    List<Favorite> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    void deleteByUser_IdAndPoi_Id(UUID userId, Long poiId);
}