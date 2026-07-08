package com.medilabo.frontendservice.gateway;

import com.medilabo.frontendservice.dto.AssessmentDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AssessmentGatewayClient {

    private final RestClient restClient;
    private final HttpServletRequest request;

    public AssessmentGatewayClient(
            @Value("${gateway.base-url}") String gatewayBaseUrl,
            RestClient.Builder restClientBuilder,
            HttpServletRequest request) {
        this.restClient = restClientBuilder.baseUrl(gatewayBaseUrl).build();
        this.request = request;
    }

    public AssessmentDto findByPatientId(Long patientId) {
        return restClient.get()
                .uri("/assessment/{patientId}", patientId)
                .header(HttpHeaders.AUTHORIZATION, authHeaderFromSession())
                .retrieve()
                .body(AssessmentDto.class);
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
}