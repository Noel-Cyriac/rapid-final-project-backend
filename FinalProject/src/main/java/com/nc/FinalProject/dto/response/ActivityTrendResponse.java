package com.nc.FinalProject.dto.response;

import java.time.LocalDate;

public record ActivityTrendResponse(
        LocalDate date,
        Long uploads,
        Long downloads,
        Long shares
) {
}