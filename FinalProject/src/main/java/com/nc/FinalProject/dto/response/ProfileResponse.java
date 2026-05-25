package com.nc.FinalProject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ProfileResponse {

    private String firstName;
    private String lastName;
    private String email;
    private LocalDate dob;
    private String profilePicture;
}