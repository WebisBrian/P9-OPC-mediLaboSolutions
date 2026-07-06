package com.medilabo.frontendservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteForm {

    @NotBlank(message = "La note ne peut pas être vide.")
    @Size(max = 5000, message = "La note ne doit pas dépasser 5000 caractères.")
    private String note;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}