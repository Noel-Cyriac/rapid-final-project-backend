package com.nc.FinalProject.exception;

public class MaxUsesExceededException extends RuntimeException {
    public MaxUsesExceededException(String message) {
        super(message);
    }
}
