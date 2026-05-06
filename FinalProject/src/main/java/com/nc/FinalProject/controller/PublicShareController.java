package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.FileViewResponse;
import com.nc.FinalProject.dto.OpenShareRequest;
import com.nc.FinalProject.dto.ShareMetaResponse;
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

    public PublicShareController(FileService fileService, FileStreamingService fileStreamingService) {
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

    // ✅ 2. ACCESS FILE (increments usedCount + supports streaming)
    @PostMapping("/{token}/access")
    public ResponseEntity<Resource> accessFile(
            @PathVariable String token,
            @RequestBody(required = false) OpenShareRequest request,
            HttpServletRequest httpRequest
    ) throws IOException {

        String password = (request != null) ? request.getPassword() : null;

        FileViewResponse file = fileService.accessSharedFile(token, password);

        return fileStreamingService.streamFile(file, httpRequest);
    }
}