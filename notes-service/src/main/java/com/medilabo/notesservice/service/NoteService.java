package com.medilabo.notesservice.service;

import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.dto.NoteResponse;
import com.medilabo.notesservice.mapper.NoteMapper;
import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;

    public NoteService(NoteRepository noteRepository, NoteMapper noteMapper) {
        this.noteRepository = noteRepository;
        this.noteMapper = noteMapper;
    }

    // Page vide = patient sans historique, cas normal (pas une erreur, pas de 404).
    public Page<NoteResponse> findByPatientId(Long patientId, Pageable pageable) {
        return noteRepository.findByPatientId(patientId, pageable)
                .map(noteMapper::toResponse);
    }

    public NoteResponse create(NoteRequest request) {
        Note note = noteMapper.toEntity(request);
        NoteResponse response = noteMapper.toResponse(noteRepository.save(note));
        log.info("Note created with id {}", response.getId());
        return response;
    }
}