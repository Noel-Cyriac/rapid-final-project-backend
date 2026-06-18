package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ShareRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
import com.nc.FinalProject.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StreamTokenRepository streamTokenRepository;

    @Mock
    private ShareRecipientRepository shareRecipientRepository;

    @Mock
    private SharePasswordTokenRepository sharePasswordTokenRepository;

    @Mock
    private MailService mailService;

    @InjectMocks
    private ShareService shareService;

    @Test
    void createShareUnified_SingleFile_Success() {
        Users user = Users.builder().id(1L).build();
        ShareRequest req = new ShareRequest();
        req.setFileId(10L);
        req.setExpireHours(24);
        req.setMaxUses(5);
        req.setPassword("");
        req.setMessage("Test message");
        req.setEmails(List.of("recipient@example.com"));

        FileEntity file = FileEntity.builder().id(10L).fileName("file.txt").build();

        when(fileRepository.findByIdAndOwner(10L, user)).thenReturn(Optional.of(file));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        ShareResponse response = shareService.createShareUnified(req, user);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("file.txt", response.getFileName());
        assertEquals("FILE", response.getType());
        verify(shareRecipientRepository).saveAll(anyList());
        verify(mailService).sendShareEmail(eq("recipient@example.com"), anyString(), eq("Test message"));
    }

    @Test
    void createShareUnified_Bundle_Success() {
        Users user = Users.builder().id(1L).build();
        ShareRequest req = new ShareRequest();
        req.setFileIds(List.of(10L, 20L));
        req.setExpireHours(24);
        req.setMaxUses(5);
        req.setPassword("securePass");
        req.setMessage("Test message");
        req.setEmails(List.of("recipient@example.com"));

        FileEntity file1 = FileEntity.builder().id(10L).fileName("file1.txt").build();
        FileEntity file2 = FileEntity.builder().id(20L).fileName("file2.txt").build();

        when(fileRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(file1, file2));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        ShareResponse response = shareService.createShareUnified(req, user);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Bundle (2)", response.getFileName());
        assertEquals("BUNDLE", response.getType());
        verify(sharePasswordTokenRepository).save(any(SharePasswordToken.class));
        verify(mailService).sendSharePasswordEmail(eq("recipient@example.com"), anyString(), anyString(), eq("Test message"));
    }

    @Test
    void openShareLink_Success() {
        Share share = Share.builder().id(100L).active(true).expireAt(LocalDateTime.now().plusHours(2)).type(Share.ShareType.FILE).file(FileEntity.builder().id(1L).fileName("file.txt").build()).build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).openCount(0).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));

        ShareMetaResponse response = shareService.openShareLink("token123");

        assertNotNull(response);
        assertEquals("file.txt", response.getFileName());
        assertEquals(1, recipient.getOpenCount());
        verify(shareRecipientRepository).save(recipient);
    }

    @Test
    void openShareLink_ThrowsLinkDisabledExceptionWhenInactive() {
        Share share = Share.builder().id(100L).active(false).expireAt(LocalDateTime.now().plusHours(2)).build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));

        assertThrows(LinkDisabledException.class, () -> shareService.openShareLink("token123"));
    }

    @Test
    void openShareLink_ThrowsLinkExpiredExceptionWhenExpired() {
        Share share = Share.builder().id(100L).active(true).expireAt(LocalDateTime.now().minusHours(2)).build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));

        assertThrows(LinkExpiredException.class, () -> shareService.openShareLink("token123"));
    }

    @Test
    void accessSharedFile_Success() {
        Share share = Share.builder().id(100L).active(true).expireAt(LocalDateTime.now().plusHours(2)).maxUses(5).password("pass123").canView(true).build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).usedCount(0).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));
        when(streamTokenRepository.save(any(StreamToken.class))).thenAnswer(invocation -> {
            StreamToken st = invocation.getArgument(0);
            st.setId(1L);
            return st;
        });

        StreamResponse response = shareService.accessSharedFile("token123", "pass123");

        assertNotNull(response);
        assertNotNull(response.getStreamToken());
        assertEquals(1, recipient.getUsedCount());
        verify(shareRecipientRepository).save(recipient);
        verify(streamTokenRepository).save(any(StreamToken.class));
    }

    @Test
    void accessSharedFile_ThrowsInvalidSharePasswordException() {
        Share share = Share.builder().id(100L).active(true).expireAt(LocalDateTime.now().plusHours(2)).password("pass123").build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));

        assertThrows(InvalidSharePasswordException.class, () -> shareService.accessSharedFile("token123", "wrongPass"));
    }

    @Test
    void accessSharedFile_ThrowsMaxUsesExceededException() {
        Share share = Share.builder().id(100L).active(true).expireAt(LocalDateTime.now().plusHours(2)).maxUses(2).password("pass123").build();
        ShareRecipient recipient = ShareRecipient.builder().id(1L).accessToken("token123").share(share).usedCount(2).active(true).build();

        when(shareRecipientRepository.findByAccessToken("token123")).thenReturn(Optional.of(recipient));

        assertThrows(MaxUsesExceededException.class, () -> shareService.accessSharedFile("token123", "pass123"));
    }

    @Test
    void revokeShare_Success() {
        Users user = Users.builder().id(1L).build();
        ShareRecipient r = ShareRecipient.builder().id(1L).active(true).build();
        Share share = Share.builder().id(100L).owner(user).active(true).recipients(List.of(r)).build();

        when(shareRepository.findById(100L)).thenReturn(Optional.of(share));

        shareService.revokeShare(100L, user);

        assertFalse(share.getActive());
        assertFalse(r.getActive());
        verify(shareRepository).save(share);
    }

    @Test
    void revokeRecipient_Success() {
        Users user = Users.builder().id(1L).build();
        Share share = Share.builder().id(100L).owner(user).build();
        ShareRecipient r = ShareRecipient.builder().id(200L).share(share).active(true).build();

        when(shareRecipientRepository.findById(200L)).thenReturn(Optional.of(r));

        shareService.revokeRecipient(200L, user);

        assertFalse(r.getActive());
        verify(shareRecipientRepository).save(r);
        verify(streamTokenRepository).deleteByRecipient_Id(200L);
    }

    @Test
    void cleanupShares_Success() {
        Users user = Users.builder().id(1L).build();
        Share share1 = Share.builder().id(100L).owner(user).active(false).expireAt(LocalDateTime.now().plusHours(1)).build();
        Share share2 = Share.builder().id(200L).owner(user).active(true).expireAt(LocalDateTime.now().minusHours(1)).build();

        when(shareRepository.findAllByOwner(user)).thenReturn(List.of(share1, share2));

        shareService.cleanupShares(user);

        verify(streamTokenRepository).deleteByRecipient_Share_Id(100L);
        verify(streamTokenRepository).deleteByRecipient_Share_Id(200L);
        verify(shareRepository).deleteAll(anyList());
    }

    @Test
    void revealPassword_Success() {
        ShareRecipient r = ShareRecipient.builder().accessToken("access123").build();
        SharePasswordToken token = SharePasswordToken.builder()
                .token("pwdToken123")
                .password("secret123")
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .recipient(r)
                .build();

        when(sharePasswordTokenRepository.findByToken("pwdToken123")).thenReturn(Optional.of(token));

        SharePasswordResponse response = shareService.revealPassword("pwdToken123");

        assertNotNull(response);
        assertEquals("secret123", response.getPassword());
        assertTrue(token.isUsed());
    }

    @Test
    void revealPassword_ThrowsPasswordAlreadyViewedException() {
        SharePasswordToken token = SharePasswordToken.builder()
                .token("pwdToken123")
                .used(true)
                .build();

        when(sharePasswordTokenRepository.findByToken("pwdToken123")).thenReturn(Optional.of(token));

        assertThrows(PasswordAlreadyViewedException.class, () -> shareService.revealPassword("pwdToken123"));
    }
}
