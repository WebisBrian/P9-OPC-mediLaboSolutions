package com.medilabo.patientservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Vérifie la provenance des requêtes via le header X-Gateway-Secret.
 *
 * OncePerRequestFilter est retenu plutôt que RequestHeaderAuthenticationFilter parce que
 * ce filtre valide une PROVENANCE de service (secret partagé), pas une IDENTITÉ utilisateur.
 * Il n'y a ni UserDetails, ni SecurityContext, ni authorities : la couche authentification
 * utilisateur reste entièrement à la gateway. Ce filtre est le seul responsable de s'assurer
 * que la requête vient bien de la gateway et non d'un appelant direct.
 *
 * Pas d'annotation @Component : le filtre est instancié directement dans SecurityConfig,
 * ce qui évite l'auto-registration Spring Boot en tant que filtre de servlet autonome
 * (double exécution si à la fois dans la chaîne Security et en bean).
 */
public class GatewaySecretFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Gateway-Secret";

    private final byte[] expectedSecretBytes;

    public GatewaySecretFilter(String expectedSecret) {
        this.expectedSecretBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String received = request.getHeader(HEADER_NAME);
        if (received == null || !isSecretValid(received)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès refusé : provenance non autorisée");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Comparaison en temps constant (MessageDigest.isEqual sur les bytes UTF-8) pour neutraliser
     * les timing attacks : une comparaison String.equals s'arrête au premier caractère différent
     * et laisse fuir des informations sur la valeur attendue via les temps de réponse.
     */
    private boolean isSecretValid(String received) {
        byte[] receivedBytes = received.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(receivedBytes, expectedSecretBytes);
    }
}
