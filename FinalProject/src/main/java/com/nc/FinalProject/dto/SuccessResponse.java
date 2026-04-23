package com.nc.FinalProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuccessResponse {
    private String message;
    private Object data; // Optional, can hold extra info like email, id, etc.
}
