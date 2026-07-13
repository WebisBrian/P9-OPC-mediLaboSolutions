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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getName())
                .map(jwtIssuer::issue)
                .map(token -> withBearerToken(exchange, token))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

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