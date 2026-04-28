package pt.dot.application.api.media;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pt.dot.application.db.entity.MediaItem;
import pt.dot.application.service.media.MediaItemService;
import pt.dot.application.service.media.R2MediaStorageService;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final R2MediaStorageService r2MediaStorageService;
    private final MediaItemService mediaItemService;

    public MediaController(
            R2MediaStorageService r2MediaStorageService,
            MediaItemService mediaItemService
    ) {
        this.r2MediaStorageService = r2MediaStorageService;
        this.mediaItemService = mediaItemService;
    }

    @PostMapping("/upload")
    public ResponseEntity<MediaUploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", required = false, defaultValue = "misc") String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "mediaType", required = false, defaultValue = "file") String mediaType
    ) {
        R2MediaStorageService.UploadResult result =
                r2MediaStorageService.upload(file, entityType, entityId, mediaType);

        MediaItem item = mediaItemService.createCloudMedia(
                entityType,
                entityId,
                mediaType,
                result
        );

        return ResponseEntity.ok(new MediaUploadResponseDto(
                item.getStorageKey(),
                item.getStorageKey(),
                result.url(),
                result.contentType(),
                result.sizeBytes()
        ));
    }
}
