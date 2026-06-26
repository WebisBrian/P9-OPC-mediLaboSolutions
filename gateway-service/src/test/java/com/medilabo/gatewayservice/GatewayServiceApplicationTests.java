package com.medilabo.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// SecurityConfig exige SECURITY_USERNAME et SECURITY_PASSWORD via @Value.
// On les fournit ici pour que le contexte @SpringBootTest démarre sans .env.
@SpringBootTest
@TestPropertySource(properties = {
        "SECURITY_USERNAME=testuser",
        "SECURITY_PASSWORD=testpassword"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
