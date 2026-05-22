package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.InvalidSharePasswordException;
import com.nc.FinalProject.exception.LinkDisabledException;
import com.nc.FinalProject.exception.LinkExpiredException;
import com.nc.FinalProject.exception.MaxUsesExceededException;
import com.nc.FinalProject.repository.FileRepository;
import com.nc.FinalProject.repository.ShareRecipientRepository;
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
    private final ShareRecipientRepository shareRecipientRepository;
    private final MailService mailService;

    // ================= CREATE =================
    @Transactional
    public ShareResponse createShareUnified(ShareRequest req, Users user) {
        Share share = Share.builder()
                .owner(user)
                .expireAt(LocalDateTime.now().plusHours(req.getExpireHours()))
                .maxUses(req.getMaxUses())
                .password(req.getPassword())
                .message(req.getMessage())
                .canDownload(req.getCanDownload() == null || req.getCanDownload())
                .canView(req.getCanView() == null || req.getCanView())
                .active(true)
                .sharedAt(LocalDateTime.now())
                .build();

        attachFiles(share, req, user);
        share = shareRepository.save(share);
        createRecipients(share, req);

        return map(share);
    }

    // ================= OPEN =================
    @Transactional
    public ShareMetaResponse openShareLink(String token) {

        ShareRecipient r = getRecipient(token);
        Share s = r.getShare();

        validate(s, r);

        r.setOpened(true);
        r.setOpenCount(r.getOpenCount() + 1);
        r.setLastOpenedAt(LocalDateTime.now());
        shareRecipientRepository.save(r);

        return mapMeta(s);
    }

    // ================= ACCESS =================
    @Transactional
    public StreamResponse accessSharedFile(String token, String password) {
        ShareRecipient r = getRecipient(token);
        Share s = r.getShare();
        validate(s, r);

        if (s.getPassword() != null && !s.getPassword().equals(password)) {
            throw new InvalidSharePasswordException("Invalid password");
        }
        if (r.getUsedCount() >= s.getMaxUses()) {
            throw new MaxUsesExceededException("Max uses exceeded");
        }
        if (!Boolean.TRUE.equals(s.getCanView()) && !Boolean.TRUE.equals(s.getCanDownload())) {
            throw new RuntimeException("Share access disabled");
        }

        r.setUsedCount(r.getUsedCount() + 1);
        shareRecipientRepository.save(r);

        StreamToken st = streamTokenRepository.save(StreamToken.builder()
                .token(UUID.randomUUID().toString())
                .recipient(r)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        return StreamResponse.builder().streamToken(st.getToken()).build();
    }

    // ================= REVOKE =================
    public void revokeShare(Long id, Users user) {
        Share s = shareRepository.findById(id).orElseThrow();
        if (!s.getOwner().getId().equals(user.getId())) return;
        s.setActive(false);
        shareRepository.save(s);
    }

    // ================= LIST =================
    public PagedResponse<ShareResponse> listSharedFiles(
            Users user,
            Pageable pageable
    ) {

        Page<Share> page =
                shareRepository.findByOwner(user, pageable);

        Page<ShareResponse> dtoPage =
                page.map(this::map);

        return new PagedResponse<>(
                dtoPage.getContent(),
                dtoPage.getNumber(),
                dtoPage.getSize(),
                dtoPage.getTotalElements(),
                dtoPage.getTotalPages(),
                dtoPage.isFirst(),
                dtoPage.isLast()
        );
    }

    // ================= CLEANUP =================
    @Transactional
    public void cleanupShares(Users user) {
        LocalDateTime now = LocalDateTime.now();
        List<Share> list = shareRepository.findAllByOwner(user)
                .stream()
                .filter(s -> !s.getActive() || s.getExpireAt().isBefore(now))
                .toList();
        list.forEach(share -> streamTokenRepository.deleteByRecipient_Share_Id(share.getId()));
        shareRepository.deleteAll(list);
    }

    // ================= HELPERS =================
    private ShareRecipient getRecipient(String token) {
        return shareRecipientRepository.findByAccessToken(token).orElseThrow();
    }

    private void validate(Share s, ShareRecipient r) {
        if (!s.getActive()) throw new LinkDisabledException("Disabled");
        if (s.getExpireAt().isBefore(LocalDateTime.now())) throw new LinkExpiredException("Expired");
        if (!r.getActive()) throw new LinkDisabledException("Recipient revoked");
    }

    private void attachFiles(Share s, ShareRequest req, Users user) {
        boolean bundle = req.getFileIds() != null && req.getFileIds().size() > 1;

        if (!bundle) {
            Long id = req.getFileId() != null ? req.getFileId() : req.getFileIds().get(0);
            s.setType(Share.ShareType.FILE);
            s.setFile(fileRepository.findByIdAndOwner(id, user).orElseThrow());
            return;
        }

        s.setType(Share.ShareType.BUNDLE);
        s.setFiles(fileRepository.findAllById(req.getFileIds()));
    }

    private void createRecipients(Share share, ShareRequest req) {
        List<ShareRecipient> recipients = req.getEmails().stream().map(email -> {
            ShareRecipient r = ShareRecipient.builder()
                    .email(email)
                    .accessToken(UUID.randomUUID().toString())
                    .opened(false).openCount(0).usedCount(0)
                    .lastOpenedAt(null).active(true)
                    .share(share)
                    .build();

            String url = "http://localhost:5175/share/" + r.getAccessToken();
            mailService.sendShareEmail(email, url, req.getMessage());
            return r;
        }).toList();

        shareRecipientRepository.saveAll(recipients);
        share.setRecipients(recipients);
    }
    // ================= MAPPING =================
    private ShareResponse map(Share s) {

        return ShareResponse.builder()
                .id(s.getId())
                .expiresAt(s.getExpireAt())
                .maxUses(s.getMaxUses())
                .active(s.getActive())
                .fileName(
                        s.getType() == Share.ShareType.FILE
                                ? s.getFile().getFileName()
                                : "Bundle (" + s.getFiles().size() + ")"
                )
                .sharedAt(s.getSharedAt())
                .message(s.getMessage())
                .type(s.getType().name())

                .recipients(
                        s.getRecipients()
                                .stream()
                                .map(r -> RecipientResponse.builder()
                                        .id(r.getId())
                                        .email(r.getEmail())
                                        .token(r.getAccessToken())
                                        .opened(r.getOpened())
                                        .openCount(r.getOpenCount())
                                        .usedCount(r.getUsedCount())
                                        .lastOpenedAt(r.getLastOpenedAt())
                                        .active(r.getActive())
                                        .build()
                                )
                                .toList()
                )
                .build();
    }

    private ShareMetaResponse mapMeta(Share s) {
        return ShareMetaResponse.builder()
                .fileName(s.getType() == Share.ShareType.FILE
                        ? s.getFile().getFileName()
                        : "Bundle (" + s.getFiles().size() + ")")
                .fileType(s.getType() == Share.ShareType.FILE
                        ? s.getFile().getFileType()
                        : "BUNDLE")
                .requiresPassword(s.getPassword() != null)
                .canDownload(true)
                .canView(true)
                .message(s.getMessage())
                .build();
    }
}