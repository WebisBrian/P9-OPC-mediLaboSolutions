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
 * Preuve de la propriété centrale du sprint : PatientClient RELAIE le JWT de
 * l'utilisateur (lu dans le SecurityContext), il n'en forge jamais.
 * Le header Authorization sortant doit porter exactement le tokenValue du Jwt
 * posé dans le contexte, sans reconstruction ni re-signature.
 */
class PatientClientTest {

    private static final String RELAYED_TOKEN_VALUE = "relayed-jwt-token-value";

    private MockRestServiceServer mockServer;
    private PatientClient patientClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        patientClient = new PatientClient("http://localhost:8081", builder);

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
    void should_relay_bearer_token_when_calling_patient_service() {
        mockServer.expect(requestTo("http://localhost:8081/patients/1"))
                .andExpect(header("Authorization", "Bearer " + RELAYED_TOKEN_VALUE))
                .andRespond(withSuccess("""
                        {"id":1,"firstName":"John","lastName":"Doe","birthDate":"1980-01-01",
                         "gender":"M","address":"10 Main St","phone":"0600000001"}
                        """, MediaType.APPLICATION_JSON));

        patientClient.findById(1L);

        mockServer.verify();
    }
}
