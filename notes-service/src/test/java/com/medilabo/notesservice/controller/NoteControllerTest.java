package com.medilabo.notesservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.dto.NoteResponse;
import com.medilabo.notesservice.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = NoteController.class,
            excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@TestPropertySource(properties = "spring.jackson.serialization.write-dates-as-timestamps=false")
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NoteService noteService;

    // --- Helpers ---

    private NoteResponse buildResponse(String id, Long patientId, String note) {
        return new NoteResponse(id, patientId, note, Instant.parse("2026-01-01T10:00:00Z"));
    }

    private NoteRequest buildValidRequest() {
        return new NoteRequest(1L, "Le patient se sent bien");
    }

    // --- GET /notes ---

    @Test
    void should_Return200WithPagedNotes_When_FindingByPatientId() throws Exception {
        // Arrange
        List<NoteResponse> notes = List.of(
                buildResponse("note-1", 1L, "Première note"),
                buildResponse("note-2", 1L, "Deuxième note")
        );
        Pageable pageable = PageRequest.of(0, 20);
        when(noteService.findByPatientId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(notes, pageable, 2));

        // Act & Assert
        mockMvc.perform(get("/notes").param("patientId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].note").value("Première note"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    // --- POST /notes ---

    @Test
    void should_Return201WithBody_When_CreatingValidNote() throws Exception {
        // Arrange
        NoteRequest request = buildValidRequest();
        NoteResponse created = buildResponse("note-1", 1L, "Le patient se sent bien");
        when(noteService.create(any(NoteRequest.class))).thenReturn(created);

        // Act & Assert
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("note-1"))
                .andExpect(jsonPath("$.patientId").value(1))
                .andExpect(jsonPath("$.note").value("Le patient se sent bien"));
    }

    @Test
    void should_Return400WithNoteError_When_PostingBlankNote() throws Exception {
        // Arrange
        NoteRequest request = buildValidRequest();
        request.setNote("");

        // Act & Assert
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());

        verify(noteService, never()).create(any());
    }

    @Test
    void should_Return400WithPatientIdError_When_PostingNullPatientId() throws Exception {
        // Arrange
        NoteRequest request = buildValidRequest();
        request.setPatientId(null);

        // Act & Assert
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.patientId").exists());

        verify(noteService, never()).create(any());
    }
}