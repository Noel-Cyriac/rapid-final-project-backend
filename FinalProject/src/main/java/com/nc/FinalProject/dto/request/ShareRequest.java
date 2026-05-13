package com.nc.FinalProject.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ShareRequest {

    private Long fileId;

    private List<Long> fileIds;

    // ✅ multiple recipients
    private List<String> emails;

    private Integer expireHours;
    private Integer maxUses;
    private String password;
    private Boolean canDownload;
    private Boolean canView;
    private String message;
}