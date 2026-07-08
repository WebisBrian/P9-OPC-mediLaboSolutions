package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.client.NoteClient;
import com.medilabo.assessmentservice.client.PatientClient;
import com.medilabo.assessmentservice.dto.AssessmentResponse;
import com.medilabo.assessmentservice.dto.NoteResponse;
import com.medilabo.assessmentservice.dto.PatientResponse;
import com.medilabo.assessmentservice.exception.InvalidPatientDataException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class AssessmentService {

    private final PatientClient patientClient;
    private final NoteClient noteClient;
    private final TriggerDetector triggerDetector;

    public AssessmentService(PatientClient patientClient, NoteClient noteClient, TriggerDetector triggerDetector) {
        this.patientClient = patientClient;
        this.noteClient = noteClient;
        this.triggerDetector = triggerDetector;
    }

    public AssessmentResponse assess(Long patientId) {
        PatientResponse patient = patientClient.findById(patientId);
        if (patient.birthDate() == null) {
            throw new InvalidPatientDataException("birthDate manquant pour le patient " + patientId);
        }

        List<NoteResponse> notes = noteClient.findAllByPatientId(patientId);

        int age = Period.between(patient.birthDate(), LocalDate.now()).getYears();
        List<String> noteTexts = notes.stream().map(NoteResponse::note).toList();
        List<String> triggers = triggerDetector.detect(noteTexts);

        RiskLevel riskLevel = RiskCalculator.calculate(age, patient.gender(), triggers.size());

        return new AssessmentResponse(patientId, riskLevel.getLabel(), triggers);
    }
}