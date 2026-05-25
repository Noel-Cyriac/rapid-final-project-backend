package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.ShareRecipient;
import com.nc.FinalProject.entity.StreamToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreamTokenRepository extends JpaRepository<StreamToken, Long> {
    Optional<StreamToken> findByToken(String token);
    void deleteByRecipient(ShareRecipient recipient);
    void deleteByRecipient_Id(Long recipientId);
    // If you need to delete all tokens for a whole Share (all its recipients):
    void deleteByRecipient_Share_Id(Long shareId);
}