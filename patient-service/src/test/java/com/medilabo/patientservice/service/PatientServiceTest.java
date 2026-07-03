package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientRequest;
import com.medilabo.patientservice.dto.PatientResponse;
import com.medilabo.patientservice.exception.DuplicatePatientException;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.mapper.PatientMapper;
import com.medilabo.patientservice.model.Gender;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    private PatientService patientService;

    private PatientRequest sampleRequest;
    private Patient samplePatient;

    @BeforeEach
    void setUp() {
        PatientMapper patientMapper = new PatientMapper();
        patientService = new PatientService(patientRepository, patientMapper);

        sampleRequest = new PatientRequest(
                "John",
                "Doe",
                LocalDate.of(1980, 6, 15),
                Gender.M,
                "10 Main St",
                "0600000001"
        );

        samplePatient = new Patient(
                1L,
                "John",
                "Doe",
                LocalDate.of(1980, 6, 15),
                Gender.M,
                "10 Main St",
                "0600000001"
        );
    }

    // --- findAll ---

    @Test
    void should_ReturnMappedResponses_When_RepositoryReturnPatients() {
        // Arrange
        Patient second = new Patient(2L, "Jane", "Smith", LocalDate.of(1990, 3, 20), Gender.F, null, null);
        when(patientRepository.findAll()).thenReturn(List.of(samplePatient, second));

        // Act
        List<PatientResponse> result = patientService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getFirstName()).isEqualTo("John");
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void should_ReturnEmptyList_When_RepositoryReturnsNoPatients() {
        // Arrange
        when(patientRepository.findAll()).thenReturn(List.of());

        // Act
        List<PatientResponse> result = patientService.findAll();

        // Assert
        assertThat(result).isEmpty();
    }

    // --- findById ---

    @Test
    void should_ReturnPatientResponse_When_IdExists() {
        // Arrange
        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));

        // Act
        PatientResponse result = patientService.findById(1L);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1980, 6, 15));
        assertThat(result.getGender()).isEqualTo(Gender.M);
        assertThat(result.getAddress()).isEqualTo("10 Main St");
        assertThat(result.getPhone()).isEqualTo("0600000001");
    }

    @Test
    void should_ThrowPatientNotFoundException_When_IdDoesNotExistOnFindById() {
        // Arrange
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> patientService.findById(99L))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- create ---

    @Test
    void should_SaveAndReturnPatientResponse_When_CreatingPatient() {
        // Arrange
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        PatientResponse result = patientService.create(sampleRequest);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
    }

    @Test
    void should_PassEntityWithRequestFieldsAndNoId_When_CreatingPatient() {
        // Arrange
        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        when(patientRepository.save(captor.capture())).thenReturn(samplePatient);

        // Act
        patientService.create(sampleRequest);

        // Assert
        Patient captured = captor.getValue();
        assertThat(captured.getId()).isNull();
        assertThat(captured.getFirstName()).isEqualTo("John");
        assertThat(captured.getLastName()).isEqualTo("Doe");
        assertThat(captured.getBirthDate()).isEqualTo(LocalDate.of(1980, 6, 15));
        assertThat(captured.getGender()).isEqualTo(Gender.M);
        assertThat(captured.getAddress()).isEqualTo("10 Main St");
        assertThat(captured.getPhone()).isEqualTo("0600000001");
    }

    // --- create : duplicate detection ---

    @Test
    void should_ThrowDuplicatePatientExceptionAndNeverSave_When_DuplicateFoundOnCreate() {
        // Arrange
        when(patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001"))
                .thenReturn(Optional.of(samplePatient));

        // Act & Assert
        assertThatThrownBy(() -> patientService.create(sampleRequest))
                .isInstanceOf(DuplicatePatientException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void should_SaveSuccessfully_When_NoDuplicateFoundOnCreate() {
        // Arrange
        when(patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001"))
                .thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        PatientResponse result = patientService.create(sampleRequest);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void should_SkipDuplicateCheckAndSave_When_PhoneIsNullOnCreate() {
        // Arrange
        PatientRequest requestWithNullPhone = new PatientRequest(
                "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", null);
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        patientService.create(requestWithNullPhone);

        // Assert
        verify(patientRepository, never()).findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                any(), any(), any(), any());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void should_SkipDuplicateCheckAndSave_When_PhoneIsEmptyOnCreate() {
        // Arrange
        PatientRequest requestWithEmptyPhone = new PatientRequest(
                "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", "");
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        patientService.create(requestWithEmptyPhone);

        // Assert
        verify(patientRepository, never()).findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                any(), any(), any(), any());
        verify(patientRepository).save(any(Patient.class));
    }

    // --- update ---

    @Test
    void should_UpdateAndReturnPatientResponse_When_IdExists() {
        // Arrange
        PatientRequest updateRequest = new PatientRequest(
                "John",
                "Doe",
                LocalDate.of(1980, 6, 15),
                Gender.M,
                "20 New St",
                "0600000002"
        );
        Patient updatedPatient = new Patient(1L, "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "20 New St", "0600000002");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientRepository.save(any(Patient.class))).thenReturn(updatedPatient);

        // Act
        PatientResponse result = patientService.update(1L, updateRequest);

        // Assert
        assertThat(result.getAddress()).isEqualTo("20 New St");
        assertThat(result.getPhone()).isEqualTo("0600000002");
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void should_ThrowPatientNotFoundExceptionAndNeverSave_When_IdDoesNotExistOnUpdate() {
        // Arrange
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> patientService.update(99L, sampleRequest))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("99");

        verify(patientRepository, never()).save(any());
    }

    // --- update : duplicate detection ---

    @Test
    void should_ThrowDuplicatePatientExceptionAndNeverSave_When_DuplicateFoundOnUpdate() {
        // Arrange
        Patient anotherPatient = new Patient(2L, "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "99 Other St", "0600000001");
        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001", 1L))
                .thenReturn(Optional.of(anotherPatient));

        // Act & Assert
        assertThatThrownBy(() -> patientService.update(1L, sampleRequest))
                .isInstanceOf(DuplicatePatientException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void should_SaveSuccessfully_When_NoDuplicateFoundOnUpdate() {
        // Arrange
        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001", 1L))
                .thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        PatientResponse result = patientService.update(1L, sampleRequest);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void should_SkipDuplicateCheckAndSave_When_PhoneIsNullOnUpdate() {
        // Arrange
        PatientRequest requestWithNullPhone = new PatientRequest(
                "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", null);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientRepository.save(any(Patient.class))).thenReturn(samplePatient);

        // Act
        patientService.update(1L, requestWithNullPhone);

        // Assert
        verify(patientRepository, never()).findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                any(), any(), any(), any(), any());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void should_ThrowPatientNotFoundExceptionAndNeverCheckDuplicate_When_IdDoesNotExistOnUpdate() {
        // Arrange
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> patientService.update(99L, sampleRequest))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("99");
        verify(patientRepository, never()).findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                any(), any(), any(), any(), any());
        verify(patientRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void should_CallDeleteById_When_IdExists() {
        // Arrange
        when(patientRepository.existsById(1L)).thenReturn(true);

        // Act
        patientService.delete(1L);

        // Assert
        verify(patientRepository).deleteById(1L);
    }

    @Test
    void should_ThrowPatientNotFoundExceptionAndNeverDeleteById_When_IdDoesNotExistOnDelete() {
        // Arrange
        when(patientRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> patientService.delete(99L))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("99");

        verify(patientRepository, never()).deleteById(any());
    }
}