package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.CreateFolderRequest;
import com.nc.FinalProject.dto.response.*;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.BadRequestException;
import com.nc.FinalProject.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;

    public FolderResponse createFolder(CreateFolderRequest request, Users user) {

        Folder parent = null;

        if (request.getParentId() != null) {
            parent = folderRepository
                    .findByIdAndOwner(request.getParentId(), user)
                    .orElseThrow();
        }

        String originalName = request.getName();
        String name = originalName;
        int count = 1;

        while (folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, parent, name)) {
            name = originalName + " (" + count + ")";
            count++;
        }

        Folder folder = folderRepository.save(
                Folder.builder()
                        .name(name)
                        .owner(user)
                        .parent(parent)
                        .createdAt(LocalDateTime.now())
                        .deleted(false)
                        .build()
        );

        return map(folder);
    }

    public PagedResponse<FolderResponse> listFolders(
            Long folderId,
            Users user,
            Pageable pageable
    ) {

        Folder folder = null;

        if (folderId != null) {
            folder = folderRepository
                    .findByIdAndOwner(folderId, user)
                    .orElseThrow();
        }

        Page<Folder> page = (folder == null)
                ? folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(
                user,
                pageable
        )
                : folderRepository.findByOwnerAndParentAndDeletedFalse(
                user,
                folder,
                pageable
        );

        Page<FolderResponse> dtoPage =
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

    public void downloadFolder(Long folderId, Users user, OutputStream outputStream) throws IOException {
        Folder folder = folderRepository.findByIdAndOwner(folderId, user).orElseThrow();
        ZipOutputStream zos = new ZipOutputStream(outputStream);

        addFolderToZip(folder, folder.getName() + "/", zos, user);

        zos.finish();
        zos.close();
    }

    private void addFolderToZip(Folder folder, String path, ZipOutputStream zos, Users user) throws IOException {
        List<FileEntity> files = fileRepository.findByOwnerAndFolderAndDeletedFalse(user, folder);
        byte[] buffer = new byte[8192];

        for (FileEntity file : files) {
            Path filePath = Paths.get(file.getFilePath());
            zos.putNextEntry(new ZipEntry(path + file.getFileName()));

            try (InputStream fis = Files.newInputStream(filePath)) {
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }

        List<Folder> children = folderRepository.findByOwnerAndParentAndDeletedFalse(user, folder);
        for (Folder child : children) {
            addFolderToZip(child, path + child.getName() + "/", zos, user);
        }
    }

    public void renameFolder(Long id, String name, Users user) {
        Folder folder = folderRepository.findByIdAndOwner(id, user).orElseThrow();
        folder.setName(name);
        folderRepository.save(folder);
    }

    public void moveFolder(Long id, Long targetFolderId, Users user) {
        Folder folder = folderRepository.findByIdAndOwner(id, user).orElseThrow();

        Folder target = targetFolderId == null
                ? null
                : folderRepository.findByIdAndOwner(targetFolderId, user).orElseThrow();

        // prevent moving into itself
        if (target != null && target.getId().equals(folder.getId())) {
            throw new BadRequestException("Cannot move folder into itself");
        }

        // prevent moving into its own child/descendant
        if (target != null && isDescendant(folder, target)) {
            throw new BadRequestException("Cannot move folder into its child");
        }

        folder.setParent(target);
        folderRepository.save(folder);
    }

    private boolean isDescendant(Folder source, Folder target) {
        Folder current = target;
        while (current != null) {
            if (current.getId().equals(source.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    public List<FolderTreeResponse> getFolderTree(Users user) {

        List<Folder> roots =
                folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(user);

        return roots.stream()
                .map(f -> buildTree(f, user))
                .toList();
    }

    private FolderTreeResponse buildTree(Folder folder, Users user) {

        List<Folder> children =
                folderRepository.findByOwnerAndParentAndDeletedFalse(user, folder);

        List<FolderTreeResponse> childDtos =
                children.stream()
                        .map(child -> buildTree(child, user))
                        .toList();

        return new FolderTreeResponse(
                folder.getId(),
                folder.getName(),
                childDtos
        );
    }

    @Transactional
    public void deleteFolders(
            List<Long> ids,
            Users user
    ) {

        List<Folder> folders =
                folderRepository.findAllById(ids);

        for (Folder folder : folders) {

            if (!folder.getOwner().getId()
                    .equals(user.getId())) {
                continue;
            }

            if (!folder.isDeleted()) {
                recursiveDelete(folder, user);
            }
        }
    }

    private void recursiveDelete(Folder folder, Users user) {
        folder.setDeleted(true);
        folder.setDeletedAt(LocalDateTime.now());
        folderRepository.save(folder);

        List<FileEntity> files = fileRepository.findByOwnerAndFolderAndDeletedFalse(user, folder);
        for (FileEntity file : files) {
            file.setDeleted(true);
            file.setDeletedAt(LocalDateTime.now());
            fileRepository.save(file);
        }

        List<Folder> children = folderRepository.findByOwnerAndParentAndDeletedFalse(user, folder);
        for (Folder child : children) {
            recursiveDelete(child, user);
        }
    }

    @Transactional
    public void permanentlyDeleteFolders(
            List<Long> ids,
            Users user
    ) {

        List<Folder> folders =
                folderRepository.findAllById(ids);

        for (Folder folder : folders) {

            if (!folder.getOwner().getId()
                    .equals(user.getId())) {
                continue;
            }

            if (!folder.isDeleted()) {
                continue;
            }

            recursivePermanentDelete(folder);
        }
    }

    private void recursivePermanentDelete(Folder folder) {
        // delete files in current folder
        List<FileEntity> files = fileRepository.findByOwnerAndFolder(folder.getOwner(), folder);

        for (FileEntity file : files) {
            fileService.autoDeleteFile(file);
        }

        // delete child folders first
        List<Folder> children = folderRepository.findByOwnerAndParent(folder.getOwner(), folder);

        for (Folder child : children) {
            recursivePermanentDelete(child);
        }

        // delete folder record
        folderRepository.delete(folder);
    }

    @Transactional
    public void restoreFolders(
            List<Long> ids,
            Users user
    ) {

        List<Folder> folders =
                folderRepository.findAllById(ids);

        for (Folder folder : folders) {

            if (!folder.getOwner().getId()
                    .equals(user.getId())) {
                continue;
            }

            if (!folder.isDeleted()) {
                continue;
            }

            restoreParentChain(folder, user);
            recursiveRestore(folder, user);
        }
    }

    private void restoreParentChain(Folder folder, Users user) {

        Folder parent = folder.getParent();

        if (parent != null && parent.isDeleted()) {
            parent.setDeleted(false);
            parent.setDeletedAt(null);
            folderRepository.save(parent);

            restoreParentChain(parent, user);
        }
    }

    private void recursiveRestore(Folder folder, Users user) {

        Folder parent = folder.getParent();

        String restoredName = generateUniqueFolderName(folder.getName(), user, parent);

        folder.setName(restoredName);
        folder.setDeleted(false);
        folder.setDeletedAt(null);
        folderRepository.save(folder);

        // ✅ FIX: restore ONLY deleted files inside folder
        List<FileEntity> files =
                fileRepository.findByOwnerAndFolder(user, folder);

        for (FileEntity file : files) {
            if (!file.getDeleted()) continue;

            file.setDeleted(false);
            file.setDeletedAt(null);
        }
        fileRepository.saveAll(files);

        // ✅ FIX: restore ONLY deleted children first
        List<Folder> children =
                folderRepository.findByOwnerAndParent(user, folder);

        for (Folder child : children) {
            if (child.isDeleted()) {
                recursiveRestore(child, user);
            }
        }
    }

    private String generateUniqueFolderName(String original, Users user, Folder parent) {

        String name = original;
        int count = 1;

        while (folderRepository.existsByOwnerAndParentAndNameAndDeletedFalse(user, parent, name)) {
            name = original + " (Restored " + count + ")";
            count++;
        }

        return name;
    }

    private FolderResponse map(Folder folder) {

        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParent() == null
                        ? null
                        : folder.getParent().getId(),
                folder.getCreatedAt()
        );
    }

    private FileResponse mapFile(
            FileEntity f
    ) {

        return new FileResponse(
                f.getId(),
                f.getFileName(),
                f.getStoredName(),
                f.getFileType(),
                f.getSize(),
                f.getFilePath(),
                f.getUploadedAt(),
                f.isStarred()
        );
    }
}