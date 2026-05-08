package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.FileViewResponse;
import com.nc.FinalProject.entity.FileEntity;
import com.nc.FinalProject.entity.SharedBundle;
import com.nc.FinalProject.entity.StreamToken;
import com.nc.FinalProject.repository.StreamTokenRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileStreamingService {

    private final StreamTokenRepository streamTokenRepository;

    public FileStreamingService(
            StreamTokenRepository streamTokenRepository
    ) {
        this.streamTokenRepository = streamTokenRepository;
    }

    // =====================================================
    // STREAM / VIEW
    // =====================================================
    public ResponseEntity<Resource> streamByToken(
            String streamToken,
            HttpServletRequest request
    ) throws IOException {

        StreamToken token = validateToken(streamToken);

        FileViewResponse file = new FileViewResponse(
                token.getSharedFile().getFile().getFilePath(),
                token.getSharedFile().getFile().getFileType()
        );

        return streamFile(file, request);
    }

    // =====================================================
    // DOWNLOAD
    // =====================================================
    public ResponseEntity<Resource> downloadByToken(
            String streamToken
    ) throws IOException {

        StreamToken token = validateToken(streamToken);

        Path path = Paths.get(
                token.getSharedFile().getFile().getFilePath()
        );

        String contentType =
                token.getSharedFile().getFile().getFileType();

        InputStream inputStream =
                new BufferedInputStream(
                        new FileInputStream(path.toFile())
                );

        Resource resource =
                new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(contentType)
                )
                .contentLength(Files.size(path))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                path.getFileName().toString() +
                                "\""
                )
                .body(resource);
    }

    // =====================================================
    // INTERNAL STREAMING LOGIC
    // =====================================================
    public ResponseEntity<Resource> streamFile(
            FileViewResponse file,
            HttpServletRequest request
    ) throws IOException {

        Path path = Paths.get(file.path());

        long fileSize = Files.size(path);

        String contentType = file.type();

        String rangeHeader = request.getHeader("Range");

        // ============================================
        // NO RANGE
        // ============================================
        if (rangeHeader == null) {

            InputStream inputStream =
                    new BufferedInputStream(
                            new FileInputStream(path.toFile())
                    );

            return ResponseEntity.ok()
                    .contentType(
                            MediaType.parseMediaType(contentType)
                    )
                    .contentLength(fileSize)
                    .header(
                            HttpHeaders.ACCEPT_RANGES,
                            "bytes"
                    )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline"
                    )
                    .body(new InputStreamResource(inputStream));
        }

        // ============================================
        // RANGE STREAMING
        // ============================================
        String[] ranges =
                rangeHeader.replace("bytes=", "").split("-");

        long start = Long.parseLong(ranges[0]);

        long end =
                (ranges.length > 1 && !ranges[1].isEmpty())
                        ? Long.parseLong(ranges[1])
                        : fileSize - 1;

        if (end >= fileSize) {
            end = fileSize - 1;
        }

        long contentLength = end - start + 1;

        RandomAccessFile randomAccessFile =
                new RandomAccessFile(path.toFile(), "r");

        randomAccessFile.seek(start);

        InputStream inputStream = new BufferedInputStream(
                new InputStream() {

                    private long remaining = contentLength;

                    @Override
                    public int read() throws IOException {

                        if (remaining <= 0) {
                            return -1;
                        }

                        remaining--;

                        return randomAccessFile.read();
                    }

                    @Override
                    public int read(
                            byte[] b,
                            int off,
                            int len
                    ) throws IOException {

                        if (remaining <= 0) {
                            return -1;
                        }

                        int toRead =
                                (int) Math.min(len, remaining);

                        int read =
                                randomAccessFile.read(
                                        b,
                                        off,
                                        toRead
                                );

                        if (read > 0) {
                            remaining -= read;
                        }

                        return read;
                    }

                    @Override
                    public void close() throws IOException {
                        randomAccessFile.close();
                    }
                }
        );

        return ResponseEntity.status(
                        HttpStatus.PARTIAL_CONTENT
                )
                .contentType(
                        MediaType.parseMediaType(contentType)
                )
                .header(
                        HttpHeaders.ACCEPT_RANGES,
                        "bytes"
                )
                .header(
                        HttpHeaders.CONTENT_RANGE,
                        "bytes " +
                                start +
                                "-" +
                                end +
                                "/" +
                                fileSize
                )
                .contentLength(contentLength)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline"
                )
                .body(new InputStreamResource(inputStream));
    }

    // =====================================================
    // TOKEN VALIDATION
    // =====================================================
    private StreamToken validateToken(String streamToken) {

        StreamToken token =
                streamTokenRepository.findByToken(streamToken)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Invalid stream token"
                                )
                        );

        if (token.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new RuntimeException("Stream expired");
        }

        return token;
    }

    public void downloadBundle(
            String streamToken,
            OutputStream outputStream
    ) throws IOException {

        StreamToken token =
                streamTokenRepository.findByToken(streamToken)
                        .orElseThrow(() -> new RuntimeException("Invalid stream token"));

        SharedBundle bundle = token.getSharedBundle();

        ZipOutputStream zos = new ZipOutputStream(outputStream);

        byte[] buffer = new byte[8192];

        for (FileEntity file : bundle.getFiles()) {

            Path path = Paths.get(file.getFilePath());

            zos.putNextEntry(new ZipEntry(file.getFileName()));

            try (InputStream fis = Files.newInputStream(path)) {

                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }

            zos.closeEntry();
        }

        zos.finish();
        zos.close();
    }
}