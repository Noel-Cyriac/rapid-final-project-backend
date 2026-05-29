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
public class ShareRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(unique = true, nullable = false)
    private String accessToken;

    private Integer openCount;
    private Integer usedCount;

    private LocalDateTime lastOpenedAt;

    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id")
    private Share share;

    @OneToMany(
            mappedBy = "recipient",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SharePasswordToken> passwordTokens;
}