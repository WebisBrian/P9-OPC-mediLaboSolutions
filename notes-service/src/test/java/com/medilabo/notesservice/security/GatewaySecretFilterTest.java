package com.medilabo.notesservice.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitaire de GatewaySecretFilter — pas de contexte Spring.
 * On vérifie les trois cas de filtrage (header absent, valeur incorrecte, valeur correcte)
 * ainsi que le fail-fast au démarrage propre à notes-service.
 */
class GatewaySecretFilterTest {

    private static final String VALID_SECRET = "test-gateway-secret";

    private GatewaySecretFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewaySecretFilter(VALID_SECRET);
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    void should_Return403_When_GatewaySecretHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notes");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void should_Return403_When_GatewaySecretHeaderHasWrongValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notes");
        request.addHeader(GatewaySecretFilter.HEADER_NAME, "wrong-secret");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void should_PassThrough_When_GatewaySecretHeaderIsCorrect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notes");
        request.addHeader(GatewaySecretFilter.HEADER_NAME, VALID_SECRET);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void should_ThrowIllegalStateException_When_SecretIsNullOrBlank() {
        assertThatThrownBy(() -> new GatewaySecretFilter(null))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new GatewaySecretFilter("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}