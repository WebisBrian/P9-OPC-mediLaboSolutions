package com.medilabo.frontendservice.gateway;

import com.medilabo.frontendservice.dto.NotePage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class NoteGatewayClient {

    // Choix d'affichage front (démo pagination), pas une option utilisateur : non configurable.
    private static final int SIZE = 3;

    private final RestClient restClient;
    private final HttpServletRequest request;

    public NoteGatewayClient(
            @Value("${gateway.base-url}") String gatewayBaseUrl,
            RestClient.Builder restClientBuilder,
            HttpServletRequest request) {
        this.restClient = restClientBuilder.baseUrl(gatewayBaseUrl).build();
        this.request = request;
    }

    public NotePage findByPatientId(Long patientId, int page) {
        return restClient.get()
                .uri("/notes?patientId={patientId}&page={page}&size={size}", patientId, page, SIZE)
                .header(HttpHeaders.AUTHORIZATION, authHeaderFromSession())
                .retrieve()
                .body(NotePage.class);
    }

    public void create(Long patientId, String note) {
        restClient.post()
                .uri("/notes")
                .header(HttpHeaders.AUTHORIZATION, authHeaderFromSession())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NoteCreateRequest(patientId, note))
                .retrieve()
                .toBodilessEntity();
    }

    private String authHeaderFromSession() {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        }
        return buildBasicAuthHeader(
                (String) session.getAttribute("username"),
                (String) session.getAttribute("password")
        );
    }

    private String buildBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // Corps du POST /notes uniquement : patientId vient de l'URL, jamais saisi dans le formulaire HTML.
    private record NoteCreateRequest(Long patientId, String note) {
    }
}