package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.response.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private Users user(Authentication auth) {
        return userRepository
                .findByEmail(auth.getName())
                .orElseThrow();
    }

    @GetMapping
    public ResponseEntity<SuccessResponse> list(
            Authentication auth,
            Pageable pageable
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Notifications fetched",
                        notificationService.list(
                                user(auth),
                                pageable
                        )
                )
        );
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<SuccessResponse> read(
            @PathVariable Long id,
            Authentication auth
    ) {

        notificationService.markRead(
                id,
                user(auth)
        );

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Notification marked read",
                        null
                )
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<SuccessResponse> readAll(
            Authentication auth
    ) {

        notificationService.markAllRead(
                user(auth)
        );

        return ResponseEntity.ok(
                new SuccessResponse(
                        "All notifications marked read",
                        null
                )
        );
    }

    @DeleteMapping("/clear")
    public ResponseEntity<SuccessResponse> clear(
            Authentication auth
    ) {

        notificationService.clearAll(
                user(auth)
        );

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Notifications cleared",
                        null
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse> clearOne(
            @PathVariable Long id,
            Authentication auth
    ) {

        notificationService.clearOne(
                id,
                user(auth)
        );

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Notification removed",
                        null
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<SuccessResponse> unread(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Unread count fetched",
                        notificationService.unreadCount(
                                user(auth)
                        )
                )
        );
    }
}
