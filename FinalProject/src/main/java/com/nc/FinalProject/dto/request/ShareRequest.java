package com.nc.FinalProject.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ShareRequest {

    private Long fileId;

    private List<Long> fileIds;

    @NotEmpty(message = "At least one recipient email is required")
    private List<
            @Email(message = "Invalid email format")
            @NotBlank(message = "Email cannot be blank")
                    String
            > emails;

    @NotNull(message = "Expire hours is required")
    @Min(value = 1, message = "Minimum expiry is 1 hour")
    @Max(value = 720, message = "Maximum expiry is 720 hours")
    private Integer expireHours;

    @NotNull(message = "Max uses is required")
    @Min(value = 1, message = "Max uses must be at least 1")
    private Integer maxUses;

    @Pattern(
            regexp = "^(\\S{6,})$",
            message = "Password must be at least 6 characters with no spaces"
    )
    private String password;

    private Boolean canDownload = true;

    private Boolean canView = true;

    @Size(max = 500, message = "Message too long")
    private String message;
}