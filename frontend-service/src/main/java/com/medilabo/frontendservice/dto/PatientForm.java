package com.medilabo.frontendservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class PatientForm {

    @NotBlank(message = "Le prénom est obligatoire.")
    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères.")
    @Pattern(regexp = "[\\p{L} '-]+", message = "Le prénom ne doit contenir que des lettres, espaces, tirets ou apostrophes.")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire.")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères.")
    @Pattern(regexp = "[\\p{L} '-]+", message = "Le nom ne doit contenir que des lettres, espaces, tirets ou apostrophes.")
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire.")
    @Past(message = "La date de naissance doit être dans le passé.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    @NotBlank(message = "Le genre est obligatoire.")
    @Pattern(regexp = "M|F", message = "Le genre doit être M ou F.")
    private String gender;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères.")
    private String address;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
    @Pattern(regexp = "[0-9 +().-]*", message = "Le téléphone ne doit contenir que des chiffres, espaces ou + ( ) . -")
    private String phone;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}