package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.request.OpenShareRequest;
import com.nc.FinalProject.dto.response.ShareMetaResponse;
import com.nc.FinalProject.dto.response.StreamResponse;
import com.nc.FinalProject.service.FileService;
import com.nc.FinalProject.service.FileStreamingService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/share")
public class PublicShareController {

    private final FileService fileService;
    private final FileStreamingService fileStreamingService;

    public PublicShareController(FileService fileService,
                                 FileStreamingService fileStreamingService) {
        this.fileService = fileService;
        this.fileStreamingService = fileStreamingService;
    }

    // ✅ 1. OPEN LINK (increments openCount only)
    @GetMapping("/{token}")
    public ResponseEntity<ShareMetaResponse> openShare(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(fileService.openShareLink(token));
    }

    // ✅ 2. VALIDATE ACCESS (password check + returns stream token)
    @PostMapping("/{token}/access")
    public ResponseEntity<StreamResponse> accessFile(
            @PathVariable String token,
            @RequestBody(required = false) OpenShareRequest request
    ) {

        String password = (request != null) ? request.getPassword() : null;

        return ResponseEntity.ok(
                fileService.accessSharedFile(token, password)
        );
    }

    // ✅ 3. REAL STREAMING ENDPOINT (USED BY VIDEO / PDF / AUDIO)
    @GetMapping("/stream/{streamToken}")
    public ResponseEntity<Resource> streamFile(
            @PathVariable String streamToken,
            HttpServletRequest request
    ) throws IOException {

        return fileStreamingService.streamByToken(streamToken, request);
    }
}