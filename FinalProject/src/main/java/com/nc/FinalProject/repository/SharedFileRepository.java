package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {

    Optional<SharedFile> findByShareLink(String shareLink);

    @Query("SELECT s FROM SharedFile s WHERE s.file.owner.email = :email")
    List<SharedFile> findByFileOwnerEmail(@Param("email") String email);
}