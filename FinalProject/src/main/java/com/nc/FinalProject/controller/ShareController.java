package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.request.OpenShareRequest;
import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FileService;
import com.nc.FinalProject.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final FileService fileService;
    private final FileStreamingService fileStreamingService;
    private final UserRepository userRepository;


    public ShareController(
            FileService fileService,
            FileStreamingService fileStreamingService, UserRepository userRepository
    ) {
        this.fileService = fileService;
        this.fileStreamingService = fileStreamingService;
        this.userRepository = userRepository;
    }

    private Users user(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    @PostMapping("/create")
    public ResponseEntity<ShareResponse> createShare(
            @RequestBody ShareRequest req,
            Authentication auth
    ) {

        return ResponseEntity.ok(
                fileService.createShareUnified(req, user(auth))
        );
    }

    @GetMapping("/shared")
    public ResponseEntity<SuccessResponse<PagedResponse<ShareResponse>>> shared(
            Authentication auth,
            @PageableDefault(
                    sort = "sharedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return ResponseEntity.ok(
                new SuccessResponse<>(
                        "Shared files fetched successfully",
                        fileService.listSharedFiles(user(auth), pageable)
                )
        );
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<SuccessResponse> revoke(@PathVariable Long shareId, Authentication auth) {
        fileService.revokeShare(shareId, user(auth));
        return ResponseEntity.ok(
                new SuccessResponse<Void>(
                        "Share revoked successfully",
                        null
                )
        );
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<SuccessResponse<Void>> cleanupShares(
            Authentication auth
    ) {

        fileService.cleanupShares(user(auth));

        return ResponseEntity.ok(
                new SuccessResponse<>(
                        "Expired/revoked shares deleted successfully",
                        null
                )
        );
    }

    // =========================
    // OPEN SHARE LINK
    // =========================
    @GetMapping("public/{token}")
    public ResponseEntity<ShareMetaResponse> openShare(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(fileService.openShareLink(token));
    }

    @GetMapping("public/bundle/{token}")
    public ResponseEntity<ShareMetaResponse> openBundleShare(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(
                fileService.openBundleShare(token)
        );
    }

    // =========================
    // VALIDATE PASSWORD
    // RETURNS STREAM TOKEN
    // =========================
    @PostMapping("public/{token}/access")
    public ResponseEntity<StreamResponse> accessFile(
            @PathVariable String token,
            @RequestBody(required = false) OpenShareRequest request
    ) {

        String password = request != null
                ? request.getPassword()
                : null;

        return ResponseEntity.ok(
                fileService.accessSharedFile(token, password)
        );
    }

    @PostMapping("public/bundle/{token}/access")
    public ResponseEntity<StreamResponse> accessBundle(
            @PathVariable String token,
            @RequestBody(required = false) OpenShareRequest request
    ) {

        String password = request != null
                ? request.getPassword()
                : null;

        return ResponseEntity.ok(
                fileService.accessBundle(token, password)
        );
    }

    // =========================
    // VIEW / STREAM
    // =========================
    @GetMapping("public/stream/{streamToken}")
    public ResponseEntity<Resource> streamFile(
            @PathVariable String streamToken,
            HttpServletRequest request
    ) throws IOException {

        return fileStreamingService.streamByToken(
                streamToken,
                request
        );
    }

    // =========================
    // DOWNLOAD
    // =========================
    @GetMapping("public/download/{streamToken}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String streamToken
    ) throws IOException {

        return fileStreamingService.downloadByToken(streamToken);
    }

    @GetMapping("public/bundle/download/{streamToken}")
    public ResponseEntity<StreamingResponseBody> downloadBundle(
            @PathVariable String streamToken
    ) {

        StreamingResponseBody stream = outputStream ->
                fileStreamingService.downloadBundle(streamToken, outputStream);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"share-bundle.zip\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }
}