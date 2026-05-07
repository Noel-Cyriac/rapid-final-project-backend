package com.nc.FinalProject.dto.request;

import lombok.Data;

@Data
public class ShareRequest {

    private String email;
    private Integer expireHours;
    private Integer maxUses;
    private String password;
    private Boolean canDownload;
    private Boolean canView;
    private String message;
}