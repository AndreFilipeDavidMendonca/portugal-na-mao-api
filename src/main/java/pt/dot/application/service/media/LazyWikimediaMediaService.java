package pt.dot.application.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.entity.Poi;
import pt.dot.application.service.wikimedia.WikimediaMediaService;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LazyWikimediaMediaService {

    private static final String USER_AGENT =
            "PortugalNaMao/1.0 (https://portugal-na-mao.vercel.app; contact: andredmendonca89@gmail.com)";

    private final boolean enabled;
    private final int maxPerEntity;
    private final WikimediaMediaService wikimediaMediaService;
    private final MediaItemService mediaItemService;
    private final R2MediaStorageService r2MediaStorageService;
    private final RestClient restClient;

    public LazyWikimediaMediaService(
            @Value("${ptdot.media.lazy-wikimedia.enabled:false}") boolean enabled,
            @Value("${ptdot.media.lazy-wikimedia.max-per-entity:5}") int maxPerEntity,
            WikimediaMediaService wikimediaMediaService,
            MediaItemService mediaItemService,
            R2MediaStorageService r2MediaStorageService
    ) {
        this.enabled = enabled;
        this.maxPerEntity = Math.max(1, maxPerEntity);
        this.wikimediaMediaService = wikimediaMediaService;
        this.mediaItemService = mediaItemService;
        this.r2MediaStorageService = r2MediaStorageService;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader("Api-User-Agent", USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build();
    }

    /**
     * Opção B:
     * - Se já existir media_item, não faz nada e devolve lista vazia.
     * - Se não existir, procura Wikimedia como antes.
     * - Tenta copiar para R2/media_item.
     * - Se R2 falhar, devolve URLs Wikimedia para o frontend poder mostrar imagem na mesma.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> ensurePoiImages(Poi poi) {
        if (!enabled || poi == null || poi.getId() == null) return List.of();
        if (isBusinessPoi(poi)) return List.of();

        if (mediaItemService.hasMedia(
                MediaItemService.ENTITY_POI,
                poi.getId(),
                MediaItemService.MEDIA_IMAGE
        )) {
            return List.of();
        }

        List<String> wikimediaUrls = wikimediaMediaService.getPoiMedia5(
                poi.getNamePt(),
                poi.getName(),
                poi.getSource(),
                List.of()
        );

        return saveUrlsOrFallback(
                MediaItemService.ENTITY_POI,
                poi.getId(),
                wikimediaUrls
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> ensureDistrictImages(District district) {
        if (!enabled || district == null || district.getId() == null) return List.of();

        if (mediaItemService.hasMedia(
                MediaItemService.ENTITY_DISTRICT,
                district.getId(),
                MediaItemService.MEDIA_IMAGE
        )) {
            return List.of();
        }

        String name = firstNonBlank(district.getNamePt(), district.getName());

        List<String> wikimediaUrls = wikimediaMediaService.getDistrictMedia5(
                name,
                List.of()
        );

        return saveUrlsOrFallback(
                MediaItemService.ENTITY_DISTRICT,
                district.getId(),
                wikimediaUrls
        );
    }

    private List<String> saveUrlsOrFallback(String entityType, Long entityId, List<String> urls) {
        List<String> wikimediaUrls = normalizeUrls(urls);
        if (wikimediaUrls.isEmpty()) return List.of();

        List<String> savedR2Urls = new ArrayList<>();
        int attempted = 0;

        for (String url : wikimediaUrls) {
            if (attempted >= maxPerEntity) break;
            attempted++;

            try {
                DownloadedMedia media = download(url);

                if (media.bytes().length == 0) {
                    continue;
                }

                if (!isImageContentType(media.contentType())) {
                    continue;
                }

                String filename = filenameFromUrl(url);

                R2MediaStorageService.UploadResult uploaded = r2MediaStorageService.uploadBytes(
                        media.bytes(),
                        filename,
                        media.contentType(),
                        entityType,
                        entityId,
                        MediaItemService.MEDIA_IMAGE
                );

                mediaItemService.createWikimediaMedia(
                        entityType,
                        entityId,
                        MediaItemService.MEDIA_IMAGE,
                        uploaded,
                        url,
                        filename
                );

                String uploadedUrl = uploaded.url();

                if (uploadedUrl != null && !uploadedUrl.isBlank()) {
                    savedR2Urls.add(uploadedUrl);
                }
            } catch (Exception ex) {
                System.out.println(
                        "[LAZY-WIKIMEDIA] failed entityType=" + entityType +
                                " entityId=" + entityId +
                                " url=" + url +
                                " -> " + ex.getMessage()
                );
            }
        }

        if (!savedR2Urls.isEmpty()) {
            return savedR2Urls.stream().limit(maxPerEntity).toList();
        }

        return wikimediaUrls.stream().limit(maxPerEntity).toList();
    }

    private DownloadedMedia download(String url) {
        ResponseEntity<byte[]> response = restClient.get()
                .uri(URI.create(url))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header("Api-User-Agent", USER_AGENT)
                .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "pt-PT,pt;q=0.9,en;q=0.8")
                .header(HttpHeaders.REFERER, "https://commons.wikimedia.org/")
                .retrieve()
                .toEntity(byte[].class);

        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();

        String contentType = response.getHeaders().getContentType() == null
                ? contentTypeFromFilename(url)
                : response.getHeaders().getContentType().toString();

        return new DownloadedMedia(body, contentType);
    }

    private List<String> normalizeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();

        Set<String> out = new LinkedHashSet<>();

        for (String url : urls) {
            if (url == null) continue;

            String clean = url.trim();
            if (clean.isBlank()) continue;
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) continue;

            out.add(clean);
        }

        return out.stream().limit(maxPerEntity).toList();
    }

    private boolean isBusinessPoi(Poi poi) {
        return poi.getOwner() != null || "business".equalsIgnoreCase(safe(poi.getSource()));
    }

    private boolean isImageContentType(String contentType) {
        String ct = safe(contentType).toLowerCase(Locale.ROOT);
        return ct.startsWith("image/");
    }

    private String filenameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();

            if (path != null && !path.isBlank()) {
                int idx = path.lastIndexOf('/');
                String name = idx >= 0 ? path.substring(idx + 1) : path;
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);

                if (!name.isBlank()) {
                    return sanitizeFilename(name);
                }
            }
        } catch (Exception ignored) {
        }

        return "wikimedia-image.jpg";
    }

    private String sanitizeFilename(String filename) {
        String clean = filename
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .trim();

        if (clean.length() > 180) {
            clean = clean.substring(0, 180);
        }

        if (!clean.contains(".")) {
            clean += ".jpg";
        }

        return clean;
    }

    private String contentTypeFromFilename(String url) {
        String lower = safe(url).toLowerCase(Locale.ROOT);

        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".svg")) return "image/svg+xml";

        return "image/jpeg";
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isBlank()) return a.trim();
        if (b != null && !b.trim().isBlank()) return b.trim();
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record DownloadedMedia(byte[] bytes, String contentType) {
    }
}