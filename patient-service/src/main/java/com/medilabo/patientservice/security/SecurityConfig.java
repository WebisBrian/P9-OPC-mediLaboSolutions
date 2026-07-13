package com.medilabo.patientservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Configuration Spring Security côté patient-service.
 *
 * Ce module n'authentifie PAS les utilisateurs (rôle réservé à la gateway).
 * Il valide un JWT RS256 émis par la gateway (resource server standard Spring
 * Security, pas de filtre de validation maison) : signature, expiration et issuer.
 * Aucun user in-memory, aucun BCrypt, aucun rôle/autorité ne sont déclarés ici —
 * le JWT porte l'identité (sub) mais aucune autorisation fine n'est exprimée.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    @Value("${jwt.issuer}")
    private String issuer;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }

    /**
     * Fail-fast : une clé publique absente ou illisible empêcherait toute validation
     * de JWT, donc tout accès légitime au service. On refuse donc de démarrer plutôt
     * que de tourner avec une validation JWT de fait inopérante — posture équivalente
     * au fail-fast de l'ancien secret partagé.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        RSAPublicKey publicKey = loadPublicKey(publicKeyResource);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static RSAPublicKey loadPublicKey(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace(PEM_HEADER, "")
                    .replace(PEM_FOOTER, "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Clé publique JWT introuvable ou illisible (" + resource
                            + ") : refus de démarrer sans clé RS256", e);
        }
    }
}