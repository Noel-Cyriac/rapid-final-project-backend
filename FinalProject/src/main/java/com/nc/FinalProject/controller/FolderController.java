package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.request.CreateFolderRequest;
import com.nc.FinalProject.dto.request.MoveFolderRequest;
import com.nc.FinalProject.dto.request.RenameFolderRequest;
import com.nc.FinalProject.dto.response.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final UserRepository userRepository;

    private Users user(Authentication auth) {
        return userRepository
                .findByEmail(auth.getName())
                .orElseThrow();
    }

    @PostMapping
    public ResponseEntity<SuccessResponse>
    createFolder(
            @RequestBody
            CreateFolderRequest request,
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Folder created",
                        folderService.createFolder(
                                request,
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/contents")
    public ResponseEntity<SuccessResponse> listFolders(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                new SuccessResponse(
                        "Folders fetched successfully",
                        folderService.listFolders(
                                folderId,
                                user(auth),
                                PageRequest.of(page, size)
                        )
                )
        );
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> downloadFolder(@PathVariable Long id, Authentication auth) {
        StreamingResponseBody stream = outputStream ->
                folderService.downloadFolder(id, user(auth), outputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"folder.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @PutMapping("/{id}/rename")
    public SuccessResponse rename(
            @PathVariable Long id,
            @RequestBody RenameFolderRequest request,
            Authentication auth
    ) {
        folderService.renameFolder(id, request.getName(), user(auth));
        return new SuccessResponse("Folder renamed", null);
    }

    @PutMapping("/{id}/move")
    public SuccessResponse move(
            @PathVariable Long id,
            @RequestBody MoveFolderRequest request,
            Authentication auth
    ) {
        folderService.moveFolder(id, request.getTargetFolderId(), user(auth));
        return new SuccessResponse("Folder moved", null);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse> deleteFolder(@PathVariable Long id, Authentication auth) {
        folderService.deleteFolder(id, user(auth));
        return ResponseEntity.ok(new SuccessResponse("Folder moved to recycle bin", null));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<SuccessResponse> restoreFolder(@PathVariable Long id, Authentication auth) {
        folderService.restoreFolder(id, user(auth));
        return ResponseEntity.ok(new SuccessResponse("Folder restored successfully", null));
    }
}