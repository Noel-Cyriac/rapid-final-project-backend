package com.nc.FinalProject.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipientResponse {

    private Long id;

    private String email;

    // unique per user
    private String token;

    // analytics
    private Boolean opened;
    private Integer openCount;
    private Integer usedCount;

    private LocalDateTime lastOpenedAt;

    private Boolean active;
}