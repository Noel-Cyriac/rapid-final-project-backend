package com.nc.FinalProject.exception;

import java.util.List;

public class SharedFileDeleteException extends RuntimeException {

    private final List<String> sharedFiles;

    public SharedFileDeleteException(
            String message,
            List<String> sharedFiles
    ) {
        super(message);
        this.sharedFiles = sharedFiles;
    }

    public List<String> getSharedFiles() {
        return sharedFiles;
    }
}