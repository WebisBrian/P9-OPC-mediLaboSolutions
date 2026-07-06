package com.medilabo.notesservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    private String id;

    // Seul lien vers le patient (stocké en base SQL dans patient-service) : aucune donnée patient dupliquée ici.
    private Long patientId;

    private String note;
}