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
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareToken;

    // =========================
    // TYPE OF SHARE
    // =========================
    @Enumerated(EnumType.STRING)
    private ShareType type;

    public enum ShareType {
        FILE,
        BUNDLE
    }

    // =========================
    // FILE SHARE (single file)
    // =========================
    @ManyToOne
    private FileEntity file;

    // =========================
    // BUNDLE SHARE (multiple files)
    // =========================
    @ManyToMany
    @JoinTable(
            name = "share_bundle_files",
            joinColumns = @JoinColumn(name = "share_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id")
    )
    private List<FileEntity> files;

    // =========================
    // COMMON FIELDS
    // =========================
    @ManyToOne
    private Users owner;

    @ElementCollection
    @CollectionTable(
            name = "share_emails",
            joinColumns = @JoinColumn(name = "share_id")
    )
    @Column(name = "email")
    private List<String> recipientEmails;

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
