package com.medilabo.patientservice.dto;

import com.medilabo.patientservice.model.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientRequest {

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[\\p{L} '-]+", message = "must contain only letters, spaces, hyphens or apostrophes")
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[\\p{L} '-]+", message = "must contain only letters, spaces, hyphens or apostrophes")
    private String lastName;

    @NotNull
    @Past
    private LocalDate birthDate;

    @NotNull
    private Gender gender;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    @Pattern(regexp = "[0-9 +().-]*", message = "must contain only digits, spaces or + ( ) . -")
    private String phone;
}