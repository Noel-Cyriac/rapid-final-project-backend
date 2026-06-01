package com.nc.FinalProject.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MoveFileRequest {
    private List<Long> fileIds;
    private Long targetFolderId; // null = root
}
