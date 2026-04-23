package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.ShareRequest;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.SharedFile;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.FileRepository;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    @PostMapping("/create")
    public SharedFile share(@RequestBody ShareRequest request, Authentication auth) {

        Users user = userRepository.findByEmail(auth.getName()).orElseThrow();
        FileEntity file = fileRepository.findById(request.getFileId())
                .filter(f -> f.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("File not found"));

        return shareService.shareFile(file, request);
    }

    @GetMapping("/list")
    public List<SharedFile> listShared(Authentication auth) {
        return shareService.listSharedFiles(auth.getName());
    }

    @GetMapping("/access/{link}")
    public SharedFile accessShared(@PathVariable String link) {
        return shareService.getSharedFile(link);
    }
}