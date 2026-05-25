package com.nc.FinalProject.dto.response;

import com.nc.FinalProject.entity.Notification;
import com.nc.FinalProject.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(
            Notification notification
    ) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(notification.isReadStatus())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}