package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    private Users user(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse> upload(
            @RequestParam("files") MultipartFile[] files,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Files uploaded successfully",
                        fileService.uploadFiles(files, user(auth))
                )
        );
    }

    @GetMapping("/list")
    public ResponseEntity<SuccessResponse> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Files fetched successfully",
                        fileService.listFiles(
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse> delete(
            @PathVariable Long id,
            Authentication auth
    ) {
        fileService.deleteFile(id, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse(
                        "File moved to recycle bin",
                        null
                )
        );
    }

    @PostMapping("/restore/{id}")
    public ResponseEntity<SuccessResponse> restore(
            @PathVariable Long id,
            Authentication auth
    ) {
        fileService.restoreFile(id, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse(
                        "File restored successfully",
                        null
                )
        );
    }

    @GetMapping("/recycle")
    public ResponseEntity<SuccessResponse> recycle(Authentication auth) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Recycle bin fetched successfully",
                        fileService.recycleBin(user(auth), PageRequest.of(0, 20))
                )
        );
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SuccessResponse> dashboard(Authentication auth) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Dashboard fetched successfully",
                        fileService.dashboard(user(auth))
                )
        );
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            Authentication auth
    ) throws Exception {

        Path path = fileService.getFilePath(id, user(auth));
        byte[] bytes = Files.readAllBytes(path);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName() + "\"")
                .body(bytes);
    }
}