package pt.dot.application.service.media;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.MediaItem;
import pt.dot.application.db.repo.MediaItemRepository;
import pt.dot.application.exception.Errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class MediaItemService {

    public static final String ENTITY_POI = "POI";
    public static final String ENTITY_DISTRICT = "DISTRICT";

    public static final String MEDIA_IMAGE = "IMAGE";
    public static final String MEDIA_VIDEO = "VIDEO";
    public static final String MEDIA_FILE = "FILE";

    public static final String PROVIDER_MANUAL = "manual";
    public static final String PROVIDER_CLOUD = "cloud";
    public static final String PROVIDER_WIKIMEDIA = "wikimedia";
    public static final String PROVIDER_CSV = "csv-import";

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

        String normalizedEntityType = normalizeUpper(entityType, null);
        String normalizedMediaType = normalizeUpper(mediaType, null);
        int max = limit <= 0 ? Integer.MAX_VALUE : limit;

        List<String> out = new ArrayList<>();

        for (MediaItem item : mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(normalizedEntityType, entityId)) {
            if (normalizedMediaType != null && !normalizedMediaType.equalsIgnoreCase(item.getMediaType())) continue;

            String resolved = mediaUrlService.resolve(item.getStorageKey());
            if (resolved == null || resolved.isBlank()) continue;

            if (!out.contains(resolved)) out.add(resolved);
            if (out.size() >= max) break;
        }

        return out;
    }

    @Transactional(readOnly = true)
    public List<String> getStorageKeys(String entityType, Long entityId, String mediaType, int limit) {
        if (entityId == null) return List.of();

        String normalizedEntityType = normalizeUpper(entityType, null);
        String normalizedMediaType = normalizeUpper(mediaType, null);
        int max = limit <= 0 ? Integer.MAX_VALUE : limit;

        List<String> out = new ArrayList<>();

        for (MediaItem item : mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(normalizedEntityType, entityId)) {
            if (normalizedMediaType != null && !normalizedMediaType.equalsIgnoreCase(item.getMediaType())) continue;

            String key = item.getStorageKey();
            if (key == null || key.isBlank()) continue;

            if (!out.contains(key)) out.add(key);
            if (out.size() >= max) break;
        }

        return out;
    }

    public MediaItem createCloudMedia(
            String entityType,
            Long entityId,
            String mediaType,
            R2MediaStorageService.UploadResult upload
    ) {
        if (entityId == null) {
            throw Errors.badRequest("MEDIA_ENTITY_ID_REQUIRED", "entityId é obrigatório para associar o ficheiro à entidade.");
        }
        if (upload == null || upload.storageKey() == null || upload.storageKey().isBlank()) {
            throw Errors.badRequest("MEDIA_UPLOAD_EMPTY", "Upload sem storageKey válido.");
        }

        String normalizedEntityType = normalizeUpper(entityType, "MISC");
        String normalizedMediaType = normalizeUpper(mediaType, MEDIA_FILE);

        MediaItem item = new MediaItem();
        item.setEntityType(normalizedEntityType);
        item.setEntityId(entityId);
        item.setMediaType(normalizedMediaType);
        item.setProvider(PROVIDER_CLOUD);
        item.setStorageKey(upload.storageKey().trim());
        item.setMimeType(upload.contentType());
        item.setPosition(nextPosition(normalizedEntityType, entityId));

        return mediaItemRepository.save(item);
    }

    public void replaceMedia(
            String entityType,
            Long entityId,
            String mediaType,
            List<String> storageKeys,
            String provider
    ) {
        if (entityId == null) return;

        String normalizedEntityType = normalizeUpper(entityType, "MISC");
        String normalizedMediaType = normalizeUpper(mediaType, MEDIA_FILE);
        String normalizedProvider = normalizeLower(provider, PROVIDER_MANUAL);

        mediaItemRepository.deleteByEntityTypeAndEntityIdAndMediaType(normalizedEntityType, entityId, normalizedMediaType);

        if (storageKeys == null || storageKeys.isEmpty()) return;

        int pos = 0;
        for (String key : storageKeys) {
            if (key == null || key.isBlank()) continue;

            MediaItem item = new MediaItem();
            item.setEntityType(normalizedEntityType);
            item.setEntityId(entityId);
            item.setMediaType(normalizedMediaType);
            item.setProvider(normalizedProvider);
            item.setStorageKey(key.trim());
            item.setPosition(pos++);

            mediaItemRepository.save(item);
        }
    }

    private int nextPosition(String entityType, Long entityId) {
        return mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(entityType, entityId).stream()
                .map(MediaItem::getPosition)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .map(v -> v + 1)
                .orElse(0);
    }

    private static String normalizeUpper(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        if (v == null || v.isBlank()) return null;
        return v.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLower(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        if (v == null || v.isBlank()) return null;
        return v.trim().toLowerCase(Locale.ROOT);
    }
}
