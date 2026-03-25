package pt.dot.application.api.poi;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.poi.CreatePoiCommentRequest;
import pt.dot.application.api.dto.poi.PoiCommentDto;
import pt.dot.application.service.poi.PoiCommentService;

import java.util.List;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class PoiCommentController {

    private final PoiCommentService service;

    public PoiCommentController(PoiCommentService service) {
        this.service = service;
    }

    @GetMapping("/pois/{poiId}/comments")
    public List<PoiCommentDto> list(@PathVariable long poiId) {
        return service.listByPoi(poiId);
    }

    @PostMapping("/pois/{poiId}/comments")
    public PoiCommentDto add(@PathVariable long poiId, @RequestBody CreatePoiCommentRequest req) {
        return service.add(poiId, req);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable long commentId) {
        service.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}