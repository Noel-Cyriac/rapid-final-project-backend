package com.nc.FinalProject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareLink;           // unique shareable token
    private String recipientEmail;
    private String message;

    private Instant expireAt;           // UTC timestamp
    private Instant shareDate;          // UTC timestamp
    private boolean accessed;

    @ManyToOne
    private FileEntity file;
}