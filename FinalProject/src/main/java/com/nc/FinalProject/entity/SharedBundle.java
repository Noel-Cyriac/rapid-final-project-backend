package com.nc.FinalProject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareToken;

    @ManyToMany
    private List<FileEntity> files;

    @ManyToOne
    private Users owner;

    private String recipientEmail;

    private LocalDateTime expireAt;

    private Integer maxUses;

    private Integer usedCount;

    private Integer openCount;

    private Boolean active;

    private String password;

    private String message;

    private LocalDateTime sharedAt;

    private LocalDateTime lastOpenedAt;
}