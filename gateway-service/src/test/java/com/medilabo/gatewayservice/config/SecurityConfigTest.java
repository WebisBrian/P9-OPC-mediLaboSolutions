package com.medilabo.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la chaîne de sécurité gateway (HTTP Basic, BCrypt, 1 user in-memory).
 *
 * Choix de la slice @WebFluxTest :
 *   - Charge uniquement la couche web réactive et la sécurité Spring Security.
 *   - N'initialise PAS le routing Spring Cloud Gateway → pas d'appel à patient-service.
 *   - @Import(SecurityConfig.class) est nécessaire car @WebFluxTest ne scanne pas les
 *     @Configuration hors controllers ; on fournit explicitement SecurityWebFilterChain,
 *     PasswordEncoder et MapReactiveUserDetailsService.
 *
 * @TestPropertySource satisfait les @Value de SecurityConfig sans dépendre du .env réel
 * (non disponible en CI). Le même couple user/password est réutilisé dans les tests
 * pour construire les vrais en-têtes HTTP Basic — la chaîne d'encodage BCrypt est ainsi
 * exercée de bout en bout.
 *
 * spring-security-test n'est PAS ajouté : les vrais headers Basic via WebTestClient
 * suffisent ; les mutators (@WithMockUser, mockUser()) seraient contre-productifs ici.
 */
@WebFluxTest
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "SECURITY_USERNAME=testuser",
        "SECURITY_PASSWORD=testpassword"
})
class SecurityConfigTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testpassword";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_return_401_when_no_credentials() {
        webTestClient.get()
                .uri("/patients")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void should_return_401_when_wrong_password() {
        webTestClient.get()
                .uri("/patients")
                .headers(h -> h.setBasicAuth(TEST_USER, "wrongpassword"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * En slice @WebFluxTest, le routing Gateway n'est pas chargé : la requête
     * authentifiée n'est pas routée vers patient-service et aboutit à 404 (aucun handler).
     * L'assertion porte uniquement sur le fait que l'auth laisse passer (statut ≠ 401).
     */
    @Test
    void should_not_return_401_when_valid_credentials() {
        webTestClient.get()
                .uri("/patients")
                .headers(h -> h.setBasicAuth(TEST_USER, TEST_PASSWORD))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }
}