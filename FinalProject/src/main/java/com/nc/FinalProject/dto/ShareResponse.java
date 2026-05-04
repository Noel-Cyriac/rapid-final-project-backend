package com.nc.FinalProject.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ShareResponse {

    private Long id;
    private String token;
    private String email;
    private LocalDateTime expiresAt;
    private int maxUses;
    private int usedCount;
    private int openCount;
    private boolean active;
    private String fileName;
}