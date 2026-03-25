// src/main/java/pt/dot/application/service/DistrictsCsvImportService.java
package pt.dot.application.service.district;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.db.entity.District;
import pt.dot.application.db.repo.DistrictRepository;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class DistrictsCsvImportService {
    private static final Logger log = LoggerFactory.getLogger(DistrictsCsvImportService.class);

    private final DistrictRepository repo;
    private final ResourceLoader resourceLoader;
    private final String csvPath;

    public DistrictsCsvImportService(
            DistrictRepository repo,
            ResourceLoader resourceLoader,
            @Value("${ptdot.sipa.districts-csv-path:classpath:/sipa/districts.csv}") String csvPath
    ) {
        this.repo = repo;
        this.resourceLoader = resourceLoader;
        this.csvPath = csvPath;
    }

    public record ImportResult(int totalRows, int created, int updated, int invalidRows) {}

    /**
     * Espera CSV com header e delimiter ',' (como o teu exemplo),
     * com quotes "..." e texto rico que pode conter ',' e ';' dentro das aspas.
     *
     * Header esperado:
     * district,inhabited_since,description,history,population,municipalities,parishes,lat,lon,files,sources
     */
    @Transactional
    public ImportResult importFromCsv() {
        log.info("[DistrictsCsvImport] A carregar CSV de '{}'", csvPath);

        Resource res = resourceLoader.getResource(csvPath);
        if (!res.exists()) {
            log.warn("[DistrictsCsvImport] Recurso '{}' não encontrado", csvPath);
            return new ImportResult(0, 0, 0, 0);
        }

        int total = 0, created = 0, updated = 0, invalid = 0;

        // Para codes estáveis e sem colisões neste import
        Set<String> usedCodes = new HashSet<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(',')
                .setQuote('"')
                .setEscape('\\') // opcional, mas ajuda se tiveres \" num texto
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            if (parser.getHeaderMap() == null || !parser.getHeaderMap().containsKey("district")) {
                throw new IllegalStateException("[DistrictsCsvImport] Header inválido: esperado coluna 'district'");
            }

            for (CSVRecord r : parser) {
                total++;

                String namePt = get(r, "district");
                if (isBlank(namePt)) {
                    invalid++;
                    continue;
                }
                namePt = namePt.trim();

                District d = repo.findByNamePtIgnoreCase(namePt).orElseGet(District::new);
                boolean isNew = (d.getId() == null);

                // base
                d.setNamePt(namePt);
                if (isBlank(d.getName())) d.setName(namePt);

                // code: se o teu schema tem varchar(10), isto respeita
                if (isBlank(d.getCode())) {
                    d.setCode(generateCode10(namePt, usedCodes));
                } else {
                    usedCodes.add(d.getCode().toUpperCase(Locale.ROOT));
                }

                // ricos
                setIfPresent(d::setInhabitedSince, get(r, "inhabited_since"));
                setIfPresent(d::setDescription, get(r, "description"));
                setIfPresent(d::setHistory, get(r, "history"));

                d.setPopulation(parseInt(get(r, "population"), d.getPopulation()));
                d.setMunicipalitiesCount(parseInt(get(r, "municipalities"), d.getMunicipalitiesCount()));
                d.setParishesCount(parseInt(get(r, "parishes"), d.getParishesCount()));

                d.setLat(parseDouble(get(r, "lat"), d.getLat()));
                d.setLon(parseDouble(get(r, "lon"), d.getLon()));

                // Nota: ignoramos 'files' e 'sources' aqui
                repo.save(d);

                if (isNew) created++;
                else updated++;
            }

        } catch (Exception e) {
            throw new IllegalStateException("[DistrictsCsvImport] Erro a ler/importar CSV", e);
        }

        log.info("[DistrictsCsvImport] Concluído (rows={}, created={}, updated={}, invalid={})",
                total, created, updated, invalid);

        return new ImportResult(total, created, updated, invalid);
    }

    // -------- helpers --------

    private static String get(CSVRecord r, String col) {
        try {
            return r.isMapped(col) ? r.get(col) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void setIfPresent(java.util.function.Consumer<String> setter, String v) {
        if (!isBlank(v)) setter.accept(v.trim());
    }

    private static Integer parseInt(String s, Integer fallback) {
        if (isBlank(s)) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static Double parseDouble(String s, Double fallback) {
        if (isBlank(s)) return fallback;
        try { return Double.parseDouble(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    /**
     * Gera code <= 10:
     * - sem acentos
     * - letras maiúsculas
     * - base: 3 letras da 1ª palavra
     * - resolve colisões com sufixo numérico
     *
     * Exemplos:
     * "Viana do Castelo" -> VIA
     * "Castelo Branco" -> CAS
     */
    private static String generateCode10(String namePt, Set<String> used) {
        String norm = Normalizer.normalize(namePt, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String firstWord = norm.split("\\s+")[0].replaceAll("[^A-Za-z]", "");
        if (firstWord.isEmpty()) firstWord = "DST";

        String base = firstWord.toUpperCase(Locale.ROOT);
        if (base.length() > 3) base = base.substring(0, 3);

        String candidate = base;
        int i = 2;
        while (used.contains(candidate)) {
            candidate = base + i;
            if (candidate.length() > 10) candidate = candidate.substring(0, 10);
            i++;
        }
        used.add(candidate);
        return candidate;
    }
}