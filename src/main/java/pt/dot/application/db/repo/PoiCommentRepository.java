package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.PoiComment;

import java.util.List;

public interface PoiCommentRepository extends JpaRepository<PoiComment, Long> {
    List<PoiComment> findByPoiIdOrderByCreatedAtDesc(Long poiId);
}