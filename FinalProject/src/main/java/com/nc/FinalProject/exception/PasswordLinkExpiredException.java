package com.nc.FinalProject.exception;

public class PasswordLinkExpiredException extends RuntimeException {
    public PasswordLinkExpiredException(String message) {
        super(message);
    }
}