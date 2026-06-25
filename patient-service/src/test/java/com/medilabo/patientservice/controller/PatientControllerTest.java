package com.medilabo.patientservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medilabo.patientservice.dto.PatientRequest;
import com.medilabo.patientservice.dto.PatientResponse;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.model.Gender;
import com.medilabo.patientservice.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@TestPropertySource(properties = "spring.jackson.serialization.write-dates-as-timestamps=false")
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PatientService patientService;

    // --- Helpers ---

    private PatientResponse buildResponse(Long id, String firstName, String lastName) {
        return new PatientResponse(id, firstName, lastName,
                LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", "0600000001");
    }

    private PatientRequest buildValidRequest() {
        return new PatientRequest("John", "Doe",
                LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", "0600000001");
    }

    // --- GET /patients ---

    @Test
    void should_Return200WithList_When_FindingAllPatients() throws Exception {
        // Arrange
        List<PatientResponse> patients = List.of(
                buildResponse(1L, "John", "Doe"),
                buildResponse(2L, "Jane", "Smith")
        );
        when(patientService.findAll()).thenReturn(patients);

        // Act & Assert
        mockMvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    // --- GET /patients/{id} ---

    @Test
    void should_Return200WithPatient_When_FindingExistingId() throws Exception {
        // Arrange
        PatientResponse response = buildResponse(1L, "John", "Doe");
        when(patientService.findById(1L)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.birthDate").value("1980-06-15"))
                .andExpect(jsonPath("$.gender").value("M"));
    }

    @Test
    void should_Return404_When_FindingNonExistingId() throws Exception {
        // Arrange
        when(patientService.findById(99L)).thenThrow(new PatientNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(get("/patients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    // --- POST /patients ---

    @Test
    void should_Return201WithLocationAndBody_When_CreatingValidPatient() throws Exception {
        // Arrange
        PatientRequest request = buildValidRequest();
        PatientResponse created = buildResponse(1L, "John", "Doe");
        when(patientService.create(any(PatientRequest.class))).thenReturn(created);

        // Act & Assert
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/patients/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void should_Return400WithFirstNameError_When_PostingBlankFirstName() throws Exception {
        // Arrange
        PatientRequest request = buildValidRequest();
        request.setFirstName("");

        // Act & Assert
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists());

        verify(patientService, never()).create(any());
    }

    @Test
    void should_Return400WithBirthDateError_When_PostingFutureBirthDate() throws Exception {
        // Arrange
        PatientRequest request = buildValidRequest();
        request.setBirthDate(LocalDate.now().plusYears(1));

        // Act & Assert
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.birthDate").exists());

        verify(patientService, never()).create(any());
    }

    // --- PUT /patients/{id} ---

    @Test
    void should_Return200WithUpdatedPatient_When_UpdatingExistingId() throws Exception {
        // Arrange
        PatientRequest request = buildValidRequest();
        PatientResponse updated = buildResponse(1L, "John", "Doe");
        when(patientService.update(eq(1L), any(PatientRequest.class))).thenReturn(updated);

        // Act & Assert
        mockMvc.perform(put("/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void should_Return404_When_UpdatingNonExistingId() throws Exception {
        // Arrange
        PatientRequest request = buildValidRequest();
        when(patientService.update(eq(99L), any(PatientRequest.class)))
                .thenThrow(new PatientNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(put("/patients/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    // --- DELETE /patients/{id} ---

    @Test
    void should_Return204_When_DeletingExistingId() throws Exception {
        // Arrange
        doNothing().when(patientService).delete(1L);

        // Act & Assert
        mockMvc.perform(delete("/patients/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void should_Return404_When_DeletingNonExistingId() throws Exception {
        // Arrange
        doThrow(new PatientNotFoundException(99L)).when(patientService).delete(99L);

        // Act & Assert
        mockMvc.perform(delete("/patients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }
}