package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.StreamToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StreamTokenRepository extends JpaRepository<StreamToken, Long> {
    Optional<StreamToken> findByToken(String token);

    void deleteByRecipient_Id(Long recipientId);

    void deleteByRecipient_Share_Id(Long shareId);

    @Transactional
    @Modifying
    @Query("DELETE FROM StreamToken s WHERE s.file.id = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);
}