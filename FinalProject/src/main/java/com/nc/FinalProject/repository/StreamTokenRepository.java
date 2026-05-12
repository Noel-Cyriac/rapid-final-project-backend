package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.Share;
import com.nc.FinalProject.entity.StreamToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreamTokenRepository extends JpaRepository<StreamToken, Long> {
    Optional<StreamToken> findByToken(String token);
    void deleteByShare(Share share);
}