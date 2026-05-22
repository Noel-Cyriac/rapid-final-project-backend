package com.nc.FinalProject.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShareResponse {

    private Long id;

    private String fileName;
    private String type; // FILE or BUNDLE

    private LocalDateTime expiresAt;
    private Integer maxUses;

    private Boolean active;
    private String message;
    private LocalDateTime sharedAt;

    // recipient details
    private List<RecipientResponse> recipients;
}