package com.nc.FinalProject.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkFolderActionRequest {

    private List<Long> ids;
}