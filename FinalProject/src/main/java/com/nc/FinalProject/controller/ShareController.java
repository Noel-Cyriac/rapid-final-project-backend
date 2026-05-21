package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.request.OpenShareRequest;
import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FileStreamingService;
import com.nc.FinalProject.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ShareController {

    private final FileStreamingService fileStreamingService;
    private final UserRepository userRepository;
    private final ShareService shareService;

    private Users user(Authentication a) {
        return userRepository.findByEmail(a.getName()).orElseThrow();
    }

    // ================= CREATE =================
    @PostMapping("/create")
    public ResponseEntity<ShareResponse> createShare(@RequestBody ShareRequest req, Authentication a) {
        return ResponseEntity.ok(shareService.createShareUnified(req, user(a)));
    }

    // ================= LIST =================
    @GetMapping("/shared")
    public ResponseEntity<SuccessResponse<PagedResponse<ShareResponse>>> shared(
            Authentication a,
            @PageableDefault(sort = "sharedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                new SuccessResponse<>(
                        "Shared files fetched successfully",
                        shareService.listSharedFiles(user(a), pageable)
                )
        );
    }

    // ================= REVOKE =================
    @DeleteMapping("/{shareId}")
    public ResponseEntity<SuccessResponse<Void>> revoke(@PathVariable Long shareId, Authentication a) {
        shareService.revokeShare(shareId, user(a));
        return ResponseEntity.ok(new SuccessResponse<>("Share revoked successfully", null));
    }

    // ================= CLEANUP =================
    @DeleteMapping("/cleanup")
    public ResponseEntity<SuccessResponse<Void>> cleanup(Authentication a) {
        shareService.cleanupShares(user(a));
        return ResponseEntity.ok(new SuccessResponse<>("Expired/revoked shares deleted successfully", null));
    }

    // ================= PUBLIC SHARE =================
    @GetMapping("/public/{token}")
    public ResponseEntity<ShareMetaResponse> open(@PathVariable String token) {
        return ResponseEntity.ok(shareService.openShareLink(token));
    }

    @PostMapping("/public/{token}/access")
    public ResponseEntity<StreamResponse> access(
            @PathVariable String token,
            @RequestBody(required = false) OpenShareRequest req
    ) {
        return ResponseEntity.ok(
                shareService.accessSharedFile(token, req != null ? req.getPassword() : null)
        );
    }

    // ================= STREAM =================
    @GetMapping("/public/stream/{streamToken}/{fileId}")
    public ResponseEntity<Resource> stream(
            @PathVariable String streamToken,
            @PathVariable Long fileId,
            HttpServletRequest request
    ) throws IOException {
        return fileStreamingService.streamByToken(streamToken, fileId, request);
    }

    // ================= DOWNLOAD =================
    @GetMapping("/public/download/{streamToken}/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String streamToken,
            @PathVariable Long fileId
    ) throws IOException {
        return fileStreamingService.downloadByToken(streamToken, fileId);
    }

    @GetMapping("/public/download/{streamToken}")
    public ResponseEntity<StreamingResponseBody> downloadBundle(@PathVariable String streamToken) {

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"share-bundle.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out -> fileStreamingService.downloadBundle(streamToken, out));
    }
}