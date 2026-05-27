package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
import com.nc.FinalProject.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final StreamTokenRepository streamTokenRepository;
    private final ShareRecipientRepository shareRecipientRepository;
    private final SharePasswordTokenRepository sharePasswordTokenRepository;
    private final MailService mailService;

    // ================= CREATE =================
    @Transactional
    public ShareResponse createShareUnified(ShareRequest req, Users user) {
        Share share = Share.builder()
                .owner(user)
                .expireAt(LocalDateTime.now().plusHours(req.getExpireHours()))
                .maxUses(req.getMaxUses())
                .password(req.getPassword() != null && !req.getPassword().isBlank() ? req.getPassword() : null)
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

        r.setOpenCount(r.getOpenCount() + 1);
        r.setLastOpenedAt(LocalDateTime.now());
        shareRecipientRepository.save(r);

        return mapMeta(s, r);
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
    @Transactional
    public void revokeShare(Long id, Users user) {
        Share s = shareRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Share not found"));

        if (!s.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        s.setActive(false);

        if (s.getRecipients() != null) {
            s.getRecipients().forEach(r -> r.setActive(false));
        }

        shareRepository.save(s);
    }

    @Transactional
    public void revokeRecipient(Long recipientId, Users user) {

        ShareRecipient r = shareRecipientRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Share share = r.getShare();

        if (!share.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        r.setActive(false);

        shareRecipientRepository.save(r);

        streamTokenRepository.deleteByRecipient_Id(recipientId);
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
        List<ShareRecipient> recipients = req.getEmails().stream()
                .map(email -> ShareRecipient.builder()
                        .email(email)
                        .accessToken(UUID.randomUUID().toString())
                        .openCount(0)
                        .usedCount(0)
                        .lastOpenedAt(null)
                        .active(true)
                        .share(share)
                        .build())
                .toList();

        shareRecipientRepository.saveAll(recipients);
        share.setRecipients(recipients);

        for (ShareRecipient recipient : recipients) {
            String shareUrl = "http://localhost:5175/share/" + recipient.getAccessToken();

            if (share.getPassword() != null && !share.getPassword().isBlank()) {
                String passwordToken = UUID.randomUUID().toString();

                SharePasswordToken token = SharePasswordToken.builder()
                        .token(passwordToken)
                        .password(share.getPassword())
                        .used(false)
                        .expiresAt(share.getExpireAt())
                        .recipient(recipient)
                        .build();

                sharePasswordTokenRepository.save(token);

                String passwordUrl = "http://localhost:5175/share-password/" + passwordToken;

                mailService.sendSharePasswordEmail(recipient.getEmail(), shareUrl, passwordUrl, req.getMessage());
            } else {
                mailService.sendShareEmail(recipient.getEmail(), shareUrl, req.getMessage());
            }
        }
    }

    @Transactional
    public SharePasswordResponse revealPassword(String token) {

        SharePasswordToken t = sharePasswordTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid link"));

        if (t.isUsed()) {
            throw new PasswordAlreadyViewedException("Password already viewed");
        }

        if (t.getExpiresAt() != null &&
                t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordLinkExpiredException("Password link expired");
        }

        t.setUsed(true);

        String shareUrl =
                "http://localhost:5175/share/" +
                        t.getRecipient().getAccessToken();

        return SharePasswordResponse.builder()
                .password(t.getPassword())
                .shareUrl(shareUrl)
                .build();
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

    private ShareMetaResponse mapMeta(Share s, ShareRecipient r) {
        List<FileMiniResponse> files;

        if (s.getType() == Share.ShareType.FILE) {
            files = List.of(new FileMiniResponse(
                    s.getFile().getId(),
                    s.getFile().getFileName(),
                    s.getFile().getFileType()
            ));
        } else {
            files = s.getFiles().stream()
                    .map(file -> new FileMiniResponse(
                            file.getId(),
                            file.getFileName(),
                            file.getFileType()
                    ))
                    .toList();
        }

        return ShareMetaResponse.builder()
                .fileName(s.getType() == Share.ShareType.FILE
                        ? s.getFile().getFileName()
                        : "Bundle (" + s.getFiles().size() + ")")
                .fileType(s.getType() == Share.ShareType.FILE
                        ? s.getFile().getFileType()
                        : "BUNDLE")
                .requiresPassword(s.getPassword() != null)
                .canDownload(Boolean.TRUE.equals(s.getCanDownload()))
                .canView(Boolean.TRUE.equals(s.getCanView()))
                .message(s.getMessage())
                .files(files)
                .expiresAt(s.getExpireAt())
                .maxUses(s.getMaxUses())
                .usedCount(r.getUsedCount())
                .build();
    }
}