package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.client.NoteClient;
import com.medilabo.assessmentservice.client.PatientClient;
import com.medilabo.assessmentservice.dto.AssessmentResponse;
import com.medilabo.assessmentservice.dto.NoteResponse;
import com.medilabo.assessmentservice.dto.PatientResponse;
import com.medilabo.assessmentservice.exception.InvalidPatientDataException;
import com.medilabo.assessmentservice.exception.PatientNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Teste uniquement ce qui est propre à AssessmentService : calcul de l'âge et orchestration
 * patient/notes. La table de décision (RiskCalculator) et la détection (TriggerDetector) sont
 * déjà couvertes exhaustivement dans leurs tests dédiés — on utilise ici leurs vraies instances,
 * seuls les appels réseau (PatientClient/NoteClient) sont mockés.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private PatientClient patientClient;

    @Mock
    private NoteClient noteClient;

    // --- Frontière d'âge 30 ---

    @Test
    void should_ReturnBorderline_When_PatientIsExactly30WithTwoTriggers() {
        // Arrange
        LocalDate birthDate = LocalDate.now().minusYears(30);
        when(patientClient.findById(1L)).thenReturn(buildPatient(1L, birthDate, "F"));
        when(noteClient.findAllByPatientId(1L))
                .thenReturn(List.of(buildNote(1L, "Anormal, réaction aux médicaments.")));

        // Act
        AssessmentResponse response = service().assess(1L);

        // Assert
        assertThat(response.riskLevel()).isEqualTo("Borderline");
        assertThat(response.triggers()).containsExactlyInAnyOrder("Anormal", "Réaction");
    }

    @Test
    void should_ReturnNone_When_PatientIsOneDayUnder30WithTwoTriggers() {
        // Arrange : pas encore 30 ans (30 ans moins un jour) -> régime "<30 ans".
        LocalDate birthDate = LocalDate.now().minusYears(30).plusDays(1);
        when(patientClient.findById(1L)).thenReturn(buildPatient(1L, birthDate, "M"));
        when(noteClient.findAllByPatientId(1L))
                .thenReturn(List.of(buildNote(1L, "Anormal, réaction aux médicaments.")));

        // Act
        AssessmentResponse response = service().assess(1L);

        // Assert
        assertThat(response.riskLevel()).isEqualTo("None");
    }

    @Test
    void should_ReturnInDanger_When_PatientIsOver30WithSixTriggers() {
        // Arrange
        LocalDate birthDate = LocalDate.now().minusYears(31);
        when(patientClient.findById(1L)).thenReturn(buildPatient(1L, birthDate, "M"));
        when(noteClient.findAllByPatientId(1L))
                .thenReturn(List.of(buildNote(1L, "Taille, Poids, Fumeur, Anormal, Cholestérol, Vertige")));

        // Act
        AssessmentResponse response = service().assess(1L);

        // Assert
        assertThat(response.riskLevel()).isEqualTo("In Danger");
        assertThat(response.triggers()).hasSize(6);
    }

    // --- Orchestration ---

    @Test
    void should_ReturnNoneWithEmptyTriggers_When_PatientHasNoNotes() {
        // Arrange
        LocalDate birthDate = LocalDate.now().minusYears(40);
        when(patientClient.findById(2L)).thenReturn(buildPatient(2L, birthDate, "F"));
        when(noteClient.findAllByPatientId(2L)).thenReturn(List.of());

        // Act
        AssessmentResponse response = service().assess(2L);

        // Assert
        assertThat(response.patientId()).isEqualTo(2L);
        assertThat(response.riskLevel()).isEqualTo("None");
        assertThat(response.triggers()).isEmpty();
    }

    @Test
    void should_AssembleResponseWithPatientIdRiskLevelAndTriggers_When_AssessingPatient() {
        // Arrange
        LocalDate birthDate = LocalDate.now().minusYears(40);
        when(patientClient.findById(4L)).thenReturn(buildPatient(4L, birthDate, "F"));
        when(noteClient.findAllByPatientId(4L))
                .thenReturn(List.of(buildNote(4L, "Anticorps élevés, taille et poids mesurés.")));

        // Act
        AssessmentResponse response = service().assess(4L);

        // Assert
        assertThat(response.patientId()).isEqualTo(4L);
        assertThat(response.riskLevel()).isEqualTo("Borderline");
        assertThat(response.triggers()).containsExactlyInAnyOrder("Anticorps", "Taille", "Poids");
    }

    @Test
    void should_CallNoteClientAfterPatientClient_When_PatientExists() {
        // Arrange
        LocalDate birthDate = LocalDate.now().minusYears(40);
        when(patientClient.findById(1L)).thenReturn(buildPatient(1L, birthDate, "M"));
        when(noteClient.findAllByPatientId(1L)).thenReturn(List.of());

        // Act
        service().assess(1L);

        // Assert
        InOrder inOrder = inOrder(patientClient, noteClient);
        inOrder.verify(patientClient).findById(1L);
        inOrder.verify(noteClient).findAllByPatientId(1L);
    }

    // --- Garde birthDate null ---

    @Test
    void should_ThrowInvalidPatientDataException_When_BirthDateIsNull() {
        // Arrange
        when(patientClient.findById(1L)).thenReturn(buildPatient(1L, null, "M"));

        // Act & Assert
        assertThatThrownBy(() -> service().assess(1L))
                .isInstanceOf(InvalidPatientDataException.class)
                .hasMessageContaining("patient 1");

        verify(noteClient, never()).findAllByPatientId(any());
    }

    // --- Propagation patient introuvable ---

    @Test
    void should_PropagateException_When_PatientNotFound() {
        // Arrange
        when(patientClient.findById(99L)).thenThrow(new PatientNotFoundException(99L));

        // Act & Assert
        assertThatThrownBy(() -> service().assess(99L))
                .isInstanceOf(PatientNotFoundException.class);

        verifyNoInteractions(noteClient);
    }

    // --- Helpers ---

    private AssessmentService service() {
        return new AssessmentService(patientClient, noteClient, new TriggerDetector());
    }

    private PatientResponse buildPatient(Long id, LocalDate birthDate, String gender) {
        return new PatientResponse(id, "John", "Doe", birthDate, gender, "10 Main St", "0600000001");
    }

    private NoteResponse buildNote(Long patientId, String text) {
        return new NoteResponse("note-1", patientId, text, Instant.parse("2026-01-01T10:00:00Z"));
    }
}