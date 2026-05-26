package com.nc.FinalProject.service;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Share;
import com.nc.FinalProject.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinCleanupService {

    private final FileRepository fileRepository;
    private final FileActivityRepository activityRepository;
    private final ShareRepository shareRepository;
    private final StreamTokenRepository streamTokenRepository;

    @Scheduled(cron = "0 0 * * * *")
    public void autoDeleteExpiredFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<FileEntity> expiredFiles = fileRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);

        for (FileEntity file : expiredFiles) {
            try {
                Long fileId = file.getId();

                // delete activities
                activityRepository.deleteByFile_Id(fileId);

                // remove shares
                List<Share> relatedShares = shareRepository.findAllByFileOrFilesContains(file, file);

                for (Share share : relatedShares) {
                    if (share.getFile() != null && share.getFile().getId().equals(fileId)) {
                        share.setFile(null);
                    }

                    if (share.getFiles() != null) {
                        share.getFiles().removeIf(f -> f.getId().equals(fileId));
                    }

                    boolean emptySingle = share.getFile() == null;
                    boolean emptyBundle = share.getFiles() == null || share.getFiles().isEmpty();

                    if (emptySingle && emptyBundle) {
                        streamTokenRepository.deleteByRecipient_Share_Id(share.getId());
                        shareRepository.delete(share);
                    } else {
                        shareRepository.save(share);
                    }
                }

                // delete physical file
                Files.deleteIfExists(Paths.get(file.getFilePath()));

                // delete db record
                fileRepository.delete(file);

                log.info("Auto deleted recycle bin file: {}", file.getFileName());
            } catch (Exception e) {
                log.error("Failed to auto delete file {}", file.getFileName(), e);
            }
        }
    }
}