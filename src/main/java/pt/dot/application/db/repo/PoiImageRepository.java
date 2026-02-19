// src/main/java/pt/dot/application/db/repo/PoiImageRepository.java
package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.PoiImage;

import java.util.List;

public interface PoiImageRepository extends JpaRepository<PoiImage, Long> {

    List<PoiImage> findByPoi_IdOrderByPositionAsc(Long poiId);
}