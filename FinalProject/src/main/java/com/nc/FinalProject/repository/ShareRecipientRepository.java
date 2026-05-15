package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.ShareRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShareRecipientRepository
        extends JpaRepository<ShareRecipient, Long> {

    Optional<ShareRecipient>
    findByAccessToken(String accessToken);
}