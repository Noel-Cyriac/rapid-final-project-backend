package com.nc.FinalProject.dto.response;

import java.util.List;

public class DeleteWarningResponse {

    private String message;
    private List<String> sharedFiles;

    public DeleteWarningResponse(String message, List<String> sharedFiles) {
        this.message = message;
        this.sharedFiles = sharedFiles;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getSharedFiles() {
        return sharedFiles;
    }
}