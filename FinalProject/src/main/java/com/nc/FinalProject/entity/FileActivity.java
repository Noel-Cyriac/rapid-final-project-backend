package com.nc.FinalProject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action; // UPLOAD DOWNLOAD DELETE RESTORE

    private Long size;

    private LocalDateTime createdAt;

    @ManyToOne
    private Users user;

    @ManyToOne
    private FileEntity file;
}