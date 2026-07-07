package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.config.MongoAuditingConfig;
import com.medilabo.notesservice.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration Mongo réel (Testcontainers) : valide les requêtes dérivées, la pagination
 * et l'auditing (@CreatedDate), non vérifiables avec un simple mock de repository.
 */
@DataMongoTest
@Testcontainers
@Import(MongoAuditingConfig.class)
class NoteRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private NoteRepository noteRepository;

    @BeforeEach
    void cleanUp() {
        noteRepository.deleteAll();
    }

    private Note note(Long patientId, String text) {
        Note note = new Note();
        note.setPatientId(patientId);
        note.setNote(text);
        return note;
    }

    @Test
    void should_ReturnOnlyNotesOfGivenPatient_When_FindingByPatientId() {
        // Arrange
        noteRepository.saveAll(List.of(
                note(1L, "Note A"),
                note(1L, "Note B"),
                note(2L, "Note C")
        ));

        // Act
        Page<Note> result = noteRepository.findByPatientId(1L, PageRequest.of(0, 10));

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(n -> n.getPatientId().equals(1L));
    }

    @Test
    void should_ReturnRequestedPageSize_When_PatientHasMoreNotesThanPageSize() {
        // Arrange
        noteRepository.saveAll(List.of(
                note(1L, "Note 1"),
                note(1L, "Note 2"),
                note(1L, "Note 3"),
                note(1L, "Note 4")
        ));

        // Act
        Page<Note> result = noteRepository.findByPatientId(1L, PageRequest.of(0, 3));

        // Assert
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void should_SetCreatedAt_When_SavingNoteWithoutExplicitCreatedAt() {
        // Arrange
        Note note = note(1L, "Note auditée");

        // Act
        Note saved = noteRepository.save(note);

        // Assert
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}