package com.medilabo.notesservice.service;

import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.dto.NoteResponse;
import com.medilabo.notesservice.mapper.NoteMapper;
import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    private NoteService noteService;

    private NoteRequest sampleRequest;
    private Note sampleNote;

    @BeforeEach
    void setUp() {
        NoteMapper noteMapper = new NoteMapper();
        noteService = new NoteService(noteRepository, noteMapper);

        sampleRequest = new NoteRequest(1L, "Le patient se sent bien");

        sampleNote = new Note("note-1", 1L, "Le patient se sent bien", Instant.parse("2026-01-01T10:00:00Z"));
    }

    // --- findByPatientId ---

    @Test
    void should_ReturnMappedPage_When_RepositoryReturnsNotes() {
        // Arrange
        Note second = new Note("note-2", 1L, "Autre note", Instant.parse("2026-01-02T10:00:00Z"));
        Pageable pageable = PageRequest.of(0, 20);
        Page<Note> notePage = new PageImpl<>(List.of(sampleNote, second), pageable, 2);
        when(noteRepository.findByPatientId(eq(1L), eq(pageable))).thenReturn(notePage);

        // Act
        Page<NoteResponse> result = noteService.findByPatientId(1L, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo("note-1");
        assertThat(result.getContent().get(0).getPatientId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getNote()).isEqualTo("Le patient se sent bien");
        assertThat(result.getContent().get(0).getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(result.getContent().get(1).getId()).isEqualTo("note-2");
    }

    @Test
    void should_ReturnEmptyPage_When_RepositoryReturnsNoNotes() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(noteRepository.findByPatientId(eq(1L), eq(pageable))).thenReturn(Page.empty(pageable));

        // Act
        Page<NoteResponse> result = noteService.findByPatientId(1L, pageable);

        // Assert
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // --- create ---

    @Test
    void should_SaveAndReturnNoteResponse_When_CreatingNote() {
        // Arrange
        when(noteRepository.save(any(Note.class))).thenReturn(sampleNote);

        // Act
        NoteResponse result = noteService.create(sampleRequest);

        // Assert
        assertThat(result.getId()).isEqualTo("note-1");
        assertThat(result.getPatientId()).isEqualTo(1L);
        assertThat(result.getNote()).isEqualTo("Le patient se sent bien");
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void should_PassEntityWithRequestFieldsAndNoIdOrCreatedAt_When_CreatingNote() {
        // Arrange
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        when(noteRepository.save(captor.capture())).thenReturn(sampleNote);

        // Act
        noteService.create(sampleRequest);

        // Assert
        Note captured = captor.getValue();
        assertThat(captured.getId()).isNull();
        assertThat(captured.getCreatedAt()).isNull();
        assertThat(captured.getPatientId()).isEqualTo(1L);
        assertThat(captured.getNote()).isEqualTo("Le patient se sent bien");
    }
}