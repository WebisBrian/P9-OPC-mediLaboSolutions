package com.medilabo.frontendservice.controller;

import com.medilabo.frontendservice.gateway.PatientGatewayClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;

@Controller
public class LoginController {

    private final PatientGatewayClient patientGatewayClient;

    public LoginController(PatientGatewayClient patientGatewayClient) {
        this.patientGatewayClient = patientGatewayClient;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model) {
        try {
            patientGatewayClient.verifyCredentials(username, password);
            session.setAttribute("username", username);
            session.setAttribute("password", password);
            return "redirect:/patients";
        } catch (HttpClientErrorException.Unauthorized e) {
            model.addAttribute("error", "Identifiants incorrects.");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

}