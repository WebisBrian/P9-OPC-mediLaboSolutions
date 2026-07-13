package com.medilabo.patientservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// JwtDecoder est fail-fast sur la clé publique RS256 : on pointe vers une clé de test
// dédiée (src/test/resources/keys/test_public_key.pem) plutôt que sur la clé gitignorée
// patient-service/src/main/resources/keys/public_key.pem, absente en CI.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "jwt.public-key-path=classpath:keys/test_public_key.pem")
class PatientServiceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void contextLoads() {
    }

}
