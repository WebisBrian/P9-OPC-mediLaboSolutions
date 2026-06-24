package com.medilabo.patientservice.mapper;

import com.medilabo.patientservice.dto.PatientRequest;
import com.medilabo.patientservice.dto.PatientResponse;
import com.medilabo.patientservice.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    public Patient toEntity(PatientRequest request) {
        Patient patient = new Patient();
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setBirthDate(request.getBirthDate());
        patient.setGender(request.getGender());
        patient.setAddress(request.getAddress());
        patient.setPhone(request.getPhone());
        return patient;
    }

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getBirthDate(),
                patient.getGender(),
                patient.getAddress(),
                patient.getPhone()
        );
    }

    public void updateEntity(Patient target, PatientRequest source) {
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setBirthDate(source.getBirthDate());
        target.setGender(source.getGender());
        target.setAddress(source.getAddress());
        target.setPhone(source.getPhone());
    }
}