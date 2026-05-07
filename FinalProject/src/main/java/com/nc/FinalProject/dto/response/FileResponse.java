package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FileResponse {

    private Long id;
    private String fileName;
    private String storedName;
    private String fileType;
    private Long size;
    private String filePath;
    private LocalDateTime uploadedAt;
}