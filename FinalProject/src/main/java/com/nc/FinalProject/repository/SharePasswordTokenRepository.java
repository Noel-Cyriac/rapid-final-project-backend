package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.SharePasswordToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SharePasswordTokenRepository
        extends JpaRepository<SharePasswordToken, Long> {

    Optional<SharePasswordToken> findByToken(String token);
}