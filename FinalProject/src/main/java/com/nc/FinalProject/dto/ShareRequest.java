package com.nc.FinalProject.dto;

import lombok.Data;

@Data
public class ShareRequest {
    private String recipientEmail;
    private Long fileId;
    private int expireHours;
    private String message;
}