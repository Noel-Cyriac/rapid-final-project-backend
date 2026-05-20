package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.PagedResponse;
import com.nc.FinalProject.dto.response.NotificationResponse;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void create(
            Users user,
            String title,
            String message,
            NotificationType type
    ) {

        Notification notification =
                Notification.builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .readStatus(false)
                        .createdAt(LocalDateTime.now())
                        .build();

        notificationRepository.save(notification);
    }

    public PagedResponse<NotificationResponse> list(
            Users user,
            Pageable pageable
    ) {

        Page<Notification> page =
                notificationRepository
                        .findByUserOrderByCreatedAtDesc(
                                user,
                                pageable
                        );

        return PagedResponse.from(
                page.map(NotificationResponse::from)
        );
    }

    public void markRead(Long id, Users user) {

        Notification notification =
                notificationRepository.findById(id)
                        .orElseThrow();

        if (!notification.getUser().getId()
                .equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        notification.setReadStatus(true);

        notificationRepository.save(notification);
    }

    public void clearAll(Users user) {
        notificationRepository.deleteByUser(user);
    }

    public long unreadCount(Users user) {
        return notificationRepository
                .countByUserAndReadStatusFalse(user);
    }
}