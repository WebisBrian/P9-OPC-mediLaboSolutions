package com.medilabo.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// SecurityConfig exige SECURITY_USERNAME et SECURITY_PASSWORD via @Value.
// On les fournit ici pour que le contexte @SpringBootTest démarre sans .env.
// JwtIssuer est fail-fast sur la clé privée RS256 : on pointe vers une clé de test
// dédiée (src/test/resources/keys/test_private_key.pem) plutôt que sur la clé
// gitignorée gateway-service/src/main/resources/keys/private_key.pem, absente en CI.
@SpringBootTest
@TestPropertySource(properties = {
        "SECURITY_USERNAME=testuser",
        "SECURITY_PASSWORD=testpassword",
        "jwt.private-key-path=classpath:keys/test_private_key.pem"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
