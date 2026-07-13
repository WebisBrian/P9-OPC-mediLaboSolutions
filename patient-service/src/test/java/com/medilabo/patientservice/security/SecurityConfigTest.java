package com.medilabo.patientservice.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medilabo.patientservice.controller.PatientController;
import com.medilabo.patientservice.service.PatientService;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Preuve du contrat de sécurité du back : validation du JWT RS256 émis par la gateway
 * (signature, expiration, issuer). Remplace conceptuellement l'ancien GatewaySecretFilterTest.
 *
 * @WebMvcTest SANS exclusion de la Security (contrairement à PatientControllerTest) :
 * la vraie SecurityConfig est importée pour exercer le SecurityFilterChain + JwtDecoder.
 * jwt.public-key-path pointe vers la clé de test dédiée (src/test/resources/keys),
 * jamais vers la clé de prod gitignorée.
 */
@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.public-key-path=classpath:keys/test_public_key.pem",
        "jwt.issuer=medilabo-gateway"
})
class SecurityConfigTest {

    private static final String VALID_ISSUER = "medilabo-gateway";
    private static final String WRONG_ISSUER = "someone-else";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @Test
    void should_return401_when_no_authorization_header() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return200_when_jwt_is_valid() throws Exception {
        when(patientService.findAll()).thenReturn(List.of());

        String token = issueToken(testPrivateKey(), VALID_ISSUER, Instant.now(), Instant.now().plusSeconds(60));

        mockMvc.perform(get("/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void should_return401_when_jwt_signed_with_wrong_key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PrivateKey wrongKey = generator.generateKeyPair().getPrivate();

        String token = issueToken(wrongKey, VALID_ISSUER, Instant.now(), Instant.now().plusSeconds(60));

        mockMvc.perform(get("/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_jwt_has_wrong_issuer() throws Exception {
        String token = issueToken(testPrivateKey(), WRONG_ISSUER, Instant.now(), Instant.now().plusSeconds(60));

        mockMvc.perform(get("/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_jwt_is_expired() throws Exception {
        Instant issuedTwoMinutesAgo = Instant.now().minusSeconds(120);
        String token = issueToken(testPrivateKey(), VALID_ISSUER, issuedTwoMinutesAgo, issuedTwoMinutesAgo.plusSeconds(60));

        mockMvc.perform(get("/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers de forge de JWT de test (Nimbus) ---

    private static PrivateKey testPrivateKey() throws Exception {
        String pem = new String(
                new ClassPathResource("keys/test_private_key.pem").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static String issueToken(PrivateKey privateKey, String issuer, Instant issuedAt, Instant expiresAt)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("testuser")
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJwt.sign(new RSASSASigner(privateKey));
        return signedJwt.serialize();
    }
}