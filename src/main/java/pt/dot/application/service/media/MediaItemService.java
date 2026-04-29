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
    private final R2MediaStorageService r2MediaStorageService;

    public MediaItemService(
            MediaItemRepository mediaItemRepository,
            MediaUrlService mediaUrlService,
            R2MediaStorageService r2MediaStorageService
    ) {
        this.mediaItemRepository = mediaItemRepository;
        this.mediaUrlService = mediaUrlService;
        this.r2MediaStorageService = r2MediaStorageService;
    }

    @Transactional(readOnly = true)
    public List<String> getResolvedUrls(String entityType, Long entityId, String mediaType, int limit) {
        if (entityId == null) return List.of();

        String normalizedEntityType = normalizeUpper(entityType, null);
        String normalizedMediaType = normalizeUpper(mediaType, null);
        if (normalizedEntityType == null) return List.of();

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
        if (normalizedEntityType == null) return List.of();

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

    @Transactional(readOnly = true)
    public boolean hasMedia(String entityType, Long entityId, String mediaType) {
        if (entityId == null) return false;

        String normalizedEntityType = normalizeUpper(entityType, null);
        String normalizedMediaType = normalizeUpper(mediaType, null);
        if (normalizedEntityType == null || normalizedMediaType == null) return false;

        return mediaItemRepository.existsByEntityTypeAndEntityIdAndMediaType(
                normalizedEntityType,
                entityId,
                normalizedMediaType
        );
    }

    public MediaItem createWikimediaMedia(
            String entityType,
            Long entityId,
            String mediaType,
            R2MediaStorageService.UploadResult upload,
            String sourceUrl,
            String title
    ) {
        if (entityId == null) return null;
        if (upload == null || upload.storageKey() == null || upload.storageKey().isBlank()) return null;

        String normalizedEntityType = normalizeUpper(entityType, "MISC");
        String normalizedMediaType = normalizeUpper(mediaType, MEDIA_IMAGE);

        MediaItem item = new MediaItem();
        item.setEntityType(normalizedEntityType);
        item.setEntityId(entityId);
        item.setMediaType(normalizedMediaType);
        item.setProvider(PROVIDER_WIKIMEDIA);
        item.setExternalId(sourceUrl);
        item.setTitle(title);
        item.setStorageKey(upload.storageKey().trim());
        item.setThumbUrl(sourceUrl);
        item.setMimeType(upload.contentType());
        item.setPosition(nextPosition(normalizedEntityType, entityId));

        return mediaItemRepository.save(item);
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

    /**
     * Diff-based replace:
     * - mantém itens que continuam na lista
     * - apaga da BD + R2 apenas os itens removidos
     * - adiciona apenas os novos
     * - atualiza posições conforme ordem recebida do FE
     */
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

        List<String> nextKeys = normalizeKeys(storageKeys);

        List<MediaItem> existing = mediaItemRepository
                .findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(normalizedEntityType, entityId)
                .stream()
                .filter(item -> normalizedMediaType.equalsIgnoreCase(item.getMediaType()))
                .toList();

        List<MediaItem> toDelete = existing.stream()
                .filter(item -> !nextKeys.contains(normalizeStorageKey(item.getStorageKey())))
                .toList();

        deleteItemsAndStorage(toDelete);

        int pos = 0;

        for (String key : nextKeys) {
            MediaItem existingItem = findByStorageKey(existing, key);

            if (existingItem != null) {
                existingItem.setPosition(pos++);
                mediaItemRepository.save(existingItem);
                continue;
            }

            MediaItem item = new MediaItem();
            item.setEntityType(normalizedEntityType);
            item.setEntityId(entityId);
            item.setMediaType(normalizedMediaType);
            item.setProvider(normalizedProvider);
            item.setStorageKey(key);
            item.setPosition(pos++);

            mediaItemRepository.save(item);
        }
    }

    public void deleteMediaAndStorage(String entityType, Long entityId) {
        if (entityId == null) return;

        String normalizedEntityType = normalizeUpper(entityType, null);
        if (normalizedEntityType == null) return;

        List<MediaItem> items = mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(
                normalizedEntityType,
                entityId
        );

        deleteItemsAndStorage(items);
    }

    public void deleteMediaAndStorage(String entityType, Long entityId, String mediaType) {
        if (entityId == null) return;

        String normalizedEntityType = normalizeUpper(entityType, null);
        String normalizedMediaType = normalizeUpper(mediaType, null);
        if (normalizedEntityType == null) return;

        List<MediaItem> items = mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(
                normalizedEntityType,
                entityId
        );

        if (normalizedMediaType != null) {
            items = items.stream()
                    .filter(item -> normalizedMediaType.equalsIgnoreCase(item.getMediaType()))
                    .toList();
        }

        deleteItemsAndStorage(items);
    }

    private void deleteItemsAndStorage(List<MediaItem> items) {
        if (items == null || items.isEmpty()) return;

        for (MediaItem item : items) {
            deleteStorageObjectIfPresent(item);
        }

        mediaItemRepository.deleteAll(items);
    }

    private void deleteStorageObjectIfPresent(MediaItem item) {
        if (item == null) return;

        String storageKey = safe(item.getStorageKey());
        if (storageKey.isBlank()) return;

        try {
            r2MediaStorageService.deleteObject(storageKey);
        } catch (Exception ex) {
            System.out.println("[MEDIA] failed deleting R2 object storageKey=" +
                    storageKey + " -> " + ex.getMessage());
        }
    }

    private MediaItem findByStorageKey(List<MediaItem> items, String storageKey) {
        if (items == null || items.isEmpty()) return null;

        String normalized = normalizeStorageKey(storageKey);

        for (MediaItem item : items) {
            if (normalized.equals(normalizeStorageKey(item.getStorageKey()))) {
                return item;
            }
        }

        return null;
    }

    private int nextPosition(String entityType, Long entityId) {
        return mediaItemRepository.findByEntityTypeAndEntityIdOrderByPositionAscIdAsc(entityType, entityId).stream()
                .map(MediaItem::getPosition)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .map(v -> v + 1)
                .orElse(0);
    }

    private static List<String> normalizeKeys(List<String> input) {
        if (input == null || input.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();

        for (String value : input) {
            String key = normalizeStorageKey(value);
            if (key.isBlank()) continue;

            if (!out.contains(key)) {
                out.add(key);
            }
        }

        return out;
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStorageKey(String value) {
        String v = safe(value);
        if (v.isBlank()) return "";

        int hashIdx = v.indexOf('#');
        if (hashIdx >= 0) {
            v = v.substring(0, hashIdx);
        }

        String marker = ".r2.dev/";
        int idx = v.indexOf(marker);
        if (idx >= 0) {
            return v.substring(idx + marker.length()).trim();
        }

        return v.trim();
    }
}