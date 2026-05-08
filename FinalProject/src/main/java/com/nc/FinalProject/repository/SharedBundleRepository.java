package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.SharedBundle;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SharedBundleRepository
        extends JpaRepository<SharedBundle, Long> {

    Optional<SharedBundle> findByShareToken(String token);

    Page<SharedBundle> findByOwner(Users owner, Pageable pageable);

    List<SharedBundle> findAllByOwner(Users owner);
}