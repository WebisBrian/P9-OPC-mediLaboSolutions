package com.medilabo.patientservice.repository;

import com.medilabo.patientservice.model.Gender;
import com.medilabo.patientservice.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration sur PostgreSQL réel (Testcontainers) : valide les requêtes dérivées de
 * déduplication, en particulier le comportement IgnoreCase qui n'est pas garanti identique
 * entre H2 et PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PatientRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PatientRepository patientRepository;

    private Patient samplePatient() {
        return new Patient(null, "John", "Doe", LocalDate.of(1980, 6, 15), Gender.M, "10 Main St", "0600000001");
    }

    // --- findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone ---

    @Test
    void should_FindPatient_When_AllFieldsMatchExactly() {
        // Arrange
        patientRepository.save(samplePatient());

        // Act
        Optional<Patient> result = patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001");

        // Assert
        assertThat(result).isPresent();
    }

    @Test
    void should_FindPatient_When_NameCaseDiffersFromStoredValue() {
        // Arrange
        patientRepository.save(samplePatient());

        // Act
        Optional<Patient> result = patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                "john", "DOE", LocalDate.of(1980, 6, 15), "0600000001");

        // Assert
        assertThat(result).isPresent();
    }

    @Test
    void should_ReturnEmpty_When_NoPatientMatches() {
        // Arrange
        patientRepository.save(samplePatient());

        // Act
        Optional<Patient> result = patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                "Alice", "Martin", LocalDate.of(1990, 1, 1), "0700000000");

        // Assert
        assertThat(result).isEmpty();
    }

    // --- findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot ---

    @Test
    void should_FindOtherPatient_When_ExcludingOneOfTwoIdenticalPatients() {
        // Arrange
        Patient first = patientRepository.save(samplePatient());
        Patient second = patientRepository.save(samplePatient());

        // Act
        Optional<Patient> result = patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001", first.getId());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(second.getId());
    }

    @Test
    void should_ReturnEmpty_When_ExcludingTheOnlyMatchingPatient() {
        // Arrange
        Patient saved = patientRepository.save(samplePatient());

        // Act
        Optional<Patient> result = patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                "John", "Doe", LocalDate.of(1980, 6, 15), "0600000001", saved.getId());

        // Assert
        assertThat(result).isEmpty();
    }
}