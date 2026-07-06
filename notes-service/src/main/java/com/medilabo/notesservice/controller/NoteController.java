package com.medilabo.notesservice.controller;

import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.dto.NoteResponse;
import com.medilabo.notesservice.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ResponseEntity<List<NoteResponse>> findByPatientId(@RequestParam Long patientId) {
        return ResponseEntity.ok(noteService.findByPatientId(patientId));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteRequest request) {
        NoteResponse created = noteService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .queryParam("patientId", created.getPatientId())
                .build()
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}