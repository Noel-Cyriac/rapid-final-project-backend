package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Folder;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FolderRepository
        extends JpaRepository<Folder, Long> {

    List<Folder> findByOwnerAndParentAndDeletedFalse(
            Users owner,
            Folder parent
    );

    Page<Folder> findByOwnerAndParentIsNullAndDeletedFalse(
            Users user,
            Pageable pageable
    );

    Page<Folder> findByOwnerAndParentAndDeletedFalse(
            Users user,
            Folder parent,
            Pageable pageable
    );

    Optional<Folder>
    findByIdAndOwner(Long id, Users owner);

    boolean existsByOwnerAndParentAndNameAndDeletedFalse(
            Users owner,
            Folder parent,
            String name
    );

    Optional<Folder>
    findByOwnerAndParentAndNameAndDeletedFalse(
            Users owner,
            Folder parent,
            String name
    );

    List<Folder>
    findByOwnerAndParent(
            Users owner,
            Folder parent
    );

    List<Folder> findByOwnerAndParentIsNullAndDeletedFalse(Users user);

    List<Folder> findByOwnerAndDeletedTrue(Users owner);
    List<Folder> findByOwnerAndParentAndDeletedTrue(Users owner, Folder parent);
    List<Folder> findByDeletedTrueAndDeletedAtBefore(
            LocalDateTime date
    );
}