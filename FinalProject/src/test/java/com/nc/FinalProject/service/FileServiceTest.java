package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
import com.nc.FinalProject.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileActivityRepository activityRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private StreamTokenRepository streamTokenRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FileService fileService;

    private Path tempUploadDir;

    @BeforeEach
    void setUp() throws IOException {
        tempUploadDir = Files.createTempDirectory("test-uploads");
        ReflectionTestUtils.setField(fileService, "uploadDir", tempUploadDir.toString());
    }

    @Test
    void uploadFiles_Success() throws IOException {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.txt", "text/plain", "Hello World".getBytes()
        );

        when(fileRepository.getUsedStorage(user)).thenReturn(0L);
        when(fileRepository.existsByOwnerAndFolderAndFileNameAndDeletedFalse(user, null, "test.txt")).thenReturn(false);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity f = invocation.getArgument(0);
            f.setId(100L);
            return f;
        });

        List<FileResponse> response = fileService.uploadFiles(new MultipartFile[]{file}, null, user);

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("test.txt", response.get(0).getFileName());
        verify(fileRepository).save(any(FileEntity.class));
        verify(activityRepository).save(any(FileActivity.class));
        verify(notificationService).create(eq(user), anyString(), anyString(), any());
    }

    @Test
    void uploadFiles_ThrowsExceptionWhenStorageLimitExceeded() {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.txt", "text/plain", "Hello World".getBytes()
        );

        // Limit is 1GB. Return 1GB used.
        when(fileRepository.getUsedStorage(user)).thenReturn(1024L * 1024 * 1024);

        assertThrows(FileUploadException.class, () ->
                fileService.uploadFiles(new MultipartFile[]{file}, null, user)
        );
    }

    @Test
    void listFiles_Success() {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        Pageable pageable = PageRequest.of(0, 10);
        FileEntity file = FileEntity.builder().id(100L).fileName("test.txt").size(11L).fileType("text/plain").build();
        Page<FileEntity> page = new PageImpl<>(List.of(file), pageable, 1);

        when(fileRepository.findByOwnerAndFolderAndDeletedFalse(user, null, pageable)).thenReturn(page);

        PagedResponse<FileResponse> response = fileService.listFiles(user, null, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("test.txt", response.getContent().get(0).getFileName());
    }

    @Test
    void recycleBin_Success() {
        Users user = Users.builder().id(1L).build();
        Pageable pageable = PageRequest.of(0, 10);

        Folder folder = Folder.builder().id(10L).name("Deleted Folder").deleted(true).deletedAt(LocalDateTime.now().minusMinutes(5)).build();
        FileEntity file = FileEntity.builder().id(20L).fileName("Deleted File.txt").deleted(true).deletedAt(LocalDateTime.now().minusMinutes(2)).size(10L).fileType("text/plain").build();

        when(folderRepository.findByOwnerAndDeletedTrue(user)).thenReturn(List.of(folder));
        when(fileRepository.findByOwnerAndDeletedTrue(user)).thenReturn(List.of(file));

        PagedResponse<RecycleItemResponse> response = fileService.recycleBin(user, pageable);

        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        // Sorted newest first
        assertEquals("FILE", response.getContent().get(0).getType());
        assertEquals("FOLDER", response.getContent().get(1).getType());
    }

    @Test
    void moveFiles_Success() {
        Users user = Users.builder().id(1L).build();
        Folder target = Folder.builder().id(2L).owner(user).name("Target Folder").build();
        FileEntity file = FileEntity.builder().id(100L).owner(user).fileName("file.txt").deleted(false).build();

        when(folderRepository.findByIdAndOwner(2L, user)).thenReturn(Optional.of(target));
        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file));
        when(fileRepository.existsByOwnerAndFolderAndFileNameAndDeletedFalse(user, target, "file.txt")).thenReturn(false);

        fileService.moveFiles(List.of(100L), 2L, user);

        assertEquals(target, file.getFolder());
        verify(fileRepository).saveAll(anyList());
    }

    @Test
    void getFilePath_Success() throws IOException {
        Users user = Users.builder().id(1L).build();
        Path dummyFile = Files.createFile(tempUploadDir.resolve("actual_file.txt"));
        FileEntity file = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("test.txt")
                .filePath(dummyFile.toString())
                .downloadCount(0)
                .size(10L)
                .build();

        when(fileRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(file));

        Path path = fileService.getFilePath(100L, user);

        assertEquals(dummyFile.toString(), path.toString());
        assertEquals(1, file.getDownloadCount());
        assertNotNull(file.getLastDownloadedAt());
        verify(fileRepository).save(file);
        verify(activityRepository).save(any(FileActivity.class));
        verify(notificationService).create(eq(user), anyString(), anyString(), any());
    }

    @Test
    void downloadMultiple_Success() throws IOException {
        Users user = Users.builder().id(1L).build();
        Path dummyFile1 = Files.createFile(tempUploadDir.resolve("file1.txt"));
        Files.write(dummyFile1, "File 1 content".getBytes());

        FileEntity file1 = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("file1.txt")
                .filePath(dummyFile1.toString())
                .downloadCount(0)
                .size(14L)
                .build();

        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file1));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fileService.downloadMultiple(List.of(100L), user, baos);

        byte[] zipBytes = baos.toByteArray();
        assertTrue(zipBytes.length > 0);
        assertEquals(1, file1.getDownloadCount());
        verify(fileRepository).saveAll(anyList());
    }

    @Test
    void deleteFiles_SoftDelete_Success() {
        Users user = Users.builder().id(1L).build();
        FileEntity file = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("test.txt")
                .deleted(false)
                .starred(true)
                .build();

        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file));
        when(shareRepository.existsByFileAndActiveTrue(file)).thenReturn(false);
        when(shareRepository.existsByFilesContainsAndActiveTrue(file)).thenReturn(false);
        when(shareRepository.findAllByFileOrFilesContains(file, file)).thenReturn(Collections.emptyList());

        fileService.deleteFiles(List.of(100L), user, false);

        assertTrue(file.getDeleted());
        assertFalse(file.isStarred());
        assertNotNull(file.getDeletedAt());
        verify(fileRepository).save(file);
    }

    @Test
    void deleteFiles_ThrowsSharedFileDeleteException() {
        Users user = Users.builder().id(1L).build();
        FileEntity file = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("test.txt")
                .build();

        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file));
        when(shareRepository.existsByFileAndActiveTrue(file)).thenReturn(true);

        assertThrows(SharedFileDeleteException.class, () ->
                fileService.deleteFiles(List.of(100L), user, false)
        );
    }

    @Test
    void restoreFiles_Success() {
        Users user = Users.builder().id(1L).build();
        FileEntity file = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("test.txt")
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .build();

        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file));

        fileService.restoreFiles(List.of(100L), user);

        assertFalse(file.getDeleted());
        assertNull(file.getDeletedAt());
        verify(fileRepository).save(file);
    }

    @Test
    void deletePermanent_Success() throws IOException {
        Users user = Users.builder().id(1L).build();
        Path dummyFile = Files.createFile(tempUploadDir.resolve("to_delete.txt"));
        FileEntity file = FileEntity.builder()
                .id(100L)
                .owner(user)
                .fileName("to_delete.txt")
                .filePath(dummyFile.toString())
                .build();

        when(fileRepository.findAllById(List.of(100L))).thenReturn(List.of(file));
        when(shareRepository.findAllByFileOrFilesContains(file, file)).thenReturn(Collections.emptyList());

        fileService.deletePermanent(List.of(100L), user);

        verify(activityRepository).deleteByFile_Id(100L);
        verify(streamTokenRepository).deleteByFileId(100L);
        verify(fileRepository).delete(file);
        assertFalse(Files.exists(dummyFile));
    }
}
