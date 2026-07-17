package com.medilabo.gatewayservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste le remplacement du header Authorization par JwtRelayGlobalFilter. JwtIssuer est
 * mocké (sa cryptographie est déjà couverte par JwtIssuerTest) : ce test porte uniquement
 * sur la responsabilité propre du filtre (lecture du SecurityContext réactif, mutation de
 * l'exchange, propagation à la chaîne), sans charger de contexte Spring ni de routing.
 */
class JwtRelayGlobalFilterTest {

    private final JwtIssuer jwtIssuer = mock(JwtIssuer.class);
    private final JwtRelayGlobalFilter filter = new JwtRelayGlobalFilter(jwtIssuer);

    @Test
    void should_replace_basic_header_with_bearer_token_when_user_authenticated() {
        when(jwtIssuer.issue("alice")).thenReturn("signed-jwt");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        AtomicReference<ServerWebExchange> exchangeSeenByChain = new AtomicReference<>();
        GatewayFilterChain chain = capturingChain(exchangeSeenByChain);

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(authenticatedAs("alice")))
                .verifyComplete();

        HttpHeaders headersSentDownstream = exchangeSeenByChain.get().getRequest().getHeaders();
        assertThat(headersSentDownstream.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer signed-jwt");
    }

    @Test
    void should_forward_exchange_unchanged_when_no_authentication_in_context() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/patients"));

        AtomicReference<ServerWebExchange> exchangeSeenByChain = new AtomicReference<>();
        GatewayFilterChain chain = capturingChain(exchangeSeenByChain);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchangeSeenByChain.get()).isSameAs(exchange);
    }

    @Test
    void should_run_just_before_routing_filter_when_order_checked() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 1);
    }

    private static Context authenticatedAs(String username) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, null, List.of());
        return ReactiveSecurityContextHolder.withAuthentication(authentication);
    }

    private static GatewayFilterChain capturingChain(AtomicReference<ServerWebExchange> sink) {
        return ex -> {
            sink.set(ex);
            return Mono.empty();
        };
    }
}
