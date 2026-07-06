package com.medilabo.frontendservice.controller;

import com.medilabo.frontendservice.dto.NoteForm;
import com.medilabo.frontendservice.dto.PatientDto;
import com.medilabo.frontendservice.dto.PatientForm;
import com.medilabo.frontendservice.gateway.NoteGatewayClient;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientGatewayClient patientGatewayClient;
    private final NoteGatewayClient noteGatewayClient;

    public PatientController(PatientGatewayClient patientGatewayClient, NoteGatewayClient noteGatewayClient) {
        this.patientGatewayClient = patientGatewayClient;
        this.noteGatewayClient = noteGatewayClient;
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
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Doublon patient refusé par le back : {}", e.getStatusCode());
            model.addAttribute("errorMessage", "Un patient identique existe déjà.");
            return "patients/form";
        } catch (HttpClientErrorException e) {
            log.warn("Échec création patient via gateway : {}", e.getStatusCode());
            model.addAttribute("errorMessage", "Les données envoyées sont invalides.");
            return "patients/form";
        }
    }

    @GetMapping("/patients/{id}")
    public String showDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            PatientDto patient = patientGatewayClient.findById(id);
            model.addAttribute("patient", patient);
            model.addAttribute("notePage", noteGatewayClient.findByPatientId(id, page));
            model.addAttribute("noteForm", new NoteForm());
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

    @PostMapping("/patients/{id}/notes")
    public String addNote(
            @PathVariable Long id,
            @Valid @ModelAttribute NoteForm noteForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return reloadDetailWithForm(id, noteForm, model, redirectAttributes);
        }
        try {
            noteGatewayClient.create(id, noteForm.getNote());
            return "redirect:/patients/" + id;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Appel gateway non autorisé (session invalide/expirée) : {}", e.getStatusCode());
            return "redirect:/login";
        } catch (HttpClientErrorException e) {
            log.warn("Échec ajout de note via gateway : {}", e.getStatusCode());
            model.addAttribute("errorMessage", "L'ajout de la note a échoué.");
            return reloadDetailWithForm(id, noteForm, model, redirectAttributes);
        }
    }

    // Le formulaire d'ajout de note vit sur la page détail patient : en cas d'erreur, il faut
    // recharger patient + notePage pour que la vue reste complète, sans perdre la saisie invalide.
    private String reloadDetailWithForm(Long id, NoteForm noteForm, Model model, RedirectAttributes redirectAttributes) {
        try {
            PatientDto patient = patientGatewayClient.findById(id);
            model.addAttribute("patient", patient);
            model.addAttribute("notePage", noteGatewayClient.findByPatientId(id, 0));
            model.addAttribute("noteForm", noteForm);
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
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Doublon patient refusé par le back : {}", e.getStatusCode());
            model.addAttribute("patientId", id);
            model.addAttribute("errorMessage", "Un patient identique existe déjà.");
            return "patients/form";
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