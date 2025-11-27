// src/main/java/pt/dot/application/service/DistrictMetadataImportService.java
package pt.dot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Service
public class DistrictMetadataImportService {

    private static final Logger log = LoggerFactory.getLogger(DistrictMetadataImportService.class);

    private final DistrictRepository districtRepository;
    private final ObjectMapper objectMapper;

    public DistrictMetadataImportService(DistrictRepository districtRepository,
                                         ObjectMapper objectMapper) {
        this.districtRepository = districtRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void importMetadataFromJson() {
        JsonNode root = loadJson();

        int totalJson = 0;
        int updated = 0;
        int missing = 0;

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            totalJson++;

            String districtNamePt = entry.getKey();   // "Aveiro", "Portalegre", etc.
            JsonNode meta = entry.getValue();

            // Procurar por name_pt (case-insensitive)
            Optional<District> opt = districtRepository.findByNamePtIgnoreCase(districtNamePt);
            if (opt.isEmpty()) {
                log.warn("[DistrictMetadataImport] Não encontrei distrito com name_pt='{}' na BD", districtNamePt);
                missing++;
                continue;
            }

            District d = opt.get();

            // Campos de texto
            if (meta.hasNonNull("description")) {
                d.setDescription(meta.get("description").asText());
            }
            if (meta.hasNonNull("history")) {
                d.setHistory(meta.get("history").asText());
            }
            if (meta.hasNonNull("inhabited_since")) {
                d.setInhabitedSince(meta.get("inhabited_since").asText());
            }

            // Campos numéricos
            if (meta.hasNonNull("population")) {
                d.setPopulation(meta.get("population").asInt());
            }
            if (meta.hasNonNull("municipalities")) {
                d.setMunicipalitiesCount(meta.get("municipalities").asInt());
            }
            if (meta.hasNonNull("parishes")) {
                d.setParishesCount(meta.get("parishes").asInt());
            }

            // ⭐️ Coordenadas (o que estava a faltar)
            if (meta.hasNonNull("lat")) {
                d.setLat(meta.get("lat").asDouble());
            }
            if (meta.hasNonNull("lon")) {
                d.setLon(meta.get("lon").asDouble());
            }

            // se quiseres, pode haver founded_year num futuro:
            // if (meta.hasNonNull("founded_year")) {
            //     d.setFoundedYear(meta.get("founded_year").asInt());
            // }

            log.info("[DistrictMetadataImport] Atualizado distrito id={} name='{}' (lat={}, lon={})",
                    d.getId(), d.getNamePt(), d.getLat(), d.getLon());

            updated++;
        }

        log.info("[DistrictMetadataImport] Import de metadata concluído. Total registos no JSON: {}, atualizados: {}, não encontrados: {}",
                totalJson, updated, missing);
    }

    private JsonNode loadJson() {
        try {
            ClassPathResource resource = new ClassPathResource("data/district-metadata.json");
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readTree(is);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler districts-metadata.json", e);
        }
    }
}