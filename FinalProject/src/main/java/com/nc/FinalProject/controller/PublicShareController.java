package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.FileViewResponse;
import com.nc.FinalProject.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/share")
public class PublicShareController {

    private final FileService fileService;

    public PublicShareController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> open(
            @PathVariable String token,
            @RequestParam(required = false) String password
    ) throws Exception {

        FileViewResponse file = fileService.openSharedFile(token, password);

        byte[] bytes = Files.readAllBytes(Path.of(file.path()));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type()))
                .body(bytes);
    }
}