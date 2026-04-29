package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.FileViewResponse;
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
import java.util.List;

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

    @GetMapping("/view/{id}")
    public ResponseEntity<byte[]> view(
            @PathVariable Long id,
            Authentication auth
    ) throws Exception {

        FileViewResponse file = fileService.viewFile(id, user(auth));

        byte[] bytes = Files.readAllBytes(Path.of(file.path()));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type()))
                .body(bytes);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<SuccessResponse> deleteMultiple(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {
        fileService.deleteFiles(ids, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse("File(s) moved to recycle bin", null)
        );
    }

    @PostMapping("/restore")
    public ResponseEntity<SuccessResponse> restoreMultiple(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {
        fileService.restoreFiles(ids, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse("File(s) restored successfully", null)
        );
    }

    @GetMapping("/recycle")
    public ResponseEntity<SuccessResponse> recycle(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Recycle bin fetched successfully",
                        fileService.recycleBin(
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }

    @DeleteMapping("/permanent-delete")
    public ResponseEntity<?> deletePermanent(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {
        fileService.deletePermanent(ids, user(auth));
        return ResponseEntity.ok(
                new SuccessResponse("File(s) deleted permanently", null)
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

    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadMultiple(
            @RequestBody List<Long> ids,
            Authentication auth
    ) throws Exception {

        byte[] zipBytes = fileService.downloadMultiple(ids, user(auth));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"files.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

    @GetMapping("/uploaded")
    public ResponseEntity<SuccessResponse> uploaded(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Uploaded files fetched",
                        fileService.getUploadedFiles(
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }

    @GetMapping("/downloaded")
    public ResponseEntity<SuccessResponse> downloaded(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Downloaded files fetched",
                        fileService.getDownloadedFiles(
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }

    @GetMapping("/recent")
    public ResponseEntity<SuccessResponse> recent(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Recently opened files fetched",
                        fileService.getRecentlyOpenedFiles(
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }
}