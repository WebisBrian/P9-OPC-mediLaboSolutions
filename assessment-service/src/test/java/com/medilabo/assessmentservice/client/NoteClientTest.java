package com.medilabo.assessmentservice.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Preuve de la propriété centrale du sprint : NoteClient RELAIE le JWT de
 * l'utilisateur (lu dans le SecurityContext), il n'en forge jamais.
 * Le header Authorization sortant doit porter exactement le tokenValue du Jwt
 * posé dans le contexte, sans reconstruction ni re-signature.
 */
class NoteClientTest {

    private static final String RELAYED_TOKEN_VALUE = "relayed-jwt-token-value";

    private MockRestServiceServer mockServer;
    private NoteClient noteClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        noteClient = new NoteClient("http://localhost:8083", builder);

        Jwt jwt = Jwt.withTokenValue(RELAYED_TOKEN_VALUE)
                .header("alg", "RS256")
                .claim("sub", "gateway-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_relay_bearer_token_when_calling_notes_service() {
        mockServer.expect(requestTo("http://localhost:8083/notes?patientId=1&page=0&size=50"))
                .andExpect(header("Authorization", "Bearer " + RELAYED_TOKEN_VALUE))
                .andRespond(withSuccess("""
                        {"content":[],"number":0,"totalPages":1,"first":true,"last":true}
                        """, MediaType.APPLICATION_JSON));

        noteClient.findAllByPatientId(1L);

        mockServer.verify();
    }
}