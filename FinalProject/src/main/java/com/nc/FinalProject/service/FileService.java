package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
import com.nc.FinalProject.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final FileActivityRepository activityRepository;
    private final FolderRepository folderRepository;
    private final ShareRepository shareRepository;
    private final MailService mailService;
    private final StreamTokenRepository streamTokenRepository;

    @Value("${file.storage.location}")
    private String uploadDir;

    // ======================
    // MULTI UPLOAD
    // ======================
    public List<FileResponse> uploadFiles(MultipartFile[] files, Users user) {
        try {
            Files.createDirectories(Paths.get(uploadDir));

            List<FileResponse> list = new ArrayList<>();

            for (MultipartFile file : files) {

                if (file.isEmpty()) continue;

                String finalName = generateUniqueName(
                        file.getOriginalFilename(),
                        user,
                        null
                );

                String storedName =
                        System.currentTimeMillis() + "_" + finalName;

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
                                .deleted(false)
                                .downloadCount(0)
                                .build()
                );

                track(user, saved, "UPLOAD", file.getSize());

                list.add(mapToResponse(saved));
            }

            return list;

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
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
    public PagedResponse<FileResponse> recycleBin(Users user, Pageable pageable) {

        Page<FileEntity> page =
                fileRepository.findByOwnerAndDeletedTrue(user, pageable);

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
    // DOWNLOAD
    // ======================
    public Path getFilePath(Long id, Users user) {

        FileEntity file =
                fileRepository.findByIdAndOwner(id, user)
                        .orElseThrow();

        file.setDownloadCount(file.getDownloadCount() + 1);
        file.setLastDownloadedAt(LocalDateTime.now());

        fileRepository.save(file);

        track(user, file, "DOWNLOAD", file.getSize());

        return Paths.get(file.getFilePath());
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

        for (FileEntity file : files) {

            if (!file.getOwner()
                    .getId()
                    .equals(user.getId())) {
                continue;
            }

            Path path = Paths.get(file.getFilePath());

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
        }

        zos.finish();
        zos.close();

        fileRepository.saveAll(files);
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

            // 1. delete child rows FIRST (important for FK constraint)
            activityRepository.deleteByFile_Id(fileId);

            // 2. delete physical file from disk
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
            } catch (Exception ignored) {}

            // 3. delete DB record
            fileRepository.delete(file);
        }
    }
    // ======================
    // DASHBOARD
    // ======================
    public DashboardResponse dashboard(Users user) {

        LocalDateTime today =
                LocalDateTime.now()
                        .toLocalDate()
                        .atStartOfDay();

        List<FileResponse> latestUploads =
                fileRepository
                        .findTop4ByOwnerAndDeletedFalseOrderByUploadedAtDesc(user)
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        List<FileResponse> latestDownloads =
                activityRepository
                        .findTop4ByUserAndActionAndFile_DeletedFalseOrderByCreatedAtDesc(
                                user,
                                "DOWNLOAD"
                        )
                        .stream()
                        .map(a -> mapToResponse(a.getFile()))
                        .toList();

        List<FileResponse> recentlyOpened =
                fileRepository
                        .findTop4ByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(user)
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return DashboardResponse.builder()
                .todayUploads(activityRepository.todayCount(user, "UPLOAD", today))
                .todayDownloads(activityRepository.todayCount(user, "DOWNLOAD", today))
                .todayDeletes(activityRepository.todayCount(user, "DELETE", today))
                .totalUploads(activityRepository.countByAction(user, "UPLOAD"))
                .totalDownloads(activityRepository.countByAction(user, "DOWNLOAD"))
                .usedStorage(fileRepository.totalUsed(user))
                .latestUploads(latestUploads)
                .latestDownloads(latestDownloads)
                .recentlyOpened(recentlyOpened)
                .build();
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

    public PagedResponse<FileResponse> getDownloadedFiles(
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

        Page<FileResponse> dtoPage =
                page.map(a -> mapToResponse(a.getFile()));

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
    public PagedResponse<FileResponse> getRecentlyOpenedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<FileEntity> page =
                fileRepository
                        .findByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(
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
                f.getUploadedAt()
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
                fileRepository.findByOwnerAndStarredTrue(user, pageable);

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

    public ShareResponse createShareUnified(
            ShareRequest req,
            Users user
    ) {

        boolean isBundle =
                req.getFileIds() != null &&
                        req.getFileIds().size() > 1;

        Share share = new Share();

        share.setOwner(user);
        share.setShareToken(UUID.randomUUID().toString());
        share.setExpireAt(
                LocalDateTime.now()
                        .plusHours(req.getExpireHours())
        );
        share.setMaxUses(req.getMaxUses());
        share.setUsedCount(0);
        share.setOpenCount(0);
        share.setActive(true);
        share.setPassword(req.getPassword());
        share.setMessage(req.getMessage());
        share.setSharedAt(LocalDateTime.now());

        // multiple recipient emails
        share.setRecipientEmails(req.getEmails());

        // ======================
        // SINGLE FILE
        // ======================
        if (!isBundle) {

            Long fileId =
                    (req.getFileId() != null)
                            ? req.getFileId()
                            : req.getFileIds().get(0);

            FileEntity file =
                    fileRepository.findByIdAndOwner(fileId, user)
                            .orElseThrow();

            share.setType(Share.ShareType.FILE);
            share.setFile(file);
        }

        // ======================
        // BUNDLE SHARE
        // ======================
        else {

            List<FileEntity> files =
                    fileRepository.findAllById(req.getFileIds());

            share.setType(Share.ShareType.BUNDLE);
            share.setFiles(files);
        }

        share = shareRepository.save(share);

        // ======================
        // SEND EMAILS
        // ======================
        String link =
                "http://localhost:5175/share/" +
                        share.getShareToken();

        for (String email : req.getEmails()) {
            mailService.sendShareEmail(email, link);
        }

        return ShareResponse.builder()
                .id(share.getId())
                .token(share.getShareToken())

                // all recipient emails
                .emails(share.getRecipientEmails())

                .expiresAt(share.getExpireAt())
                .maxUses(share.getMaxUses())
                .usedCount(share.getUsedCount())
                .openCount(share.getOpenCount())
                .active(share.getActive())

                .fileName(
                        share.getType() == Share.ShareType.FILE
                                ? share.getFile().getFileName()
                                : "Bundle (" +
                                share.getFiles().size() +
                                  " files)"
                )

                .sharedAt(share.getSharedAt())
                .message(share.getMessage())
                .type(share.getType().name())
                .build();
    }
    public ShareMetaResponse openShareLink(String token) {

        Share share = shareRepository.findByShareToken(token)
                .orElseThrow();

        if (!share.getActive())
            throw new LinkDisabledException("Link disabled");

        if (share.getExpireAt().isBefore(LocalDateTime.now()))
            throw new LinkExpiredException("Link expired");

        share.setOpenCount(share.getOpenCount() + 1);
        share.setLastOpenedAt(LocalDateTime.now());

        shareRepository.save(share);

        // =========================
        // FILE LIST (FOR BUNDLE SUPPORT)
        // =========================
        List<FileMiniResponse> files;

        if (share.getType() == Share.ShareType.FILE) {

            files = List.of(
                    new FileMiniResponse(
                            share.getFile().getId(),
                            share.getFile().getFileName(),
                            share.getFile().getFileType()
                    )
            );

        } else {

            files = share.getFiles().stream()
                    .map(f -> new FileMiniResponse(
                            f.getId(),
                            f.getFileName(),
                            f.getFileType()
                    ))
                    .toList();
        }

        return ShareMetaResponse.builder()
                .fileName(
                        share.getType() == Share.ShareType.FILE
                                ? share.getFile().getFileName()
                                : "Bundle (" + share.getFiles().size() + " files)"
                )
                .fileType(
                        share.getType() == Share.ShareType.FILE
                                ? share.getFile().getFileType()
                                : "BUNDLE"
                )
                .requiresPassword(share.getPassword() != null)
                .canDownload(true)
                .canView(true)
                .message(share.getMessage())

                // ✅ NEW FIELD (IMPORTANT)
                .files(files)

                .build();
    }

    @Transactional
    public StreamResponse accessSharedFile(String token, String password) {

        Share share = shareRepository.findByShareToken(token)
                .orElseThrow();

        if (!share.getActive())
            throw new LinkDisabledException("Link disabled");

        if (share.getExpireAt().isBefore(LocalDateTime.now()))
            throw new LinkExpiredException("Link expired");

        if (share.getUsedCount() >= share.getMaxUses())
            throw new MaxUsesExceededException("Max uses exceeded");

        if (share.getPassword() != null &&
                !share.getPassword().equals(password)) {
            throw new InvalidSharePasswordException("Invalid password");
        }

        share.setUsedCount(share.getUsedCount() + 1);
        shareRepository.save(share);

        String streamToken = UUID.randomUUID().toString();

        StreamToken st = StreamToken.builder()
                .token(streamToken)
                .share(share)   // ✅ IMPORTANT: single relation now
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        streamTokenRepository.save(st);

        return StreamResponse.builder()
                .streamToken(streamToken)
                .build();
    }
    public void revokeShare(Long shareId, Users user) {

        Share share = shareRepository.findById(shareId)
                .orElseThrow();

        if (!share.getOwner().getId().equals(user.getId()))
            return;

        share.setActive(false);

        shareRepository.save(share);
    }

    public PagedResponse<ShareResponse> listSharedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<Share> page =
                shareRepository.findByOwner(user, pageable);

        return new PagedResponse<>(

                page.map(s -> ShareResponse.builder()

                        .id(s.getId())
                        .token(s.getShareToken())

                        // all emails
                        .emails(s.getRecipientEmails())

                        .expiresAt(s.getExpireAt())
                        .maxUses(s.getMaxUses())
                        .usedCount(s.getUsedCount())
                        .openCount(s.getOpenCount())
                        .active(s.getActive())

                        .fileName(
                                s.getType() == Share.ShareType.FILE
                                        ? s.getFile().getFileName()
                                        : "Bundle (" +
                                        s.getFiles().size() +
                                          " files)"
                        )

                        .sharedAt(s.getSharedAt())
                        .message(s.getMessage())
                        .lastOpenedAt(s.getLastOpenedAt())
                        .type(s.getType().name())

                        .build()

                ).getContent(),

                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
    @Transactional
    public void cleanupShares(Users user) {

        LocalDateTime now = LocalDateTime.now();

        List<Share> shares =
                shareRepository.findAllByOwner(user);

        List<Share> toDelete = shares.stream()
                .filter(s ->
                        !s.getActive() ||
                                s.getExpireAt().isBefore(now)
                )
                .toList();

        // delete stream tokens first
        for (Share share : toDelete) {
            streamTokenRepository.deleteByShare(share);
        }

        // then delete shares
        shareRepository.deleteAll(toDelete);
    }
}