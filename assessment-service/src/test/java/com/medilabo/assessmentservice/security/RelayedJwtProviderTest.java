package com.medilabo.assessmentservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayedJwtProviderTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return_token_value_when_context_holds_jwt() {
        Jwt jwt = Jwt.withTokenValue("relayed-token")
                .header("alg", "RS256")
                .claim("sub", "gateway-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));

        assertThat(RelayedJwtProvider.currentTokenValue()).isEqualTo("relayed-token");
    }

    @Test
    void should_throw_when_context_has_no_authentication() {
        assertThatThrownBy(RelayedJwtProvider::currentTokenValue)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_when_principal_is_not_a_jwt() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("not-a-jwt", null));

        assertThatThrownBy(RelayedJwtProvider::currentTokenValue)
                .isInstanceOf(IllegalStateException.class);
    }
}
