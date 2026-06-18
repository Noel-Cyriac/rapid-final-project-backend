package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.CreateFolderRequest;
import com.nc.FinalProject.dto.response.FolderResponse;
import com.nc.FinalProject.dto.response.FolderTreeResponse;
import com.nc.FinalProject.dto.response.PagedResponse;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Folder;
import com.nc.FinalProject.entity.Share;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.exception.MoveException;
import com.nc.FinalProject.exception.SharedFileDeleteException;
import com.nc.FinalProject.repository.FileRepository;
import com.nc.FinalProject.repository.FolderRepository;
import com.nc.FinalProject.repository.ShareRepository;
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
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileService fileService;

    @Mock
    private ShareRepository shareRepository;

    @InjectMocks
    private FolderService folderService;

    @Test
    void createFolder_Success() {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("My Folder");
        request.setParentId(null);

        when(folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, null, "My Folder")).thenReturn(false);
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setId(100L);
            return folder;
        });

        FolderResponse response = folderService.createFolder(request, user);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("My Folder", response.getName());
        assertNull(response.getParentId());
    }

    @Test
    void createFolder_DuplicateNameHandling() {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("My Folder");
        request.setParentId(null);

        // Naming collision occurs once, then second check passes
        when(folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, null, "My Folder")).thenReturn(true);
        when(folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, null, "My Folder (1)")).thenReturn(false);

        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setId(101L);
            return folder;
        });

        FolderResponse response = folderService.createFolder(request, user);

        assertNotNull(response);
        assertEquals("My Folder (1)", response.getName());
    }

    @Test
    void listFolders_Root() {
        Users user = Users.builder().id(1L).email("user@example.com").build();
        Pageable pageable = PageRequest.of(0, 10);
        Folder folder = Folder.builder().id(2L).name("My Folder").build();
        Page<Folder> page = new PageImpl<>(List.of(folder), pageable, 1);

        when(folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(user, pageable)).thenReturn(page);

        PagedResponse<FolderResponse> response = folderService.listFolders(null, user, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("My Folder", response.getContent().get(0).getName());
    }

    @Test
    void renameFolder_Success() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).name("Old Name").owner(user).build();

        when(folderRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(folder));

        folderService.renameFolder(100L, "New Name", user);

        assertEquals("New Name", folder.getName());
        verify(folderRepository).save(folder);
    }

    @Test
    void moveFolder_Success() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).name("Folder A").owner(user).build();
        Folder target = Folder.builder().id(200L).name("Folder B").owner(user).build();

        when(folderRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(folder));
        when(folderRepository.findByIdAndOwner(200L, user)).thenReturn(Optional.of(target));

        folderService.moveFolder(100L, 200L, user);

        assertEquals(target, folder.getParent());
        verify(folderRepository).save(folder);
    }

    @Test
    void moveFolder_ThrowsMoveExceptionWhenTargetIsSelf() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).name("Folder A").owner(user).build();

        when(folderRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(folder));

        assertThrows(MoveException.class, () -> folderService.moveFolder(100L, 100L, user));
    }

    @Test
    void moveFolder_ThrowsMoveExceptionWhenTargetIsDescendant() {
        Users user = Users.builder().id(1L).build();
        Folder parent = Folder.builder().id(100L).name("Parent").owner(user).build();
        Folder child = Folder.builder().id(200L).name("Child").owner(user).parent(parent).build();

        when(folderRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(parent));
        when(folderRepository.findByIdAndOwner(200L, user)).thenReturn(Optional.of(child));

        assertThrows(MoveException.class, () -> folderService.moveFolder(100L, 200L, user));
    }

    @Test
    void getFolderTree_Success() {
        Users user = Users.builder().id(1L).build();
        Folder root = Folder.builder().id(100L).name("Root").owner(user).build();
        Folder child = Folder.builder().id(200L).name("Child").owner(user).build();

        when(folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(user)).thenReturn(List.of(root));
        when(folderRepository.findByOwnerAndParentAndDeletedFalse(user, root)).thenReturn(List.of(child));
        when(folderRepository.findByOwnerAndParentAndDeletedFalse(user, child)).thenReturn(Collections.emptyList());

        List<FolderTreeResponse> tree = folderService.getFolderTree(user);

        assertNotNull(tree);
        assertEquals(1, tree.size());
        assertEquals("Root", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("Child", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    void deleteFolders_SoftDeleteSuccess() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).owner(user).name("Folder").deleted(false).build();
        FileEntity file = FileEntity.builder().id(1L).owner(user).fileName("file.txt").deleted(false).build();

        when(folderRepository.findAllById(List.of(100L))).thenReturn(List.of(folder));
        when(fileRepository.findByOwnerAndFolderAndDeletedFalse(user, folder)).thenReturn(List.of(file));
        when(folderRepository.findByOwnerAndParentAndDeletedFalse(user, folder)).thenReturn(Collections.emptyList());

        folderService.deleteFolders(List.of(100L), user, false);

        assertTrue(folder.isDeleted());
        assertTrue(file.getDeleted());
        assertNotNull(folder.getDeletedAt());
        assertNotNull(file.getDeletedAt());
        verify(folderRepository).save(folder);
        verify(fileRepository).save(file);
    }

    @Test
    void deleteFolders_ThrowsSharedFileDeleteExceptionWhenShared() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).owner(user).name("Folder").build();
        FileEntity file = FileEntity.builder().id(1L).owner(user).fileName("shared.txt").build();

        when(folderRepository.findAllById(List.of(100L))).thenReturn(List.of(folder));
        when(fileRepository.findByOwnerAndFolderAndDeletedFalse(user, folder)).thenReturn(List.of(file));
        when(shareRepository.existsByFileAndActiveTrue(file)).thenReturn(true);

        assertThrows(SharedFileDeleteException.class, () -> folderService.deleteFolders(List.of(100L), user, false));
    }

    @Test
    void restoreFolders_Success() {
        Users user = Users.builder().id(1L).build();
        Folder parent = Folder.builder().id(100L).owner(user).name("Parent").deleted(true).build();
        Folder child = Folder.builder().id(200L).owner(user).name("Child").parent(parent).deleted(true).build();
        FileEntity file = FileEntity.builder().id(1L).owner(user).fileName("file.txt").deleted(true).build();

        when(folderRepository.findAllById(List.of(200L))).thenReturn(List.of(child));
        when(folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, parent, "Child")).thenReturn(false);
        when(fileRepository.findByOwnerAndFolder(user, child)).thenReturn(List.of(file));
        when(folderRepository.findByOwnerAndParent(user, child)).thenReturn(Collections.emptyList());

        folderService.restoreFolders(List.of(200L), user);

        assertFalse(parent.isDeleted());
        assertFalse(child.isDeleted());
        assertFalse(file.getDeleted());
        verify(folderRepository).save(parent);
        verify(folderRepository).save(child);
        verify(fileRepository).saveAll(any());
    }

    @Test
    void permanentlyDeleteFolders_Success() {
        Users user = Users.builder().id(1L).build();
        Folder folder = Folder.builder().id(100L).owner(user).name("Folder").deleted(true).build();
        FileEntity file = FileEntity.builder().id(1L).owner(user).fileName("file.txt").build();

        when(folderRepository.findAllById(List.of(100L))).thenReturn(List.of(folder));
        when(fileRepository.findByOwnerAndFolder(user, folder)).thenReturn(List.of(file));
        when(folderRepository.findByOwnerAndParent(user, folder)).thenReturn(Collections.emptyList());

        folderService.permanentlyDeleteFolders(List.of(100L), user);

        verify(fileService).autoDeleteFile(file);
        verify(folderRepository).delete(folder);
    }
}
