package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.Notification;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserOrderByCreatedAtDesc(
            Users user,
            Pageable pageable
    );

    long countByUserAndReadStatusFalse(Users user);

    void deleteByUser(Users user);
}