package com.nc.FinalProject.exception;

public class PasswordAlreadyViewedException extends RuntimeException {
    public PasswordAlreadyViewedException(String message) {
        super(message);
    }
}