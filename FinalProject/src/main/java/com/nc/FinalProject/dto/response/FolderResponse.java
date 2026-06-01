package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FolderResponse {

    private Long id;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
}