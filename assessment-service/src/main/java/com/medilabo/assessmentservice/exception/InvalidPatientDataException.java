package com.medilabo.assessmentservice.exception;

public class InvalidPatientDataException extends RuntimeException {

    public InvalidPatientDataException(String message) {
        super(message);
    }
}