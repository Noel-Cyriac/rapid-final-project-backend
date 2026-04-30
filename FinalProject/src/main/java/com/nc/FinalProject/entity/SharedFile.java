package com.nc.FinalProject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareLink;

    @ManyToOne
    private FileEntity file;

    @ManyToOne
    private Users owner;

    private String recipientEmail;

    private LocalDateTime expireAt;

    private Integer maxUses;

    private Integer usedCount;

    private Integer openCount;

    private Boolean active;

    private Boolean canDownload;

    private Boolean canView;

    private String password;

    private LocalDateTime createdAt;

    private LocalDateTime lastOpenedAt;

    private Integer accessed;

    private String message;
}