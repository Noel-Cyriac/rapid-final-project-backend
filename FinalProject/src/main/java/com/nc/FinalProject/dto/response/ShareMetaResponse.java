package com.nc.FinalProject.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
    private List<FileMiniResponse> files;
}