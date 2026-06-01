package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.response.FileViewResponse;
import com.nc.FinalProject.dto.response.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FileService;
import com.nc.FinalProject.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final FileStreamingService fileStreamingService;

    private Users user(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse> upload(
            @RequestParam("files") MultipartFile[] files,
            Authentication auth
    ) throws Exception{
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
    public ResponseEntity<Resource> view(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest request
    ) throws Exception {

        FileViewResponse file =
                fileService.viewFile(id, user(auth));

        return fileStreamingService.streamFile(file, request);
    }

    @GetMapping("/{id}/stream-token")
    public SuccessResponse getStreamToken(
            @PathVariable Long id,
            Authentication auth
    ) {

        String token =
                fileService.createStreamToken(
                        id,
                        user(auth)
                );

        return new SuccessResponse(
                "Stream token created",
                Map.of(
                        "url",
                        "http://localhost:8080/api/files/stream/"
                                + token
                )
        );
    }

    @GetMapping("/stream/{token}")
    public ResponseEntity<Resource> streamByToken(
            @PathVariable String token,
            HttpServletRequest request
    ) throws Exception {

        return fileStreamingService.streamByTokenForViewing(
                token,
                request
        );
    }

    @DeleteMapping("/delete")
    public ResponseEntity<SuccessResponse> deleteMultiple(
            @RequestBody List<Long> ids,
            @RequestParam(defaultValue = "false") boolean force,
            Authentication auth
    ) {

        fileService.deleteFiles(
                ids,
                user(auth),
                force
        );

        return ResponseEntity.ok(
                new SuccessResponse(
                        "File(s) moved to recycle bin",
                        null
                )
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

    @GetMapping("/dashboard/latest-uploads")
    public ResponseEntity<SuccessResponse> latestUploads(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Latest uploads fetched",
                        fileService.latestUploads(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/dashboard/latest-downloads")
    public ResponseEntity<SuccessResponse> latestDownloads(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Latest downloads fetched",
                        fileService.latestDownloads(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/dashboard/recently-opened")
    public ResponseEntity<SuccessResponse> recentlyOpened(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Recently opened files fetched",
                        fileService.recentlyOpened(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<SuccessResponse> dashboardStats(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Dashboard stats fetched",
                        fileService.dashboardStats(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/dashboard/storage-breakdown")
    public ResponseEntity<SuccessResponse> storageBreakdown(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Storage breakdown fetched",
                        fileService.storageBreakdown(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/dashboard/activity-trend")
    public ResponseEntity<SuccessResponse> activityTrend(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Activity trend fetched",
                        fileService.activityTrend(
                                user(auth),
                                days
                        )
                )
        );
    }

    @GetMapping("/dashboard/transfer-usage")
    public ResponseEntity<SuccessResponse> transferUsage(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Transfer usage fetched",
                        fileService.transferUsage(
                                user(auth),
                                days
                        )
                )
        );
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            Authentication auth
    ) throws Exception {

        Path path = fileService.getFilePath(id, user(auth));

        InputStream inputStream =
                new BufferedInputStream(
                        new FileInputStream(path.toFile())
                );

        Resource resource =
                new InputStreamResource(inputStream);

        String contentType =
                Files.probeContentType(path);

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(contentType)
                )
                .contentLength(Files.size(path))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                path.getFileName().toString() +
                                "\""
                )
                .body(resource);
    }

    @PostMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadMultiple(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {

        StreamingResponseBody stream = outputStream -> {
            fileService.downloadMultiple(
                    ids,
                    user(auth),
                    outputStream
            );
        };

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"files.zip\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
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

    @PostMapping("/star")
    public ResponseEntity<SuccessResponse> star(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {
        fileService.starFiles(ids, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse("Files starred successfully", null)
        );
    }

    @PostMapping("/unstar")
    public ResponseEntity<SuccessResponse> unstar(
            @RequestBody List<Long> ids,
            Authentication auth
    ) {
        fileService.unstarFiles(ids, user(auth));

        return ResponseEntity.ok(
                new SuccessResponse("Files unstarred successfully", null)
        );
    }

    @GetMapping("/starred")
    public ResponseEntity<SuccessResponse> starred(Authentication auth, Pageable pageable) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Starred files fetched successfully",
                        fileService.getStarredFiles(user(auth), pageable)
                )
        );
    }

}