package com.medilabo.notesservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// JwtDecoder est fail-fast sur la clé publique RS256 : on pointe vers une clé de test
// dédiée (src/test/resources/keys/test_public_key.pem) plutôt que sur la clé gitignorée
// notes-service/src/main/resources/keys/public_key.pem, absente en CI.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "jwt.public-key-path=classpath:keys/test_public_key.pem")
class NotesServiceApplicationTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDb = new MongoDBContainer("mongo:7");

    @Test
    void contextLoads() {
    }

}
