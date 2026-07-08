package com.medilabo.assessmentservice.controller;

import com.medilabo.assessmentservice.dto.AssessmentResponse;
import com.medilabo.assessmentservice.exception.InvalidPatientDataException;
import com.medilabo.assessmentservice.exception.PatientNotFoundException;
import com.medilabo.assessmentservice.service.AssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AssessmentController.class,
            excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    @Test
    void should_Return200WithAssessment_When_PatientExists() throws Exception {
        AssessmentResponse response = new AssessmentResponse(4L, "Early onset",
                List.of("Anticorps", "Réaction", "Fumeur"));
        when(assessmentService.assess(4L)).thenReturn(response);

        mockMvc.perform(get("/assessment/4"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.patientId").value(4))
                .andExpect(jsonPath("$.riskLevel").value("Early onset"))
                .andExpect(jsonPath("$.triggers", hasSize(3)))
                .andExpect(jsonPath("$.triggers[0]").value("Anticorps"));
    }

    @Test
    void should_Return200WithNoneAndEmptyTriggers_When_PatientHasNoTrigger() throws Exception {
        AssessmentResponse response = new AssessmentResponse(1L, "None", List.of());
        when(assessmentService.assess(1L)).thenReturn(response);

        mockMvc.perform(get("/assessment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("None"))
                .andExpect(jsonPath("$.triggers", hasSize(0)));
    }

    @Test
    void should_Return404_When_PatientDoesNotExist() throws Exception {
        when(assessmentService.assess(99L)).thenThrow(new PatientNotFoundException(99L));

        mockMvc.perform(get("/assessment/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    @Test
    void should_Return502_When_PatientDataIsInvalid() throws Exception {
        when(assessmentService.assess(1L))
                .thenThrow(new InvalidPatientDataException("birthDate manquant pour le patient 1"));

        mockMvc.perform(get("/assessment/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message", containsString("1")));
    }
}