package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FileDownloadResponse {
    private Long id;
    private String fileName;
    private Long size;
    private LocalDateTime lastDownloadedAt;
    private boolean starred;
}
