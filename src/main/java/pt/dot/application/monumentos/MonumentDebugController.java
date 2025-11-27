package pt.dot.application.monumentos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MonumentDebugController {

    private final MonumentService monumentService;

    public MonumentDebugController(MonumentService monumentService) {
        this.monumentService = monumentService;
    }

    @GetMapping("/debug/sipa")
    public List<MonumentDto> debugSipa(@RequestParam String name) {
        System.out.println("[MonumentDebugController] /debug/sipa name=" + name);
        return monumentService.searchByName(name);
    }
}