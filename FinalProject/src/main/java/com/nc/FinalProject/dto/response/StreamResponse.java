package com.nc.FinalProject.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamResponse {
    private String streamToken;
}