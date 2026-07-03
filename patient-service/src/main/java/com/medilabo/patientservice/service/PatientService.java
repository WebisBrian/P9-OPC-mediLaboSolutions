package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientRequest;
import com.medilabo.patientservice.dto.PatientResponse;
import com.medilabo.patientservice.exception.DuplicatePatientException;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.mapper.PatientMapper;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public PatientService(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    public List<PatientResponse> findAll() {
        return patientRepository.findAll().stream()
                .map(patientMapper::toResponse)
                .toList();
    }

    public PatientResponse findById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(id));
        return patientMapper.toResponse(patient);
    }

    public PatientResponse create(PatientRequest request) {
        checkForDuplicate(request, null);
        Patient patient = patientMapper.toEntity(request);
        PatientResponse response = patientMapper.toResponse(patientRepository.save(patient));
        log.info("Patient created with id {}", response.getId());
        return response;
    }

    public PatientResponse update(Long id, PatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(id));
        checkForDuplicate(request, id);
        patientMapper.updateEntity(patient, request);
        PatientResponse response = patientMapper.toResponse(patientRepository.save(patient));
        log.info("Patient updated with id {}", id);
        return response;
    }

    private void checkForDuplicate(PatientRequest request, Long excludeId) {
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;
        if (phone == null || phone.isEmpty()) {
            return;
        }
        String firstName = request.getFirstName().trim();
        String lastName  = request.getLastName().trim();
        boolean duplicate = excludeId == null
                ? patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhone(
                        firstName, lastName, request.getBirthDate(), phone).isPresent()
                : patientRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndPhoneAndIdNot(
                        firstName, lastName, request.getBirthDate(), phone, excludeId).isPresent();
        if (duplicate) {
            log.warn("Duplicate patient detected");
            throw new DuplicatePatientException();
        }
    }

    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new PatientNotFoundException(id);
        }
        patientRepository.deleteById(id);
        log.info("Patient deleted with id {}", id);
    }
}