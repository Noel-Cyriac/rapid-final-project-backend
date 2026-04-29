package com.nc.FinalProject.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardResponse {

    private Long todayUploads;
    private Long todayDownloads;
    private Long todayDeletes;

    private Long totalUploads;
    private Long totalDownloads;

    private Long usedStorage;

    private List<FileResponse> latestUploads;

    private List<FileResponse> latestDownloads;

    private List<FileResponse> recentlyOpened;
}