package com.medilabo.frontendservice.controller;

import com.medilabo.frontendservice.dto.PatientDto;
import com.medilabo.frontendservice.gateway.PatientGatewayClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Controller
public class PatientController {

    private final PatientGatewayClient patientGatewayClient;

    public PatientController(PatientGatewayClient patientGatewayClient) {
        this.patientGatewayClient = patientGatewayClient;
    }

    @GetMapping("/patients")
    public String listPatients(Model model) {
        try {
            List<PatientDto> patients = patientGatewayClient.findAll();
            model.addAttribute("patients", patients);
            return "patients/list";
        } catch (HttpClientErrorException.Unauthorized e) {
            return "redirect:/login";
        }
    }
}