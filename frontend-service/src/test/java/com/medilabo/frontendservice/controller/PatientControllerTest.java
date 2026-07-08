package com.medilabo.frontendservice.controller;

import com.medilabo.frontendservice.config.WebConfig;
import com.medilabo.frontendservice.dto.AssessmentDto;
import com.medilabo.frontendservice.dto.NotePage;
import com.medilabo.frontendservice.dto.PatientDto;
import com.medilabo.frontendservice.gateway.AssessmentGatewayClient;
import com.medilabo.frontendservice.gateway.NoteGatewayClient;
import com.medilabo.frontendservice.gateway.PatientGatewayClient;
import com.medilabo.frontendservice.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Le point le plus important : l'échec de l'appel assessment ne doit jamais empêcher
 * l'affichage de la page détail (patient + notes). AuthInterceptor/WebConfig sont importés
 * tels quels (pas de contexte de sécurité Spring côté front) et satisfaits via une session
 * simulant un utilisateur déjà connecté.
 */
@WebMvcTest(controllers = PatientController.class)
@Import({WebConfig.class, AuthInterceptor.class})
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientGatewayClient patientGatewayClient;

    @MockitoBean
    private NoteGatewayClient noteGatewayClient;

    @MockitoBean
    private AssessmentGatewayClient assessmentGatewayClient;

    // --- Helpers ---

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");
        session.setAttribute("password", "testpass");
        return session;
    }

    private PatientDto buildPatient(Long id) {
        return new PatientDto(id, "John", "Doe", LocalDate.of(1980, 6, 15), "M", "10 Main St", "0600000001");
    }

    private NotePage emptyNotePage() {
        return new NotePage(List.of(), 0, 0, true, true);
    }

    // --- GET /patients/{id} — intégration assessment ---

    @Test
    void should_PutAssessmentInModel_When_AssessmentCallSucceeds() throws Exception {
        // Arrange
        when(patientGatewayClient.findById(1L)).thenReturn(buildPatient(1L));
        when(noteGatewayClient.findByPatientId(1L, 0)).thenReturn(emptyNotePage());
        AssessmentDto assessment = new AssessmentDto(1L, "Borderline", List.of("Anormal", "Réaction"));
        when(assessmentGatewayClient.findByPatientId(1L)).thenReturn(assessment);

        // Act & Assert
        mockMvc.perform(get("/patients/1").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attribute("assessment", assessment));
    }

    @Test
    void should_RenderPageWithNullAssessment_When_AssessmentCallFails() throws Exception {
        // Arrange
        when(patientGatewayClient.findById(1L)).thenReturn(buildPatient(1L));
        when(noteGatewayClient.findByPatientId(1L, 0)).thenReturn(emptyNotePage());
        when(assessmentGatewayClient.findByPatientId(1L))
                .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null));

        // Act : la page détail (patient + notes) doit quand même s'afficher, sans erreur 5xx.
        MvcResult result = mockMvc.perform(get("/patients/1").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andReturn();

        // Assert : l'assessment est dégradé à null plutôt que de faire échouer la page entière.
        assertThat(result.getModelAndView().getModel()).containsKey("assessment");
        assertThat(result.getModelAndView().getModel().get("assessment")).isNull();
    }

    @Test
    void should_RedirectToLogin_When_AssessmentCallIsUnauthorized() throws Exception {
        // Arrange
        when(patientGatewayClient.findById(1L)).thenReturn(buildPatient(1L));
        when(noteGatewayClient.findByPatientId(1L, 0)).thenReturn(emptyNotePage());
        when(assessmentGatewayClient.findByPatientId(1L))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        // Act & Assert : Unauthorized reste géré comme les autres appels gateway (redirect login).
        mockMvc.perform(get("/patients/1").session(authenticatedSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/login"));
    }

    // --- POST /patients/{id}/notes — échec de validation, assessment doit rester affiché ---

    @Test
    void should_KeepAssessmentInModel_When_NoteValidationFails() throws Exception {
        // Arrange
        when(patientGatewayClient.findById(1L)).thenReturn(buildPatient(1L));
        when(noteGatewayClient.findByPatientId(1L, 0)).thenReturn(emptyNotePage());
        AssessmentDto assessment = new AssessmentDto(1L, "Borderline", List.of("Anormal", "Réaction"));
        when(assessmentGatewayClient.findByPatientId(1L)).thenReturn(assessment);

        // Act & Assert : note vide -> violation @NotBlank -> rechargement de la page détail complète,
        // qui doit conserver l'assessment (avant ce correctif, il disparaissait sur ce chemin).
        mockMvc.perform(post("/patients/1/notes")
                        .session(authenticatedSession())
                        .param("note", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attribute("assessment", assessment))
                .andExpect(model().attributeHasFieldErrors("noteForm", "note"));
    }
}