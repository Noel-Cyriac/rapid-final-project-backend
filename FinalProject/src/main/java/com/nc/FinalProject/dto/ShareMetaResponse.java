package com.nc.FinalProject.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ShareMetaResponse {

    private String fileName;
    private String fileType;

    private boolean requiresPassword;
    private boolean canDownload;
    private boolean canView;

    private String message;
}