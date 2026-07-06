package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.model.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NoteRepository extends MongoRepository<Note, String> {

    // List<Note> -> Page<Note> : pagination + tri portés par le Pageable, plus par la méthode dérivée.
    Page<Note> findByPatientId(Long patientId, Pageable pageable);
}