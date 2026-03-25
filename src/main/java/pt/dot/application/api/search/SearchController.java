package pt.dot.application.api.search;

import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.search.SearchItemDto;
import pt.dot.application.service.search.SearchService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<SearchItemDto> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return searchService.search(q, limit);
    }
}