package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.FileResponse;
import com.nc.FinalProject.dto.SuccessResponse;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        Users user = userRepository.findByEmail(auth.getName()).orElseThrow();
        FileResponse response = fileService.uploadFile(file, user);
        return ResponseEntity.ok(
                new SuccessResponse("File uploaded successfully", response)
        );
    }

    @GetMapping("/list")
    public ResponseEntity<SuccessResponse> listFiles(Authentication auth,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        Users user = userRepository.findByEmail(auth.getName()).orElseThrow();
        Page<FileResponse> filesPage =
                fileService.listFiles(user, PageRequest.of(page, size));
        return ResponseEntity.ok(
                new SuccessResponse("Files fetched successfully", filesPage.getContent())
        );
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication auth) throws IOException {

        Users user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Path path = fileService.getFilePath(id, user);
        String fileName = fileService.getFileName(id);

        byte[] content = Files.readAllBytes(path);

        // detect mime type
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(content);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Users user = userRepository.findByEmail(auth.getName()).orElseThrow();
        fileService.deleteFile(id, user);
        return ResponseEntity.ok("File deleted");
    }
}