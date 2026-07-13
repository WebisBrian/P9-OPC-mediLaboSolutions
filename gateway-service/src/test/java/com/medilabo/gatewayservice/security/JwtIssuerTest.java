package com.medilabo.gatewayservice.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste le contrat de JwtIssuer (claims émis, signature RS256 vérifiable, fail-fast),
 * pas la cryptographie de Nimbus elle-même ni le parsing PKCS#8 (déjà exercé par
 * l'émission réussie du token).
 */
class JwtIssuerTest {

    private static final String ISSUER = "medilabo-gateway-test";
    private static final long EXPIRATION_SECONDS = 60L;

    private JwtIssuer jwtIssuer;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        Resource privateKeyResource = new ClassPathResource("keys/test_private_key.pem");
        jwtIssuer = new JwtIssuer(privateKeyResource, ISSUER, EXPIRATION_SECONDS);
        publicKey = readPublicKey(new ClassPathResource("keys/test_public_key.pem"));
    }

    @Test
    void should_issue_signed_token_when_verified_with_matching_public_key() throws Exception {
        String token = jwtIssuer.issue("alice");

        SignedJWT signedJwt = SignedJWT.parse(token);

        assertThat(signedJwt.verify(new RSASSAVerifier(publicKey))).isTrue();
    }

    @Test
    void should_include_expected_claims_when_token_issued() throws Exception {
        String token = jwtIssuer.issue("alice");

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getExpirationTime()).isNotNull();
    }

    @Test
    void should_expire_after_configured_duration_when_token_issued() throws Exception {
        String token = jwtIssuer.issue("alice");

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plusSeconds(EXPIRATION_SECONDS));
    }

    @Test
    void should_reject_signature_when_verified_with_wrong_public_key() throws Exception {
        String token = jwtIssuer.issue("alice");
        SignedJWT signedJwt = SignedJWT.parse(token);

        RSAPublicKey wrongPublicKey = (RSAPublicKey) KeyPairGenerator.getInstance("RSA")
                .generateKeyPair()
                .getPublic();

        assertThat(signedJwt.verify(new RSASSAVerifier(wrongPublicKey))).isFalse();
    }

    @Test
    void should_fail_fast_when_private_key_resource_missing() {
        Resource missing = new ClassPathResource("keys/does-not-exist.pem");

        assertThatThrownBy(() -> new JwtIssuer(missing, ISSUER, EXPIRATION_SECONDS))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RSAPublicKey readPublicKey(Resource resource) throws Exception {
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
