package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
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
    public List<FileResponse> uploadFiles(MultipartFile[] files, Long folderId, Users user) throws IOException {
        final long MAX_TOTAL_SIZE = 100L * 1024 * 1024; // 100MB
        final long MAX_USER_STORAGE = 1024L * 1024 * 1024; // 1GB

        Files.createDirectories(Paths.get(uploadDir));
        List<FileResponse> list = new ArrayList<>();
        int uploadedCount = 0;

        // optional folder lookup
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwner(folderId, user)
                    .orElseThrow(() -> new FileUploadException("Folder not found"));
        }

        long currentUsage = fileRepository.getUsedStorage(user);
        long requestSize = Arrays.stream(files)
                .filter(file -> !file.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();

        // storage validation
        if (currentUsage + requestSize > MAX_USER_STORAGE) {
            throw new FileUploadException("Storage limit exceeded (1GB per user)");
        }

        // request validation
        if (requestSize > MAX_TOTAL_SIZE) {
            throw new FileUploadException("Total upload size cannot exceed 100MB");
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;

            // unique name INSIDE current folder
            String finalName = generateUniqueName(originalName, user, folder);
            String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + finalName;
            Path path = Paths.get(uploadDir, storedName);

            file.transferTo(path);

            FileEntity saved = fileRepository.save(
                    FileEntity.builder()
                            .fileName(finalName)
                            .storedName(storedName)
                            .fileType(file.getContentType())
                            .size(file.getSize())
                            .filePath(path.toString())
                            .uploadedAt(LocalDateTime.now())
                            .owner(user)
                            .folder(folder) // NEW
                            .deleted(false)
                            .downloadCount(0)
                            .build()
            );

            // existing tracking logic
            track(user, saved, "UPLOAD", file.getSize());
            list.add(mapToResponse(saved));

            uploadedCount++;
        }

        // existing notification logic
        if (uploadedCount > 0) {
            String message = folder != null
                    ? uploadedCount + " file(s) uploaded to " + folder.getName()
                    : uploadedCount + " file(s) uploaded successfully";

            notificationService.create(user, "Upload Completed", message, NotificationType.UPLOAD);
        }

        return list;
    }

    @Transactional
    public List<FileResponse> uploadFolder(
            MultipartFile[] files,
            List<String> paths,
            Long parentId,
            Users user
    ) throws IOException {

        final long MAX_TOTAL_SIZE = 100L * 1024 * 1024; // 100MB
        final long MAX_USER_STORAGE = 1024L * 1024 * 1024; // 1GB

        Files.createDirectories(Paths.get(uploadDir));

        List<FileResponse> uploaded = new ArrayList<>();
        Map<String, Folder> folderCache = new HashMap<>();

        Folder initialParent = null;

        if (parentId != null) {
            initialParent = folderRepository.findByIdAndOwner(parentId, user)
                    .orElseThrow(() -> new FolderUploadException("Parent folder not found"));
        }

        // =========================
        // 1. PRE-CALCULATE SIZE
        // =========================
        long requestSize = 0;

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                requestSize += file.getSize();
            }
        }

        long currentUsage = fileRepository.getUsedStorage(user);

        // =========================
        // 2. VALIDATIONS
        // =========================
        if (requestSize > MAX_TOTAL_SIZE) {
            throw new FolderUploadException("Total upload size cannot exceed 100MB");
        }

        if (currentUsage + requestSize > MAX_USER_STORAGE) {
            throw new FolderUploadException("Storage limit exceeded (1GB per user)");
        }

        // =========================
        // 3. PROCESS UPLOAD (NO MERGING, FULL ISOLATION)
        // =========================
        for (int i = 0; i < files.length; i++) {

            MultipartFile file = files[i];
            if (file.isEmpty()) continue;

            String relativePath = paths.get(i);
            String[] parts = relativePath.split("/");

            Folder currentParent = initialParent;

            StringBuilder pathKey = new StringBuilder();

            for (int j = 0; j < parts.length - 1; j++) {

                String folderName = parts[j];

                pathKey.append("/").append(folderName);

                String key = (currentParent == null ? "root" : currentParent.getId())
                        + pathKey.toString();

                Folder folder = folderCache.get(key);

                if (folder == null) {

                    String finalName = folderName;
                    int count = 1;

                    while (folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(
                            user, currentParent, finalName)) {
                        finalName = folderName + " (" + count + ")";
                        count++;
                    }

                    Folder finalParent = currentParent;

                    folder = folderRepository.save(
                            Folder.builder()
                                    .name(finalName)
                                    .owner(user)
                                    .parent(finalParent)
                                    .createdAt(LocalDateTime.now())
                                    .deleted(false)
                                    .build()
                    );

                    folderCache.put(key, folder);
                }

                currentParent = folder;
            }
            // =========================
            // FILE CREATION
            // =========================
            String fileName = parts[parts.length - 1];
            String finalName = generateUniqueName(fileName, user, currentParent);

            String storedName =
                    System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + finalName;

            Path path = Paths.get(uploadDir, storedName);
            file.transferTo(path);

            FileEntity saved = fileRepository.save(
                    FileEntity.builder()
                            .fileName(finalName)
                            .storedName(storedName)
                            .fileType(file.getContentType())
                            .size(file.getSize())
                            .filePath(path.toString())
                            .uploadedAt(LocalDateTime.now())
                            .owner(user)
                            .folder(currentParent)
                            .deleted(false)
                            .downloadCount(0)
                            .build()
            );

            track(user, saved, "UPLOAD", file.getSize());
            uploaded.add(mapToResponse(saved));
        }

        // =========================
        // 4. NOTIFICATION
        // =========================
        notificationService.create(
                user,
                "Folder Upload Completed",
                uploaded.size() + " file(s) uploaded",
                NotificationType.UPLOAD
        );

        return uploaded;
    }
    // ======================
    // LIST FILES
    // ======================
    public PagedResponse<FileResponse> listFiles(
            Users user,
            Long folderId,
            Pageable pageable
    ) {

        Folder folder = null;

        if (folderId != null) {
            folder = folderRepository
                    .findByIdAndOwner(folderId, user)
                    .orElseThrow();
        }

        Page<FileEntity> page =
                fileRepository.findByOwnerAndFolderAndDeletedFalse(
                        user,
                        folder,
                        pageable
                );

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
    public PagedResponse<RecycleItemResponse> recycleBin(
            Users user,
            Pageable pageable
    ) {

        List<RecycleItemResponse> items = new ArrayList<>();

        // 1. FOLDERS (only top-level deleted folders)
        List<Folder> deletedFolders =
                folderRepository.findByOwnerAndDeletedTrue(user);

        for (Folder f : deletedFolders) {
            items.add(new RecycleItemResponse(
                    "FOLDER",
                    f.getId(),
                    f.getName(),
                    null,
                    null,
                    f.getDeletedAt(),
                    f.getParent() != null ? f.getParent().getId() : null
            ));
        }

        // 2. FILES (ONLY files NOT inside deleted folders)
        List<FileEntity> deletedFiles =
                fileRepository.findByOwnerAndDeletedTrue(user);

        for (FileEntity f : deletedFiles) {

            // IMPORTANT RULE:
            if (f.getFolder() != null && f.getFolder().isDeleted()) {
                continue; // skip → handled by folder
            }

            items.add(new RecycleItemResponse(
                    "FILE",
                    f.getId(),
                    f.getFileName(),
                    f.getSize(),
                    f.getFileType(),
                    f.getDeletedAt(),
                    f.getFolder() != null ? f.getFolder().getId() : null
            ));
        }

        // sort newest first
        items.sort((a, b) -> b.getDeletedAt().compareTo(a.getDeletedAt()));

        // manual pagination (since combined list)
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());

        List<RecycleItemResponse> pageContent =
                start >= items.size() ? List.of() : items.subList(start, end);

        return new PagedResponse<>(
                pageContent,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                items.size(),
                (int) Math.ceil((double) items.size() / pageable.getPageSize()),
                pageable.getPageNumber() == 0,
                end >= items.size()
        );
    }
    @Transactional
    public void moveFiles(List<Long> fileIds, Long targetFolderId, Users user) {

        // 1. Resolve target folder
        Folder targetFolder = null;

        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndOwner(targetFolderId, user)
                    .orElseThrow(() -> new MoveException("Target folder not found"));
        }

        // 2. Fetch files
        List<FileEntity> files = fileRepository.findAllById(fileIds);

        if (files.isEmpty()) {
            throw new MoveException("No files found");
        }

        // 3. Track validation results
        List<FileEntity> validFiles = new ArrayList<>();

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId())) {
                continue;
            }

            if (file.getDeleted()) {
                continue;
            }

            if (file.getFolder() != null && targetFolder != null
                    && file.getFolder().getId().equals(targetFolder.getId())) {
                continue;
            }

            if (file.getFolder() == null && targetFolder == null) {
                continue;
            }

            validFiles.add(file);
        }

        if (validFiles.isEmpty()) {
            throw new MoveException("No valid files to move");
        }

        // 4. Optional: duplicate name protection inside target folder
        Map<String, Integer> nameCount = new HashMap<>();

        for (FileEntity file : validFiles) {

            String originalName = file.getFileName();
            String newName = originalName;

            if (targetFolder != null) {

                boolean exists = fileRepository.existsByOwnerAndFolderAndFileNameAndDeletedFalse(
                        user,
                        targetFolder,
                        originalName
                );

                if (exists) {
                    int count = nameCount.getOrDefault(originalName, 1);

                    String base = originalName;
                    String ext = "";

                    int dotIndex = originalName.lastIndexOf('.');
                    if (dotIndex != -1) {
                        base = originalName.substring(0, dotIndex);
                        ext = originalName.substring(dotIndex);
                    }

                    newName = base + " (" + count + ")" + ext;
                    nameCount.put(originalName, count + 1);
                }
            }

            file.setFileName(newName);
            file.setFolder(targetFolder);
        }

        fileRepository.saveAll(validFiles);
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
            if (!file.getOwner().getId().equals(user.getId())) {
                continue;
            }
            permanentlyDeleteFile(file);
        }
    }

    private void permanentlyDeleteFile(FileEntity file) {
        Long fileId = file.getId();

        // 1. delete activity rows
        activityRepository.deleteByFile_Id(fileId);

        // 2. handle shares referencing this file
        List<Share> relatedShares = shareRepository.findAllByFileOrFilesContains(file, file);

        for (Share share : relatedShares) {
            // remove single-file relation
            if (share.getFile() != null && share.getFile().getId().equals(fileId)) {
                share.setFile(null);
            }

            // remove from bundle
            if (share.getFiles() != null) {
                share.getFiles().removeIf(f -> f.getId().equals(fileId));
            }

            // determine if share is empty
            boolean emptySingle = share.getFile() == null;
            boolean emptyBundle = share.getFiles() == null || share.getFiles().isEmpty();

            // delete empty share
            if (emptySingle && emptyBundle) {
                streamTokenRepository.deleteByRecipient_Share_Id(share.getId());
                shareRepository.delete(share);
            } else {
                shareRepository.save(share);
            }
        }

        // 3. delete stream tokens
        streamTokenRepository.deleteByFileId(fileId);

        // 4. delete physical file
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
        } catch (Exception ignored) {
        }

        // 5. delete DB record
        fileRepository.delete(file);
    }

    @Transactional
    public void autoDeleteFile(FileEntity file) {
        permanentlyDeleteFile(file);
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