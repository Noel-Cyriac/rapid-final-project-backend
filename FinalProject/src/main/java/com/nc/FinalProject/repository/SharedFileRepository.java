package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.SharedFile;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {

    Optional<SharedFile> findByShareLink(String shareLink);

    Page<SharedFile> findByOwner(Users owner, Pageable pageable);

    Page<SharedFile> findByFileOwnerEmail(
            String email,
            Pageable pageable
    );
}