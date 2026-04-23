package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Page<FileEntity> findByOwner(Users owner, Pageable pageable);
}