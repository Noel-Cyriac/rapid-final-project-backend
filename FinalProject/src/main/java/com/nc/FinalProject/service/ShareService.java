package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Share;
import com.nc.FinalProject.entity.StreamToken;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.exception.InvalidSharePasswordException;
import com.nc.FinalProject.exception.LinkDisabledException;
import com.nc.FinalProject.exception.LinkExpiredException;
import com.nc.FinalProject.exception.MaxUsesExceededException;
import com.nc.FinalProject.repository.FileRepository;
import com.nc.FinalProject.repository.ShareRepository;
import com.nc.FinalProject.repository.StreamTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final StreamTokenRepository streamTokenRepository;
    private final MailService mailService;

    // ======================
    // CREATE SHARE
    // ======================
    public ShareResponse createShareUnified(
            ShareRequest req,
            Users user
    ) {

        boolean isBundle =
                req.getFileIds() != null &&
                        req.getFileIds().size() > 1;

        Share share = new Share();

        share.setOwner(user);
        share.setShareToken(UUID.randomUUID().toString());

        share.setExpireAt(
                LocalDateTime.now()
                        .plusHours(req.getExpireHours())
        );

        share.setMaxUses(req.getMaxUses());
        share.setUsedCount(0);
        share.setOpenCount(0);
        share.setActive(true);
        share.setPassword(req.getPassword());
        share.setMessage(req.getMessage());
        share.setSharedAt(LocalDateTime.now());

        share.setRecipientEmails(req.getEmails());

        // ======================
        // SINGLE FILE
        // ======================
        if (!isBundle) {

            Long fileId =
                    req.getFileId() != null
                            ? req.getFileId()
                            : req.getFileIds().get(0);

            FileEntity file =
                    fileRepository.findByIdAndOwner(fileId, user)
                            .orElseThrow();

            share.setType(Share.ShareType.FILE);
            share.setFile(file);

        } else {

            List<FileEntity> files =
                    fileRepository.findAllById(req.getFileIds());

            share.setType(Share.ShareType.BUNDLE);
            share.setFiles(files);
        }

        share = shareRepository.save(share);

        String link =
                "http://localhost:5175/share/" +
                        share.getShareToken();

        for (String email : req.getEmails()) {
            mailService.sendShareEmail(email, link);
        }

        return mapToResponse(share);
    }

    // ======================
    // OPEN SHARE LINK
    // ======================
    public ShareMetaResponse openShareLink(String token) {

        Share share =
                shareRepository.findByShareToken(token)
                        .orElseThrow();

        validateShare(share);

        share.setOpenCount(share.getOpenCount() + 1);
        share.setLastOpenedAt(LocalDateTime.now());

        shareRepository.save(share);

        List<FileMiniResponse> files;

        if (share.getType() == Share.ShareType.FILE) {

            files = List.of(
                    new FileMiniResponse(
                            share.getFile().getId(),
                            share.getFile().getFileName(),
                            share.getFile().getFileType()
                    )
            );

        } else {

            files = share.getFiles()
                    .stream()
                    .map(f -> new FileMiniResponse(
                            f.getId(),
                            f.getFileName(),
                            f.getFileType()
                    ))
                    .toList();
        }

        return ShareMetaResponse.builder()
                .fileName(
                        share.getType() == Share.ShareType.FILE
                                ? share.getFile().getFileName()
                                : "Bundle (" +
                                share.getFiles().size() +
                                  " files)"
                )
                .fileType(
                        share.getType() == Share.ShareType.FILE
                                ? share.getFile().getFileType()
                                : "BUNDLE"
                )
                .requiresPassword(share.getPassword() != null)
                .canDownload(true)
                .canView(true)
                .message(share.getMessage())
                .files(files)
                .build();
    }

    // ======================
    // ACCESS SHARE
    // ======================
    @Transactional
    public StreamResponse accessSharedFile(
            String token,
            String password
    ) {

        Share share =
                shareRepository.findByShareToken(token)
                        .orElseThrow();

        validateShare(share);

        if (share.getUsedCount() >= share.getMaxUses()) {
            throw new MaxUsesExceededException(
                    "Max uses exceeded"
            );
        }

        if (share.getPassword() != null &&
                !share.getPassword().equals(password)) {

            throw new InvalidSharePasswordException(
                    "Invalid password"
            );
        }

        share.setUsedCount(
                share.getUsedCount() + 1
        );

        shareRepository.save(share);

        String streamToken =
                UUID.randomUUID().toString();

        StreamToken st = StreamToken.builder()
                .token(streamToken)
                .share(share)
                .expiresAt(
                        LocalDateTime.now().plusMinutes(10)
                )
                .build();

        streamTokenRepository.save(st);

        return StreamResponse.builder()
                .streamToken(streamToken)
                .build();
    }

    // ======================
    // REVOKE
    // ======================
    public void revokeShare(Long shareId, Users user) {

        Share share =
                shareRepository.findById(shareId)
                        .orElseThrow();

        if (!share.getOwner().getId().equals(user.getId())) {
            return;
        }

        share.setActive(false);

        shareRepository.save(share);
    }

    // ======================
    // LIST SHARES
    // ======================
    public PagedResponse<ShareResponse> listSharedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<Share> page =
                shareRepository.findByOwner(user, pageable);

        return new PagedResponse<>(

                page.map(this::mapToResponse)
                        .getContent(),

                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    // ======================
    // CLEANUP
    // ======================
    @Transactional
    public void cleanupShares(Users user) {

        LocalDateTime now = LocalDateTime.now();

        List<Share> shares =
                shareRepository.findAllByOwner(user);

        List<Share> toDelete = shares.stream()
                .filter(s ->
                        !s.getActive() ||
                                s.getExpireAt().isBefore(now)
                )
                .toList();

        for (Share share : toDelete) {
            streamTokenRepository.deleteByShare(share);
        }

        shareRepository.deleteAll(toDelete);
    }

    // ======================
    // VALIDATION
    // ======================
    private void validateShare(Share share) {

        if (!share.getActive()) {
            throw new LinkDisabledException(
                    "Link disabled"
            );
        }

        if (share.getExpireAt()
                .isBefore(LocalDateTime.now())) {

            throw new LinkExpiredException(
                    "Link expired"
            );
        }
    }

    // ======================
    // MAPPER
    // ======================
    private ShareResponse mapToResponse(Share s) {

        return ShareResponse.builder()
                .id(s.getId())
                .token(s.getShareToken())
                .emails(s.getRecipientEmails())
                .expiresAt(s.getExpireAt())
                .maxUses(s.getMaxUses())
                .usedCount(s.getUsedCount())
                .openCount(s.getOpenCount())
                .active(s.getActive())

                .fileName(
                        s.getType() == Share.ShareType.FILE
                                ? s.getFile().getFileName()
                                : "Bundle (" +
                                s.getFiles().size() +
                                  " files)"
                )

                .sharedAt(s.getSharedAt())
                .message(s.getMessage())
                .lastOpenedAt(s.getLastOpenedAt())
                .type(s.getType().name())
                .build();
    }
}