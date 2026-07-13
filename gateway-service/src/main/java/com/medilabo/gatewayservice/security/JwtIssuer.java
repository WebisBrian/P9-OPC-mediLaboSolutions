package com.medilabo.gatewayservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Émet les JWT RS256 injectés par la gateway vers les services back, en remplacement
 * du secret partagé statique (AddRequestHeader X-Gateway-Secret).
 *
 * Claims strictement limités à sub/iss/iat/exp : aucune notion de rôle/autorité,
 * de refresh token, de rotation de clé ni de JWKS n'est introduite ici.
 */
@Component
public class JwtIssuer {

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final RSASSASigner signer;
    private final String issuer;
    private final long expirationSeconds;

    /**
     * Fail-fast : une clé privée absente ou illisible empêcherait la gateway de
     * relayer la moindre requête vers les backs. On refuse donc de démarrer plutôt
     * que de tourner avec une émission JWT de fait inopérante — posture équivalente
     * au fail-fast de l'ancien secret partagé.
     */
    public JwtIssuer(
            @Value("${jwt.private-key-path}") Resource privateKeyResource,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        this.signer = new RSASSASigner(loadPrivateKey(privateKeyResource));
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(String username) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expirationSeconds)))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Échec de signature du JWT", e);
        }
        return signedJwt.serialize();
    }

    private static PrivateKey loadPrivateKey(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace(PEM_HEADER, "")
                    .replace(PEM_FOOTER, "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Clé privée JWT introuvable ou illisible (" + resource
                            + ") : refus de démarrer sans clé RS256", e);
        }
    }
}