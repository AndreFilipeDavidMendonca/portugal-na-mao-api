package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.MediaItem;

import java.util.List;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    List<MediaItem> findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(String entityType, Long entityId);

    boolean existsByEntityTypeAndEntityIdAndMediaType(String entityType, Long entityId, String mediaType);

    void deleteByEntityTypeAndEntityIdAndMediaType(String entityType, Long entityId, String mediaType);
}
