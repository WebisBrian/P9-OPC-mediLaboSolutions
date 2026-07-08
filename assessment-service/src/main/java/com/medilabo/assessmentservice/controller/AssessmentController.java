package com.medilabo.assessmentservice.controller;

import com.medilabo.assessmentservice.dto.AssessmentResponse;
import com.medilabo.assessmentservice.service.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<AssessmentResponse> assess(@PathVariable Long patientId) {
        return ResponseEntity.ok(assessmentService.assess(patientId));
    }
}