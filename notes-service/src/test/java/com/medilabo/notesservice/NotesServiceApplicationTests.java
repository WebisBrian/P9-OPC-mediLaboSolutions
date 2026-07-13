package com.medilabo.notesservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// JwtDecoder est fail-fast sur la clé publique RS256 : on pointe vers une clé de test
// dédiée (src/test/resources/keys/test_public_key.pem) plutôt que sur la clé gitignorée
// notes-service/src/main/resources/keys/public_key.pem, absente en CI.
@SpringBootTest
@TestPropertySource(properties = "jwt.public-key-path=classpath:keys/test_public_key.pem")
class NotesServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
