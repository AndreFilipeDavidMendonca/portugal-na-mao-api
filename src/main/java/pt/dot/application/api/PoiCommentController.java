package pt.dot.application.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.CreatePoiCommentRequest;
import pt.dot.application.api.dto.PoiCommentDto;
import pt.dot.application.service.PoiCommentService;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174"
        },
        allowCredentials = "true"
)
public class PoiCommentController {

    private final PoiCommentService service;

    public PoiCommentController(PoiCommentService service) {
        this.service = service;
    }

    // GET /api/pois/{poiId}/comments
    @GetMapping("/pois/{poiId}/comments")
    public List<PoiCommentDto> list(@PathVariable long poiId, HttpSession session) {
        return service.listByPoi(poiId, session);
    }

    // POST /api/pois/{poiId}/comments
    @PostMapping("/pois/{poiId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public PoiCommentDto add(@PathVariable long poiId, @RequestBody CreatePoiCommentRequest req, HttpSession session) {
        return service.add(poiId, req, session);
    }

    // DELETE /api/comments/{commentId}
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long commentId, HttpSession session) {
        service.delete(commentId, session);
    }
}