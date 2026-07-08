package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.PatientResponse;
import com.medilabo.assessmentservice.exception.PatientNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PatientClient {

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    private final RestClient restClient;
    private final String gatewaySecret;

    public PatientClient(
            @Value("${patient-service.base-url}") String baseUrl,
            @Value("${gateway.secret}") String gatewaySecret,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.gatewaySecret = gatewaySecret;
    }

    public PatientResponse findById(Long patientId) {
        try {
            return restClient.get()
                    .uri("/patients/{id}", patientId)
                    .header(GATEWAY_SECRET_HEADER, gatewaySecret)
                    .retrieve()
                    .body(PatientResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Patient not found calling patient-service: status={}", ex.getStatusCode());
            throw new PatientNotFoundException(patientId);
        } catch (RestClientResponseException ex) {
            log.warn("Failed to call patient-service: status={}", ex.getStatusCode());
            throw ex;
        }
    }
}