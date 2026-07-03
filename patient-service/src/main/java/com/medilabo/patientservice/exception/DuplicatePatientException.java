package com.medilabo.patientservice.exception;

public class DuplicatePatientException extends RuntimeException {

    public DuplicatePatientException() {
        super("A patient with the same name, birth date and phone already exists");
    }
}
