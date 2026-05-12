package com.nc.FinalProject.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ShareResponse {

    private Long id;
    private String token;
    private List<String> emails;
    private LocalDateTime expiresAt;
    private int maxUses;
    private int usedCount;
    private int openCount;
    private boolean active;
    private String fileName;
    private LocalDateTime sharedAt;
    private LocalDateTime lastOpenedAt;
    private String message;
    private String type; // FILE or BUNDLE
}