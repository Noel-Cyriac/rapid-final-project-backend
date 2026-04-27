package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileActivity;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDateTime;
import java.util.List;

public interface FileActivityRepository extends JpaRepository<FileActivity, Long> {

    @Query("""
    SELECT COUNT(a) FROM FileActivity a
    WHERE a.user=:user AND a.action=:action
    """)
    Long countByAction(Users user, String action);

    @Query("""
    SELECT COALESCE(SUM(a.size),0) FROM FileActivity a
    WHERE a.user=:user AND a.action=:action
    """)
    Long totalBytes(Users user, String action);

    List<FileActivity> findTop4ByUserAndActionOrderByCreatedAtDesc(
            Users user,
            String action
    );

    @Query("""
    SELECT COUNT(a) FROM FileActivity a
    WHERE a.user=:user
    AND a.action=:action
    AND a.createdAt >= :start
    """)
    Long todayCount(Users user, String action, LocalDateTime start);
}