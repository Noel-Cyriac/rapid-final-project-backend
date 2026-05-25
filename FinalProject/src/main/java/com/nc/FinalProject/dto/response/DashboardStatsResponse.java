package com.nc.FinalProject.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsResponse {

    private Long todayUploads;
    private Long todayDownloads;
    private Long todayDeletes;

    private Long totalUploads;
    private Long totalDownloads;

    private Long usedStorage;
}