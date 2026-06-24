package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientRequest;
import com.medilabo.patientservice.dto.PatientResponse;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.mapper.PatientMapper;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
        Patient patient = patientMapper.toEntity(request);
        return patientMapper.toResponse(patientRepository.save(patient));
    }

    public PatientResponse update(Long id, PatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(id));
        patientMapper.updateEntity(patient, request);
        return patientMapper.toResponse(patientRepository.save(patient));
    }

    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new PatientNotFoundException(id);
        }
        patientRepository.deleteById(id);
    }
}