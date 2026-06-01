package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Folder;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    Page<FileEntity> findByOwnerAndDeletedFalse(
            Users user,
            Pageable pageable
    );

    Page<FileEntity> findByOwnerAndDeletedTrue(
            Users user,
            Pageable pageable
    );

    Optional<FileEntity> findByIdAndOwner(
            Long id,
            Users user
    );

    boolean existsByOwnerAndFolderAndFileNameAndDeletedFalse(
            Users owner,
            Folder folder,
            String fileName
    );

    @Query("""
    SELECT COALESCE(SUM(f.size), 0)
    FROM FileEntity f
    WHERE f.owner = :user
""")
    Long totalUsed(@Param("user") Users user);

    List<FileEntity> findTop4ByOwnerAndDeletedFalseOrderByUploadedAtDesc(
            Users user
    );

    List<FileEntity> findTop4ByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(
            Users user
    );

    Page<FileEntity> findByOwnerAndDeletedFalseOrderByUploadedAtDesc(
            Users user,
            Pageable pageable
    );

    Page<FileEntity>
    findByOwnerAndDeletedFalseAndLastOpenedAtNotNullOrderByLastOpenedAtDesc(
            Users user,
            Pageable pageable
    );

    Page<FileEntity> findByOwnerAndStarredTrueAndDeletedFalse(
            Users user,
            Pageable pageable
    );

    @Query("""
        SELECT 
            COALESCE(f.fileType, 'UNKNOWN'),
            COALESCE(SUM(f.size), 0)
        FROM FileEntity f
        WHERE f.owner = :user
        GROUP BY f.fileType
    """)
    List<Object[]> storageBreakdown(@Param("user") Users user);

    List<FileEntity> findByDeletedTrueAndDeletedAtBefore(
            LocalDateTime date
    );

    List<FileEntity>
    findTop4ByOwnerAndDeletedFalseAndLastDownloadedAtNotNullOrderByLastDownloadedAtDesc(Users user);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileEntity f WHERE f.owner = :user AND f.deleted = false")
    Long getUsedStorage(@Param("user") Users user);

    Page<FileEntity>
    findByOwnerAndFolderAndDeletedFalse(
            Users owner,
            Folder folder,
            Pageable pageable
    );

    List<FileEntity>
    findByOwnerAndFolderAndDeletedFalse(
            Users owner,
            Folder folder
    );

    List<FileEntity>
    findByOwnerAndFolder(
            Users owner,
            Folder folder
    );
}