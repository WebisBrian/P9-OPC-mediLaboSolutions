package com.medilabo.assessmentservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lit le JWT de l'utilisateur authentifié depuis le SecurityContext (synchrone —
 * assessment-service est un back servlet, pas réactif) pour le relayer tel quel
 * vers les appels sortants (PatientClient, NoteClient).
 *
 * assessment ne détient aucune clé privée et ne signe jamais de token : il retransmet
 * la string JWT exacte reçue de la gateway (jwt.getTokenValue()), jamais reconstruite
 * ni re-signée à partir des claims — cela romprait la signature RS256 posée par la gateway.
 */
public final class RelayedJwtProvider {

    private RelayedJwtProvider() {
    }

    /**
     * La garde ci-dessous ne devrait jamais se déclencher en fonctionnement normal :
     * anyRequest().authenticated() sur le resource-server garantit qu'un appel sortant
     * part toujours d'une requête déjà authentifiée par un JWT. Mais un échec explicite
     * vaut mieux qu'un NPE/ClassCastException opaque si la configuration évoluait
     * (endpoint permitAll, tâche @Scheduled hors requête HTTP...).
     */
    public static String currentTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "Aucun JWT présent dans le SecurityContext : appel sortant hors contexte "
                            + "de requête authentifiée");
        }
        return jwt.getTokenValue();
    }
}