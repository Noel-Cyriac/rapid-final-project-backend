package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.ShareRequest;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.SharedFile;
import com.nc.FinalProject.repository.SharedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
                .recipientEmail(request.getRecipientEmail())
                .message(request.getMessage())
                .shareLink(UUID.randomUUID().toString())
                .shareDate(Instant.now())
                .expireAt(expireAt)
                .accessed(false)
                .build();

        return sharedFileRepository.save(sharedFile);
    }

    public List<SharedFile> listSharedFiles(String ownerEmail) {
        return sharedFileRepository.findByFileOwnerEmail(ownerEmail);
    }

    public SharedFile getSharedFile(String link) {
        SharedFile shared = sharedFileRepository.findByShareLink(link)
                .orElseThrow(() -> new RuntimeException("Invalid share link"));

        if (shared.getExpireAt().isBefore(Instant.now())) {
            throw new RuntimeException("Share link expired");
        }

        shared.setAccessed(true);
        return sharedFileRepository.save(shared);
    }
}