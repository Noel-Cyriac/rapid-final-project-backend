package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.RefreshToken;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUser(Users user);
}