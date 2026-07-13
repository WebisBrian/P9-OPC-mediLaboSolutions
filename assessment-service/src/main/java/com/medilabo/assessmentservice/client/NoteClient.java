package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.NotePage;
import com.medilabo.assessmentservice.dto.NoteResponse;
import com.medilabo.assessmentservice.security.RelayedJwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NoteClient {

    private static final int PAGE_SIZE = 50;

    private final RestClient restClient;

    public NoteClient(
            @Value("${notes-service.base-url}") String baseUrl,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Récupère l'intégralité des notes du patient, page par page, pour garantir
     * l'exhaustivité de la détection des déclencheurs (un déclencheur raté fausse le calcul).
     */
    public List<NoteResponse> findAllByPatientId(Long patientId) {
        List<NoteResponse> notes = new ArrayList<>();
        int page = 0;
        NotePage notePage;
        do {
            notePage = fetchPage(patientId, page);
            notes.addAll(notePage.content());
            page++;
        } while (!notePage.last());
        return notes;
    }

    private NotePage fetchPage(Long patientId, int page) {
        try {
            return restClient.get()
                    .uri("/notes?patientId={patientId}&page={page}&size={size}", patientId, page, PAGE_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + RelayedJwtProvider.currentTokenValue())
                    .retrieve()
                    .body(NotePage.class);
        } catch (RestClientResponseException ex) {
            log.warn("Failed to call notes-service: status={}", ex.getStatusCode());
            throw ex;
        }
    }
}