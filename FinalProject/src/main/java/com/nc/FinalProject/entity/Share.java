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

    @Enumerated(EnumType.STRING)
    private ShareType type;

    public enum ShareType {
        FILE,
        BUNDLE
    }

    @ManyToOne
    private FileEntity file;

    @ManyToMany
    @JoinTable(
            name = "share_bundle_files",
            joinColumns = @JoinColumn(name = "share_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id")
    )
    private List<FileEntity> files;

    @ManyToOne
    private Users owner;

    private LocalDateTime expireAt;

    // global config
    private Integer maxUses;

    private String password;

    private String message;

    private Boolean active;

    private LocalDateTime sharedAt;

    @OneToMany(
            mappedBy = "share",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ShareRecipient> recipients;
}