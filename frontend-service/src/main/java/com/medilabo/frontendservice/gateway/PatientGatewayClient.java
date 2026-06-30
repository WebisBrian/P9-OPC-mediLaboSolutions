package com.medilabo.frontendservice.gateway;

import com.medilabo.frontendservice.dto.PatientDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class PatientGatewayClient {

    private final RestClient restClient;
    private final HttpServletRequest request;

    public PatientGatewayClient(
            @Value("${gateway.base-url}") String gatewayBaseUrl,
            RestClient.Builder restClientBuilder,
            HttpServletRequest request) {
        this.restClient = restClientBuilder.baseUrl(gatewayBaseUrl).build();
        this.request = request;
    }

    public List<PatientDto> findAll() {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        }
        String username = (String) session.getAttribute("username");
        String password = (String) session.getAttribute("password");

        return restClient.get()
                .uri("/patients")
                .header(HttpHeaders.AUTHORIZATION, buildBasicAuthHeader(username, password))
                .retrieve()
                .body(new ParameterizedTypeReference<List<PatientDto>>() {});
    }

    public void verifyCredentials(String username, String password) {
        restClient.get()
                .uri("/patients")
                .header(HttpHeaders.AUTHORIZATION, buildBasicAuthHeader(username, password))
                .retrieve()
                .toBodilessEntity();
    }

    private String buildBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}