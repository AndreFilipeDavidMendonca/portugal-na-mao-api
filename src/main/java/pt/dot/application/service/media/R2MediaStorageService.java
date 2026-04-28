package pt.dot.application.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class R2MediaStorageService {

    private static final long MAX_UPLOAD_BYTES = 100L * 1024L * 1024L;

    private final boolean enabled;
    private final String bucket;
    private final String baseUrl;
    private final S3Client s3Client;

    public R2MediaStorageService(
            @Value("${ptdot.media.r2.enabled:false}") boolean enabled,
            @Value("${ptdot.media.r2.endpoint:}") String endpoint,
            @Value("${ptdot.media.r2.access-key-id:}") String accessKeyId,
            @Value("${ptdot.media.r2.secret-access-key:}") String secretAccessKey,
            @Value("${ptdot.media.r2.bucket:}") String bucket,
            @Value("${ptdot.media.r2.region:auto}") String region,
            @Value("${ptdot.media.base-url}") String baseUrl
    ) {
        this.enabled = enabled;
        this.bucket = bucket;
        this.baseUrl = stripTrailingSlash(baseUrl);

        if (enabled) {
            require(endpoint, "ptdot.media.r2.endpoint");
            require(accessKeyId, "ptdot.media.r2.access-key-id");
            require(secretAccessKey, "ptdot.media.r2.secret-access-key");
            require(bucket, "ptdot.media.r2.bucket");

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                    ))
                    .region(Region.of(region == null || region.isBlank() ? "auto" : region))
                    .build();
        } else {
            this.s3Client = null;
        }
    }

    public UploadResult upload(MultipartFile file, String entityType, Long entityId, String mediaType) {
        if (!enabled) {
            throw new IllegalStateException("R2 media upload is disabled");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Ficheiro em falta");
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Ficheiro demasiado grande");
        }

        String contentType = normalizeContentType(file.getContentType());
        String key = buildStorageKey(entityType, entityId, mediaType, file.getOriginalFilename(), contentType);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new IllegalStateException("Erro ao ler ficheiro para upload", ex);
        }

        return new UploadResult(key, baseUrl + "/" + key, contentType, file.getSize());
    }

    private static String buildStorageKey(
            String entityType,
            Long entityId,
            String mediaType,
            String originalFilename,
            String contentType
    ) {
        String entity = safeSegment(entityType, "misc").toLowerCase(Locale.ROOT);
        String type = safeSegment(mediaType, mediaTypeFromContentType(contentType)).toLowerCase(Locale.ROOT);
        String idPart = entityId == null ? "pending" : String.valueOf(entityId);
        String ext = extensionFrom(originalFilename, contentType);

        return entity + "/" + idPart + "/" + type + "s/" + UUID.randomUUID() + ext;
    }

    private static String mediaTypeFromContentType(String contentType) {
        if (contentType == null) return "file";
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        if (contentType.equals("application/pdf")) return "document";
        return "file";
    }

    private static String safeSegment(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        v = v.replaceAll("[^a-zA-Z0-9_-]", "-");
        v = v.replaceAll("-+", "-");
        v = v.replaceAll("^-|-$", "");
        return v.isBlank() ? fallback : v;
    }

    private static String extensionFrom(String filename, String contentType) {
        if (filename != null) {
            String clean = filename.trim();
            int idx = clean.lastIndexOf('.');
            if (idx >= 0 && idx < clean.length() - 1) {
                String ext = clean.substring(idx).toLowerCase(Locale.ROOT).replaceAll("[^.a-z0-9]", "");
                if (ext.length() >= 2 && ext.length() <= 12) return ext;
            }
        }

        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.toLowerCase(Locale.ROOT);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + property);
        }
    }

    public record UploadResult(String storageKey, String url, String contentType, long sizeBytes) {}
}