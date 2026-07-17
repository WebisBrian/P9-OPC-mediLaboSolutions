package com.medilabo.gatewayservice.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Remplace l'injection statique de l'ancien secret partagé (AddRequestHeader) par
 * l'émission dynamique d'un JWT RS256 portant l'identité de l'utilisateur authentifié
 * en Basic à la gateway, injecté en Authorization: Bearer vers le back ciblé.
 *
 * Le SecurityContext réactif (ReactiveSecurityContextHolder) est peuplé par la chaîne
 * Spring Security WebFlux en amont dans le WebFilter chain : ce filtre s'exécute donc
 * après authentification. L'ancien header Authorization: Basic du client est remplacé
 * (et non cumulé) afin de ne jamais faire fuiter les credentials utilisateur vers un back.
 */
@Component
public class JwtRelayGlobalFilter implements GlobalFilter, Ordered {

    private final JwtIssuer jwtIssuer;

    public JwtRelayGlobalFilter(JwtIssuer jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Récupère l'utilisateur authentifié dans le {@code SecurityContext} réactif, fait émettre
     * un JWT à son nom par {@link JwtIssuer}, puis le pose en {@code Authorization: Bearer} sur
     * la requête avant qu'elle ne poursuive vers le back ciblé.
     *
     * @param exchange la requête/réponse en cours, mutée pour porter le nouveau header
     * @param chain    la suite de la chaîne de filtres gateway à invoquer
     * @return un {@link Mono} qui complète une fois la requête (éventuellement mutée) transmise à la chaîne
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getName())
                .map(jwtIssuer::issue)
                .map(token -> withBearerToken(exchange, token))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    /**
     * Construit une copie de l'exchange dont le header {@code Authorization} porte le JWT émis,
     * en remplacement (et non en complément) de tout header {@code Authorization} déjà présent
     * (le {@code Basic} entrant du client, notamment).
     *
     * @param exchange l'exchange d'origine, non modifié
     * @param token    le JWT sérialisé à injecter
     * @return un nouvel {@link ServerWebExchange} portant la requête mutée
     */
    private ServerWebExchange withBearerToken(ServerWebExchange exchange, String token) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    /**
     * Doit s'exécuter avant l'envoi effectif de la requête (NettyRoutingFilter,
     * order = LOWEST_PRECEDENCE) pour que le header Authorization Bearer soit bien
     * celui transmis au back.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}