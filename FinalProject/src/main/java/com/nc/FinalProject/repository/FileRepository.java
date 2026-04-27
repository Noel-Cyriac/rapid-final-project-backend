package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    Page<FileEntity> findByOwnerAndDeletedFalse(Users user, Pageable pageable);

    Page<FileEntity> findByOwnerAndDeletedTrue(Users user, Pageable pageable);

    Optional<FileEntity> findByIdAndOwner(Long id, Users user);

    boolean existsByOwnerAndFolderAndFileNameAndDeletedFalse(
            Users owner,
            com.nc.FinalProject.entity.Folder folder,
            String fileName
    );

    @Query("SELECT COALESCE(SUM(f.size),0) FROM FileEntity f WHERE f.owner=:user AND f.deleted=false")
    Long totalUsed(Users user);

    List<FileEntity> findTop4ByOwnerAndDeletedFalseOrderByUploadedAtDesc(Users user);
}