package com.medilabo.notesservice.config;

import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class NoteSeeder implements CommandLineRunner {

    private final NoteRepository noteRepository;

    public NoteSeeder(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Override
    public void run(String... args) {
        // Mongo n'a pas d'équivalent du data.sql + ON CONFLICT DO NOTHING utilisé côté patient-service :
        // count() > 0 sert ici de garde d'idempotence pour ne pas dupliquer le seed à chaque redémarrage.
        if (noteRepository.count() > 0) {
            log.info("Note seeding skipped: notes already present");
            return;
        }

        List<Note> notes = List.of(
                note(1L, "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé"),
                note(2L, "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement"),
                note(2L, "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale"),
                note(3L, "Le patient déclare qu'il fume depuis peu"),
                note(3L, "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé"),
                note(4L, "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments"),
                note(4L, "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps"),
                note(4L, "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé"),
                note(4L, "Taille, Poids, Cholestérol, Vertige et Réaction")
        );

        noteRepository.saveAll(notes);
        log.info("Seeded {} notes", notes.size());
    }

    private Note note(Long patientId, String text) {
        Note note = new Note();
        note.setPatientId(patientId);
        note.setNote(text);
        return note;
    }
}