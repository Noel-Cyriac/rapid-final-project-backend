package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Share;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    Page<Share> findByOwner(Users owner, Pageable pageable);

    Optional<Share> findByShareToken(String token);

    boolean existsByFileAndActiveTrue(FileEntity file);

    boolean existsByFilesContainsAndActiveTrue(FileEntity file);

    List<Share> findAllByFileOrFilesContains(FileEntity file, FileEntity file2);

    List<Share> findAllByOwner(Users owner);

    @Query("""
    SELECT 
        FUNCTION('DATE', s.sharedAt),
        COUNT(s)
    FROM Share s
    WHERE s.owner = :user
    AND s.sharedAt >= :start
    GROUP BY FUNCTION('DATE', s.sharedAt)
    ORDER BY FUNCTION('DATE', s.sharedAt)
""")
    List<Object[]> sharesPerDay(
            @Param("user") Users user,
            @Param("start") LocalDateTime start
    );
}
