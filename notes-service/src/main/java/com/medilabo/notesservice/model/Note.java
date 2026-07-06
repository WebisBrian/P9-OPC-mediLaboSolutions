package com.medilabo.notesservice.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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

    // Immuable après création : pas de setter, valorisé une seule fois par l'auditing Mongo.
    @CreatedDate
    @Setter(AccessLevel.NONE)
    private Instant createdAt;
}