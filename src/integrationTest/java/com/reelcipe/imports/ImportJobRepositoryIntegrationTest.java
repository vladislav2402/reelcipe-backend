package com.reelcipe.imports;

import com.reelcipe.auth.FixtureUserInitializer;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class ImportJobRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired
    ImportJobRepository imports;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void repositoryPersistsImportAndEnforcesUserClientLookup() {
        UUID clientRequestId = UuidV7.randomUuid();
        ImportJob job = new ImportJob(
                UuidV7.randomUuid(),
                FixtureUserInitializer.ALICE_ID,
                clientRequestId,
                ImportSourceType.LINK,
                "https://example.com/video",
                "hash-v1",
                ImportStatus.QUEUED,
                ImportStage.RESOLVING,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400),
                Instant.now());
        imports.save(job);

        assertThat(imports.findByUserIdAndClientRequestId(FixtureUserInitializer.ALICE_ID, clientRequestId))
                .map(ImportJob::getId)
                .contains(job.getId());
        assertThat(imports.findByIdAndUserId(job.getId(), FixtureUserInitializer.BOB_ID)).isEmpty();
    }
}
