package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.FileResponse;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.exception.FileNotFoundException;
import com.nc.FinalProject.exception.FileUploadException;
import com.nc.FinalProject.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    @Value("${file.storage.location}")
    private String uploadDir;

    public FileResponse uploadFile(MultipartFile multipartFile, Users user) {

        try {
            // Max size check (100 MB)
            if (multipartFile.getSize() > 100L * 1024 * 1024) {
                throw new FileUploadException("File exceeds 100 MB");
            }

            // Ensure upload directory exists inside project folder
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Sanitize filename
            String originalFilename = multipartFile.getOriginalFilename().replaceAll("\\s+", "_");
            Path filePath = uploadPath.resolve(System.currentTimeMillis() + "_" + originalFilename);

            // Save file locally
            multipartFile.transferTo(filePath.toFile());

            // Save file info in DB
            FileEntity fileEntity = FileEntity.builder()
                    .fileName(multipartFile.getOriginalFilename())
                    .filePath(filePath.toString())
                    .size(multipartFile.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .owner(user)
                    .build();

            FileEntity saved = fileRepository.save(fileEntity);

            return new FileResponse(
                    saved.getId(),
                    saved.getFileName(),
                    FileResponse.formatSize(saved.getSize()),  // human-readable
                    saved.getUploadedAt()
            );

        } catch (IOException e) {
            logger.error("File upload failed: {}", e.getMessage(), e);
            throw new FileUploadException("Failed to upload file: " + e.getMessage());
        }
    }
    public Page<FileResponse> listFiles(Users user, Pageable pageable) {
        return fileRepository.findByOwner(user, pageable)
                .map(f -> new FileResponse(
                        f.getId(),
                        f.getFileName(),
                        FileResponse.formatSize(f.getSize()), // convert bytes to KB/MB/GB
                        f.getUploadedAt()
                ));
    }

    public Path getFilePath(Long fileId, Users user) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .filter(f -> f.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new FileNotFoundException("File not found or access denied"));

        Path path = Path.of(fileEntity.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new FileNotFoundException("File not found on server");
        }
        return path; // just return Path, no HTTP stuff
    }

    public String getFileName(Long fileId) {
        return fileRepository.findById(fileId)
                .map(FileEntity::getFileName)
                .orElse("unknown");
    }

    public FileEntity getFile(Long fileId, Users user) {
        return fileRepository.findById(fileId)
                .filter(f -> f.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("File not found"));
    }

    public void deleteFile(Long fileId, Users user) {
        FileEntity file = getFile(fileId, user);
        new File(file.getFilePath()).delete();
        fileRepository.delete(file);
    }
}