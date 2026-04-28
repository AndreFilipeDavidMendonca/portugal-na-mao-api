package pt.dot.application.service.media;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.MediaItem;
import pt.dot.application.db.repo.MediaItemRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class MediaItemService {

    public static final String ENTITY_POI = "POI";
    public static final String ENTITY_DISTRICT = "DISTRICT";

    public static final String MEDIA_IMAGE = "IMAGE";
    public static final String MEDIA_VIDEO = "VIDEO";

    private final MediaItemRepository mediaItemRepository;
    private final MediaUrlService mediaUrlService;

    public MediaItemService(
            MediaItemRepository mediaItemRepository,
            MediaUrlService mediaUrlService
    ) {
        this.mediaItemRepository = mediaItemRepository;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public List<String> getResolvedUrls(String entityType, Long entityId, String mediaType, int limit) {
        if (entityId == null) return List.of();

        List<String> out = new ArrayList<>();

        for (MediaItem item : mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(entityType, entityId)) {
            if (mediaType != null && !mediaType.equalsIgnoreCase(item.getMediaType())) continue;

            String resolved = mediaUrlService.resolve(item.getStorageKey());
            if (resolved == null || resolved.isBlank()) continue;

            if (!out.contains(resolved)) out.add(resolved);
            if (out.size() >= limit) break;
        }

        return out;
    }

    public void replaceMedia(
            String entityType,
            Long entityId,
            String mediaType,
            List<String> storageKeys,
            String provider
    ) {
        if (entityId == null) return;

        mediaItemRepository.deleteByEntityTypeAndEntityIdAndMediaType(entityType, entityId, mediaType);

        if (storageKeys == null || storageKeys.isEmpty()) return;

        int pos = 0;
        for (String key : storageKeys) {
            if (key == null || key.isBlank()) continue;

            MediaItem item = new MediaItem();
            item.setEntityType(entityType);
            item.setEntityId(entityId);
            item.setMediaType(mediaType);
            item.setProvider(provider);
            item.setStorageKey(key.trim());
            item.setPosition(pos++);

            mediaItemRepository.save(item);
        }
    }
}