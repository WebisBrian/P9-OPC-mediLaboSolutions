package com.medilabo.notesservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration Spring Security côté notes-service.
 *
 * Ce module n'authentifie PAS les utilisateurs (rôle réservé à la gateway).
 * Il vérifie uniquement la provenance des requêtes via GatewaySecretFilter.
 * Aucun user in-memory, aucun BCrypt, aucun login form ne sont déclarés ici.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        /*
         * anyRequest().permitAll() : c'est GatewaySecretFilter qui décide de bloquer (403)
         * ou de laisser passer. Spring Security n'exprime aucune règle d'autorisation
         * utilisateur ici (pas de principal, pas de rôle). Déclarer .authenticated() serait
         * incorrect car aucun utilisateur n'est jamais positionné dans le SecurityContext.
         */
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new GatewaySecretFilter(gatewaySecret), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}