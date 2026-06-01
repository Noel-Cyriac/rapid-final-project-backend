package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FolderContentResponse {

    private List<FolderResponse> folders;

    private List<FileResponse> files;
}