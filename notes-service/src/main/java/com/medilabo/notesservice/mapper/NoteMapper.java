package com.medilabo.notesservice.mapper;

import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.dto.NoteResponse;
import com.medilabo.notesservice.model.Note;
import org.springframework.stereotype.Component;

@Component
public class NoteMapper {

    public Note toEntity(NoteRequest request) {
        Note note = new Note();
        note.setPatientId(request.getPatientId());
        note.setNote(request.getNote());
        return note;
    }

    public NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getPatientId(),
                note.getNote()
        );
    }
}