package com.medilabo.frontendservice.controller;

import com.medilabo.frontendservice.dto.PatientDto;
import com.medilabo.frontendservice.dto.PatientForm;
import com.medilabo.frontendservice.gateway.PatientGatewayClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

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
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        }
    }

    @GetMapping("/patients/new")
    public String showNewForm(Model model) {
        model.addAttribute("patientForm", new PatientForm());
        return "patients/form";
    }

    @PostMapping("/patients")
    public String createPatient(@Valid @ModelAttribute PatientForm patientForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "patients/form";
        }
        try {
            patientGatewayClient.create(patientForm);
            return "redirect:/patients";
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException e) {
            log.warn("Échec création patient via gateway : {}", e.getStatusCode());
            model.addAttribute("errorMessage", "Les données envoyées sont invalides.");
            return "patients/form";
        }
    }

    @GetMapping("/patients/{id}")
    public String showDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            PatientDto patient = patientGatewayClient.findById(id);
            model.addAttribute("patient", patient);
            return "patients/detail";
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Patient introuvable via gateway : {}", e.getStatusCode());
            redirectAttributes.addFlashAttribute("errorMessage", "Patient introuvable.");
            return "redirect:/patients";
        }
    }

    @GetMapping("/patients/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            PatientDto patient = patientGatewayClient.findById(id);
            model.addAttribute("patientForm", toForm(patient));
            model.addAttribute("patientId", id);
            return "patients/form";
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Patient introuvable via gateway : {}", e.getStatusCode());
            redirectAttributes.addFlashAttribute("errorMessage", "Patient introuvable.");
            return "redirect:/patients";
        }
    }

    @PostMapping("/patients/{id}")
    public String updatePatient(
            @PathVariable Long id,
            @Valid @ModelAttribute PatientForm patientForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("patientId", id);
            return "patients/form";
        }
        try {
            patientGatewayClient.update(id, patientForm);
            return "redirect:/patients";
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Patient introuvable via gateway : {}", e.getStatusCode());
            redirectAttributes.addFlashAttribute("errorMessage", "Patient introuvable.");
            return "redirect:/patients";
        } catch (HttpClientErrorException e) {
            log.warn("Échec mise à jour patient via gateway : {}", e.getStatusCode());
            model.addAttribute("patientId", id);
            model.addAttribute("errorMessage", "Les données envoyées sont invalides.");
            return "patients/form";
        }
    }

    @PostMapping("/patients/{id}/delete")
    public String deletePatient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            patientGatewayClient.delete(id);
            return "redirect:/patients";
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Patient introuvable via gateway : {}", e.getStatusCode());
            redirectAttributes.addFlashAttribute("errorMessage", "Patient introuvable.");
            return "redirect:/patients";
        }
    }

    private PatientForm toForm(PatientDto dto) {
        PatientForm form = new PatientForm();
        form.setFirstName(dto.firstName());
        form.setLastName(dto.lastName());
        form.setBirthDate(dto.birthDate());
        form.setGender(dto.gender());
        form.setAddress(dto.address());
        form.setPhone(dto.phone());
        return form;
    }
}