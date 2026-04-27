package com.nc.FinalProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String firstName;
    private String lastName;
    private String email;
}