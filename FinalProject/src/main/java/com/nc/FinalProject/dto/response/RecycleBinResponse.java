package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RecycleBinResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long size;
    private LocalDateTime deletedAt;
}
