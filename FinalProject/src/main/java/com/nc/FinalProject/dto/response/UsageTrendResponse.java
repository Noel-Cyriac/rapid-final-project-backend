package com.nc.FinalProject.dto.response;

import java.time.LocalDate;

public record UsageTrendResponse(
        LocalDate date,
        Long uploadSize,
        Long downloadSize
) {
}