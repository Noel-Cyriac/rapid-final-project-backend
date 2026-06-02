package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeResponse {

    private Long id;
    private String name;
    private List<FolderTreeResponse> children;
}
