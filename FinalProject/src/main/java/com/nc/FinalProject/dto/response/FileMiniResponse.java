package com.nc.FinalProject.dto.response;

public record FileMiniResponse(
        Long id,
        String fileName,
        String fileType
) {}