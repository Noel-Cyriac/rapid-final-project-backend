package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.SharedFileDeleteException;
import com.nc.FinalProject.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final FileActivityRepository activityRepository;
    private final FolderRepository folderRepository;
    private final ShareRepository shareRepository;
    private final StreamTokenRepository streamTokenRepository;
    private final NotificationService notificationService;

    @Value("${file.storage.location}")
    private String uploadDir;

    // ======================
    // MULTI UPLOAD
    // ======================
    public List<FileResponse> uploadFiles(
            MultipartFile[] files,
            Users user
    ) {
        try {

            Files.createDirectories(Paths.get(uploadDir));

            List<FileResponse> list =
                    new ArrayList<>();

            int uploadedCount = 0;
            long totalSize = 0;

            for (MultipartFile file : files) {

                if (file.isEmpty()) continue;

                String finalName =
                        generateUniqueName(
                                file.getOriginalFilename(),
                                user,
                                null
                        );

                String storedName =
                        System.currentTimeMillis()
                                + "_"
                                + finalName;

                Path path =
                        Paths.get(uploadDir, storedName);

                file.transferTo(path);

                FileEntity saved =
                        fileRepository.save(
                                FileEntity.builder()
                                        .fileName(finalName)
                                        .storedName(storedName)
                                        .fileType(file.getContentType())
                                        .size(file.getSize())
                                        .filePath(path.toString())
                                        .uploadedAt(LocalDateTime.now())
                                        .owner(user)
                                        .deleted(false)
                                        .downloadCount(0)
                                        .build()
                        );

                track(
                        user,
                        saved,
                        "UPLOAD",
                        file.getSize()
                );

                list.add(
                        mapToResponse(saved)
                );

                uploadedCount++;
                totalSize += file.getSize();
            }

            // ADD HERE
            if (uploadedCount > 0) {

                notificationService.create(
                        user,
                        "Upload Completed",
                        uploadedCount +
                                " file(s) uploaded successfully",
                        NotificationType.UPLOAD
                );
            }

            return list;

        } catch (Exception e) {
            throw new RuntimeException(
                    e.getMessage()
            );
        }
    }

    // ======================
    // LIST FILES
    // ======================
    public PagedResponse<FileResponse> listFiles(Users user, Pageable pageable) {

        Page<FileEntity> page =
                fileRepository.findByOwnerAndDeletedFalse(user, pageable);

        Page<FileResponse> dtoPage =
                page.map(this::mapToResponse);

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }

    // ======================
    // RECYCLE BIN
    // ======================
    public PagedResponse<RecycleBinResponse> recycleBin(Users user, Pageable pageable) {

        Page<FileEntity> page =
                fileRepository.findByOwnerAndDeletedTrue(user, pageable);

        Page<RecycleBinResponse> dtoPage =
                page.map(f -> new RecycleBinResponse(
                        f.getId(),
                        f.getFileName(),
                        f.getFileType(),
                        f.getSize(),
                        f.getDeletedAt()
                ));

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }

    // ======================
    // DOWNLOAD
    // ======================
    public Path getFilePath(Long id, Users user) {

        FileEntity file =
                fileRepository.findByIdAndOwner(id, user)
                        .orElseThrow();

        file.setDownloadCount(
                file.getDownloadCount() + 1
        );

        file.setLastDownloadedAt(
                LocalDateTime.now()
        );

        fileRepository.save(file);

        track(
                user,
                file,
                "DOWNLOAD",
                file.getSize()
        );

        // ADD HERE
        notificationService.create(
                user,
                "Download Completed",
                file.getFileName() + " downloaded",
                NotificationType.DOWNLOAD
        );

        return Paths.get(
                file.getFilePath()
        );
    }

    public void downloadMultiple(
            List<Long> ids,
            Users user,
            OutputStream outputStream
    ) throws IOException {

        List<FileEntity> files =
                fileRepository.findAllById(ids);

        ZipOutputStream zos =
                new ZipOutputStream(outputStream);

        byte[] buffer = new byte[8192];

        int downloadedCount = 0;

        for (FileEntity file : files) {

            if (!file.getOwner()
                    .getId()
                    .equals(user.getId())) {
                continue;
            }

            Path path =
                    Paths.get(file.getFilePath());

            zos.putNextEntry(
                    new ZipEntry(file.getFileName())
            );

            try (
                    InputStream fis =
                            Files.newInputStream(path)
            ) {

                int len;

                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }

            zos.closeEntry();

            // update stats
            file.setDownloadCount(
                    file.getDownloadCount() + 1
            );

            file.setLastDownloadedAt(
                    LocalDateTime.now()
            );

            track(
                    user,
                    file,
                    "DOWNLOAD",
                    file.getSize()
            );

            downloadedCount++;
        }

        zos.finish();
        zos.close();

        fileRepository.saveAll(files);

        // ADD HERE
        if (downloadedCount > 0) {

            notificationService.create(
                    user,
                    "Download Completed",
                    downloadedCount +
                            " file(s) downloaded",
                    NotificationType.DOWNLOAD
            );
        }
    }

    // ======================
    // DELETE
    // ======================
    public void deleteFiles(List<Long> ids, Users user, boolean force) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        List<String> shared = new ArrayList<>();

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            boolean sharedExists =
                    shareRepository.existsByFileAndActiveTrue(file)
                            || shareRepository.existsByFilesContainsAndActiveTrue(file);

            if (sharedExists && !force) {
                shared.add(file.getFileName());
            }
        }

        if (!shared.isEmpty()) {
            throw new SharedFileDeleteException(
                    "Files are currently shared",
                    shared
            );
        }

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            // revoke shares
            List<Share> shares =
                    shareRepository.findAllByFileOrFilesContains(file, file);

            shares.forEach(s -> s.setActive(false));
            shareRepository.saveAll(shares);

            file.setDeleted(true);
            file.setDeletedAt(LocalDateTime.now());

            file.setStarred(false);
            file.setStarredAt(null);

            fileRepository.save(file);

            track(user, file, "DELETE", 0L);
        }
    }

    // ======================
    // RESTORE
    // ======================
    public void restoreFiles(List<Long> ids, Users user) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            file.setDeleted(false);
            file.setDeletedAt(null);

            fileRepository.save(file);

            track(user, file, "RESTORE", 0L);
        }
    }

    @Transactional
    public void deletePermanent(List<Long> ids, Users user) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        for (FileEntity file : files) {

            // ensure ownership
            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            Long fileId = file.getId();

            // 1. delete activity rows
            activityRepository.deleteByFile_Id(fileId);

            // 2. handle shares referencing this file
            List<Share> relatedShares =
                    shareRepository.findAllByFileOrFilesContains(file, file);

            for (Share share : relatedShares) {

                // remove single-file relation
                if (share.getFile() != null &&
                        share.getFile().getId().equals(fileId)) {

                    share.setFile(null);
                }

                // remove from bundle
                if (share.getFiles() != null) {

                    share.getFiles().removeIf(f ->
                            f.getId().equals(fileId));
                }

                // determine if share is now empty
                boolean emptySingle =
                        share.getFile() == null;

                boolean emptyBundle =
                        share.getFiles() == null ||
                                share.getFiles().isEmpty();

                // delete empty share
                if (emptySingle && emptyBundle) {
                    streamTokenRepository.deleteByRecipient_Share_Id(share.getId());
                    shareRepository.delete(share);
                } else {

                    // otherwise update modified share
                    shareRepository.save(share);
                }
            }

            // 3. delete physical file
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
            } catch (Exception ignored) {
            }

            // 4. delete file record
            fileRepository.delete(file);
        }
    }

    // ======================
    // DASHBOARD
    // ======================
    public List<FileResponse> latestUploads(Users user) {

        return fileRepository
                .findTop4ByOwnerAndDeletedFalseOrderByUploadedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<FileDownloadResponse> latestDownloads(Users user) {

        return fileRepository
                .findTop4ByOwnerAndDeletedFalseAndLastDownloadedAtNotNullOrderByLastDownloadedAtDesc(user)
                .stream()
                .map(f -> new FileDownloadResponse(
                        f.getId(),
                        f.getFileName(),
                        f.getSize(),
                        f.getLastDownloadedAt(),
                        f.isStarred()
                ))
                .toList();
    }

    public List<RecentlyOpenedResponse> recentlyOpened(Users user) {

        return fileRepository
                .findTop4ByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(user)
                .stream()
                .map(f -> new RecentlyOpenedResponse(
                        f.getId(),
                        f.getFileName(),
                        f.getFileType(),
                        f.getSize(),
                        f.getLastOpenedAt(),
                        f.isStarred()
                ))
                .toList();
    }

    public DashboardStatsResponse dashboardStats(Users user) {

        LocalDateTime today =
                LocalDate.now().atStartOfDay();

        return DashboardStatsResponse.builder()
                .todayUploads(
                        activityRepository.todayCount(
                                user,
                                "UPLOAD",
                                today
                        )
                )
                .todayDownloads(
                        activityRepository.todayCount(
                                user,
                                "DOWNLOAD",
                                today
                        )
                )
                .todayDeletes(
                        activityRepository.todayCount(
                                user,
                                "DELETE",
                                today
                        )
                )
                .totalUploads(
                        activityRepository.countByAction(
                                user,
                                "UPLOAD"
                        )
                )
                .totalDownloads(
                        activityRepository.countByAction(
                                user,
                                "DOWNLOAD"
                        )
                )
                .usedStorage(
                        fileRepository.totalUsed(user)
                )
                .build();
    }

    public List<FileTypeStorageResponse> storageBreakdown(Users user) {

        Map<String, Long> grouped = new HashMap<>();

        List<Object[]> rows =
                fileRepository.storageBreakdown(user);

        for (Object[] r : rows) {

            String mime =
                    (String) r[0];

            Long size =
                    ((Number) r[1]).longValue();

            String category =
                    categorizeMime(mime);

            grouped.put(
                    category,
                    grouped.getOrDefault(category, 0L) + size
            );
        }

        return grouped.entrySet()
                .stream()
                .map(e -> new FileTypeStorageResponse(
                        e.getKey(),
                        e.getValue()
                ))
                .toList();
    }

    private String categorizeMime(String mime) {

        if (mime == null)
            return "Others";

        if (mime.startsWith("image/"))
            return "Images";

        if (mime.startsWith("video/"))
            return "Videos";

        if (mime.startsWith("audio/"))
            return "Audio";

        if (mime.contains("pdf")
                || mime.contains("document")
                || mime.contains("text")
                || mime.contains("sheet")
                || mime.contains("presentation"))
            return "Documents";

        return "Others";
    }

    public List<ActivityTrendResponse> activityTrend(
            Users user,
            int days
    ) {

        LocalDateTime start =
                LocalDateTime.now().minusDays(days);

        Map<LocalDate, Long> uploadMap =
                toMap(
                        activityRepository.countPerDay(
                                user,
                                "UPLOAD",
                                start
                        )
                );

        Map<LocalDate, Long> downloadMap =
                toMap(
                        activityRepository.countPerDay(
                                user,
                                "DOWNLOAD",
                                start
                        )
                );

        Map<LocalDate, Long> shareMap =
                toMap(
                        shareRepository.sharesPerDay(
                                user,
                                start
                        )
                );

        List<ActivityTrendResponse> result =
                new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {

            LocalDate date =
                    LocalDate.now().minusDays(i);

            result.add(
                    new ActivityTrendResponse(
                            date,
                            uploadMap.getOrDefault(date, 0L),
                            downloadMap.getOrDefault(date, 0L),
                            shareMap.getOrDefault(date, 0L)
                    )
            );
        }

        return result;
    }

    public List<UsageTrendResponse> transferUsage(
            Users user,
            int days
    ) {

        LocalDateTime start =
                LocalDateTime.now().minusDays(days);

        Map<LocalDate, Long> uploadMap =
                toMap(
                        activityRepository.usagePerDay(
                                user,
                                "UPLOAD",
                                start
                        )
                );

        Map<LocalDate, Long> downloadMap =
                toMap(
                        activityRepository.usagePerDay(
                                user,
                                "DOWNLOAD",
                                start
                        )
                );

        List<UsageTrendResponse> result =
                new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {

            LocalDate date =
                    LocalDate.now().minusDays(i);

            result.add(
                    new UsageTrendResponse(
                            date,
                            uploadMap.getOrDefault(date, 0L),
                            downloadMap.getOrDefault(date, 0L)
                    )
            );
        }

        return result;
    }

    private Map<LocalDate, Long> toMap(List<Object[]> rows) {

        return rows.stream()
                .collect(Collectors.toMap(
                        r -> ((java.sql.Date) r[0]).toLocalDate(),
                        r -> ((Number) r[1]).longValue()
                ));
    }

    public PagedResponse<FileResponse> getUploadedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<FileEntity> page =
                fileRepository.findByOwnerAndDeletedFalseOrderByUploadedAtDesc(
                        user,
                        pageable
                );

        Page<FileResponse> dtoPage = page.map(this::mapToResponse);

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }

    public PagedResponse<FileDownloadResponse> getDownloadedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<FileActivity> page =
                activityRepository
                        .findByUserAndActionAndFile_DeletedFalseOrderByCreatedAtDesc(
                                user,
                                "DOWNLOAD",
                                pageable
                        );

        Page<FileDownloadResponse> dtoPage =
                page.map(a -> new FileDownloadResponse(
                        a.getFile().getId(),
                        a.getFile().getFileName(),
                        a.getFile().getSize(),
                        a.getCreatedAt(),a.getFile().isStarred()
                ));

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }

    public PagedResponse<RecentlyOpenedResponse> getRecentlyOpenedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<FileEntity> page =
                fileRepository
                        .findByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(
                                user,
                                pageable
                        );

        Page<RecentlyOpenedResponse> dtoPage =
                page.map(f -> new RecentlyOpenedResponse(
                        f.getId(),
                        f.getFileName(),
                        f.getFileType(),
                        f.getSize(),
                        f.getLastOpenedAt(),
                        f.isStarred()
                ));

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }
    // ======================
    // TRACKING
    // ======================
    private void track(Users user, FileEntity file, String action, Long size) {

        activityRepository.save(
                FileActivity.builder()
                        .user(user)
                        .file(file)
                        .action(action)
                        .size(size)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    // ======================
    // UNIQUE FILE NAME
    // ======================
    private String generateUniqueName(
            String original,
            Users user,
            Folder folder
    ) {

        String name = original;
        int count = 1;

        while (fileRepository
                .existsByOwnerAndFolderAndFileNameAndDeletedFalse(
                        user,
                        folder,
                        name
                )) {

            int dot = original.lastIndexOf(".");

            if (dot == -1) {
                name = original + "(" + count + ")";
            } else {
                String base = original.substring(0, dot);
                String ext = original.substring(dot);
                name = base + "(" + count + ")" + ext;
            }

            count++;
        }

        return name;
    }

    // ======================
    // ENTITY -> DTO
    // ======================
    private FileResponse mapToResponse(FileEntity f) {

        return new FileResponse(
                f.getId(),
                f.getFileName(),
                f.getStoredName(),
                f.getFileType(),
                f.getSize(),
                f.getFilePath(),
                f.getUploadedAt(),
                f.isStarred()
        );
    }

    public FileViewResponse viewFile(Long id, Users user) {

        FileEntity file = fileRepository.findByIdAndOwner(id, user)
                .orElseThrow();

        file.setLastOpenedAt(LocalDateTime.now());

        fileRepository.save(file);

        return new FileViewResponse(
                file.getFilePath(),
                file.getFileType()
        );
    }

    public void starFiles(List<Long> ids, Users user) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            file.setStarred(true);
            file.setStarredAt(LocalDateTime.now());
        }

        fileRepository.saveAll(files);
    }

    public void unstarFiles(List<Long> ids, Users user) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            file.setStarred(false);
            file.setStarredAt(null);
        }

        fileRepository.saveAll(files);
    }

    public PagedResponse<FileResponse> getStarredFiles(Users user, Pageable pageable) {

        Page<FileEntity> page =
                fileRepository
                        .findByOwnerAndStarredTrueAndDeletedFalse(
                                user,
                                pageable
                        );

        return new PagedResponse<>(
                page.map(this::mapToResponse).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Transactional
    public String createStreamToken(Long fileId, Users user) {

        FileEntity file = fileRepository
                .findByIdAndOwner(fileId, user)
                .orElseThrow();

        file.setLastOpenedAt(LocalDateTime.now());

        StreamToken token = StreamToken.builder()
                .token(UUID.randomUUID().toString())
                .file(file)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build();

        streamTokenRepository.save(token);

        return token.getToken();
    }
}