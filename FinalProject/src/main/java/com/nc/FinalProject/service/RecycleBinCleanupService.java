package com.nc.FinalProject.service;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Folder;
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
    private final FolderRepository folderRepository;
    private final FileService fileService;

    @Scheduled(cron = "0 0 * * * *")
    public void autoDeleteExpiredItems() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        // delete expired folders first
        List<Folder> expiredFolders = folderRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);

        for (Folder folder : expiredFolders) {
            try {
                // restore-safe permanent delete
                deleteFolderPermanently(folder);
                log.info("Auto deleted folder: {}", folder.getName());
            } catch (Exception e) {
                log.error("Failed to delete folder {}", folder.getName(), e);
            }
        }

        // delete orphan files
        List<FileEntity> expiredFiles = fileRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);

        for (FileEntity file : expiredFiles) {
            // skip files already removed by folder deletion
            if (file.getFolder() != null && file.getFolder().isDeleted()) {
                continue;
            }

            try {
                fileService.autoDeleteFile(file);
                log.info("Auto deleted file: {}", file.getFileName());
            } catch (Exception e) {
                log.error("Failed to delete file {}", file.getFileName(), e);
            }
        }
    }

    private void deleteFolderPermanently(Folder folder) {
        List<FileEntity> files = fileRepository.findByOwnerAndFolder(folder.getOwner(), folder);

        for (FileEntity file : files) {
            fileService.autoDeleteFile(file);
        }

        List<Folder> children = folderRepository.findByOwnerAndParent(folder.getOwner(), folder);

        for (Folder child : children) {
            deleteFolderPermanently(child);
        }

        folderRepository.delete(folder);
    }
}