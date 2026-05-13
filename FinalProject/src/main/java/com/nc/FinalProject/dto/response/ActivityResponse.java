package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ActivityResponse {

    private Long fileId;
    private String fileName;
    private String action;
    private LocalDateTime createdAt;
}