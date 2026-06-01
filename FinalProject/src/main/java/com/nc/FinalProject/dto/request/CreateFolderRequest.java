package com.nc.FinalProject.dto.request;

import lombok.Data;

@Data
public class CreateFolderRequest {

    private String name;

    private Long parentId;
}