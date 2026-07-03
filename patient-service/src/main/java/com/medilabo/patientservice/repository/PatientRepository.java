package com.medilabo.patientservice.repository;

import com.medilabo.patientservice.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
            String firstName, String lastName, LocalDate birthDate, String phone);

    Optional<Patient> findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
            String firstName, String lastName, LocalDate birthDate, String phone, Long id);
}