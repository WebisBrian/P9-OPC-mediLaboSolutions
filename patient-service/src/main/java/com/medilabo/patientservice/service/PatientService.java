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

    /**
     * Refuse la création/modification si un autre patient partage déjà même nom/prénom
     * (insensible à la casse), date de naissance et téléphone. Sans téléphone renseigné,
     * aucune vérification n'est effectuée : ce n'est pas un critère fiable à lui seul.
     *
     * @param request   les données du patient à créer/modifier
     * @param excludeId lors d'une modification, l'id du patient à exclure de la recherche
     *                  (pour ne pas le détecter comme doublon de lui-même) ; {@code null} en création
     * @throws DuplicatePatientException si un autre patient correspond déjà à ces critères
     */
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