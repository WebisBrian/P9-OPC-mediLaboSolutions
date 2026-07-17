package com.medilabo.gatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configuration de sécurité centralisée à la gateway (API réactive WebFlux).
 * Authentification HTTP Basic, un seul utilisateur in-memory, mots de passe BCrypt.
 */
@Configuration
public class SecurityConfig {

    // @Value résout les variables d'environnement nativement : pas besoin de relais
    // dans application.yml. Les variables sont chargées par docker-compose à la racine du projet
    @Value("${SECURITY_USERNAME}")
    private String username;

    @Value("${SECURITY_PASSWORD}")
    private String password;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .httpBasic(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated())
                // CSRF désactivé : API stateless HTTP Basic, pas de session cookie
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MapReactiveUserDetailsService userDetailsService(PasswordEncoder encoder) {
        // ROLE_USER : authority neutre requise par l'API UserDetails, sans logique de droits
        UserDetails user = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(user);
    }
}