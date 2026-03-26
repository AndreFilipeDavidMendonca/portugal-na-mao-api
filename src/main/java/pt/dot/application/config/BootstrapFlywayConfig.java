package pt.dot.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

@Configuration
public class BootstrapFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapFlywayConfig.class);

    @Bean
    FlywayMigrationStrategy bootstrapAwareFlywayMigrationStrategy(
            DataSource dataSource,
            ResourceLoader resourceLoader,
            @Value("${ptdot.bootstrap.reconcile-schema:false}") boolean reconcileSchema,
            @Value("${ptdot.bootstrap.schema-script-path:classpath:/db/bootstrap/schema_pt_dot.sql}") String schemaScriptPath
    ) {
        return flyway -> {
            if (reconcileSchema) {
                applyTrackedScript(dataSource, resourceLoader, schemaScriptPath, "bootstrap-schema");
            } else {
                log.info("[BootstrapSchema] Skip (ptdot.bootstrap.reconcile-schema=false)");
            }

            flyway.migrate();
        };
    }

    public static void applyTrackedScript(
            DataSource dataSource,
            ResourceLoader resourceLoader,
            String scriptPath,
            String scriptName
    ) {
        try {
            Resource resource = resourceLoader.getResource(scriptPath);
            if (!resource.exists()) {
                throw new IllegalStateException("Bootstrap SQL não encontrado: " + scriptPath);
            }

            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            String checksum = sha256(sql);

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            ensureBootstrapStateTable(jdbc);

            String stored = jdbc.query(
                    "select checksum from app_bootstrap_state where script_name = ?",
                    ps -> ps.setString(1, scriptName),
                    rs -> rs.next() ? rs.getString(1) : null
            );

            if (checksum.equals(stored)) {
                log.info("[BootstrapSchema] Script '{}' sem alterações. Skip.", scriptName);
                return;
            }

            log.info("[BootstrapSchema] A aplicar script '{}' de '{}'", scriptName, scriptPath);

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, StandardCharsets.UTF_8.name(), resource);
            populator.execute(dataSource);

            jdbc.update(
                    """
                    insert into app_bootstrap_state(script_name, checksum, applied_at)
                    values (?, ?, ?)
                    on conflict (script_name)
                    do update set checksum = excluded.checksum, applied_at = excluded.applied_at
                    """,
                    scriptName,
                    checksum,
                    Timestamp.from(Instant.now())
            );

            log.info("[BootstrapSchema] Script '{}' aplicado com sucesso.", scriptName);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao aplicar bootstrap schema script", e);
        }
    }

    private static void ensureBootstrapStateTable(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                create table if not exists app_bootstrap_state (
                    script_name varchar(120) primary key,
                    checksum varchar(128) not null,
                    applied_at timestamptz not null default now()
                )
                """
        );
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
