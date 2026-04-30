package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.ShareRequest;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.SharedFile;
import com.nc.FinalProject.repository.SharedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final SharedFileRepository sharedFileRepository;

    public SharedFile shareFile(FileEntity file, ShareRequest request) {

        Instant expireAt = LocalDateTime.now()
                .plusHours(request.getExpireHours())
                .atZone(ZoneId.systemDefault())
                .toInstant();

        SharedFile sharedFile = SharedFile.builder()
                .file(file)
                .recipientEmail(request.getEmail())
                .message(request.getMessage())
                .shareLink(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(request.getExpireHours()))
                .accessed(0)
                .build();

        return sharedFileRepository.save(sharedFile);
    }

    public Page<SharedFile> listSharedFiles(String ownerEmail, Pageable pageable) {
        return sharedFileRepository.findByFileOwnerEmail(ownerEmail, pageable);
    }

    public SharedFile getSharedFile(String link) {
        SharedFile shared = sharedFileRepository.findByShareLink(link)
                .orElseThrow(() -> new RuntimeException("Invalid share link"));

        if (shared.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Share link expired");
        }

        shared.setAccessed(
                shared.getAccessed() == null ? 1 : shared.getAccessed() + 1
        );
        return sharedFileRepository.save(shared);
    }
}