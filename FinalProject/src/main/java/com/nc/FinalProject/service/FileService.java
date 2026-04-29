package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
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

    public byte[] downloadMultiple(List<Long> ids, Users user) throws Exception {

        List<FileEntity> files = fileRepository.findAllById(ids);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

            Path path = Paths.get(file.getFilePath());

            zos.putNextEntry(new ZipEntry(file.getFileName()));

            Files.copy(path, zos);

            zos.closeEntry();

            // update stats per file
            file.setDownloadCount(file.getDownloadCount() + 1);
            file.setLastDownloadedAt(LocalDateTime.now());

            track(user, file, "DOWNLOAD", file.getSize());
        }

        zos.finish();
        zos.close();

        fileRepository.saveAll(files);

        return baos.toByteArray();
    }

    // ======================
    // DELETE
    // ======================
    public void deleteFiles(List<Long> ids, Users user) {

        List<FileEntity> files = fileRepository.findAllById(ids);

        for (FileEntity file : files) {

            if (!file.getOwner().getId().equals(user.getId()))
                continue;

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
}