package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileActivity;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface FileActivityRepository extends JpaRepository<FileActivity, Long> {

    @Query("""
        SELECT COUNT(a)
        FROM FileActivity a
        WHERE a.user = :user
        AND a.action = :action
    """)
    Long countByAction(@Param("user") Users user,
                       @Param("action") String action);

    @Query("""
        SELECT COALESCE(SUM(a.size), 0)
        FROM FileActivity a
        WHERE a.user = :user
        AND a.action = :action
    """)
    Long totalBytes(@Param("user") Users user,
                    @Param("action") String action);

    List<FileActivity> findTop4ByUserAndActionOrderByCreatedAtDesc(
            Users user,
            String action
    );

    @Query("""
        SELECT COUNT(a)
        FROM FileActivity a
        WHERE a.user = :user
        AND a.action = :action
        AND a.createdAt >= :start
    """)
    Long todayCount(@Param("user") Users user,
                    @Param("action") String action,
                    @Param("start") LocalDateTime start);

    @Modifying
    void deleteByFile_Id(Long fileId);

    Page<FileActivity> findByUserAndActionOrderByCreatedAtDesc(
            Users user,
            String action,
            Pageable pageable
    );

    Page<FileActivity> findByUserAndActionAndFile_DeletedFalseOrderByCreatedAtDesc(
            Users user,
            String action,
            Pageable pageable
    );

    List<FileActivity> findTop4ByUserAndActionAndFile_DeletedFalseOrderByCreatedAtDesc(
            Users user,
            String action
    );

}