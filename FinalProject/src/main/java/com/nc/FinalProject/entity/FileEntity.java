package com.nc.FinalProject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;     // user visible

    private String storedName;   // actual disk name

    private String fileType;

    private Long size;

    private String filePath;

    private Integer downloadCount = 0;

    private Boolean deleted = false;

    private LocalDateTime uploadedAt;

    private LocalDateTime lastDownloadedAt;

    private LocalDateTime lastOpenedAt;

    @ManyToOne
    private Users owner;

    @ManyToOne
    private Folder folder;
}