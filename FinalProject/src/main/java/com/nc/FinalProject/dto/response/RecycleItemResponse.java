package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
public class RecycleItemResponse {

    private String type; // FILE or FOLDER

    private Long id;
    private String name;

    private Long size; // null for folder
    private String fileType; // null for folder

    private LocalDateTime deletedAt;

    private Long parentFolderId; // optional
}
