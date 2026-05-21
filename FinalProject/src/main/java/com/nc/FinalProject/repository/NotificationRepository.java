package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.Notification;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserOrderByCreatedAtDesc(
            Users user,
            Pageable pageable
    );

    long countByUserAndReadStatusFalse(Users user);

    @Modifying
    void deleteByUser(Users user);

    void deleteByIdAndUser(
            Long id,
            Users user
    );

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readStatus = true
            WHERE n.user = :user
            AND n.readStatus = false
            """)
    int markAllAsRead(Users user);
}