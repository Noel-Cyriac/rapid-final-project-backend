package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.FileViewResponse;
import com.nc.FinalProject.entity.StreamToken;
import com.nc.FinalProject.repository.StreamTokenRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;

@Service
public class FileStreamingService {

    private final StreamTokenRepository streamTokenRepository;

    public FileStreamingService(StreamTokenRepository streamTokenRepository) {
        this.streamTokenRepository = streamTokenRepository;
    }

    public ResponseEntity<Resource> streamByToken(
            String streamToken,
            HttpServletRequest request
    ) throws IOException {

        StreamToken token = streamTokenRepository.findByToken(streamToken)
                .orElseThrow(() -> new RuntimeException("Invalid stream token"));

        if (token.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Stream expired");
        }

        FileViewResponse file = new FileViewResponse(
                token.getSharedFile().getFile().getFilePath(),
                token.getSharedFile().getFile().getFileType()
        );

        return streamFile(file, request);
    }


    public ResponseEntity<Resource> streamFile(
            FileViewResponse file,
            HttpServletRequest request
    ) throws IOException {

        Path path = Paths.get(file.path());
        long fileSize = Files.size(path);

        String contentType = file.type();
        String rangeHeader = request.getHeader("Range");

        RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r");

        // =========================
        // FULL FILE (NO RANGE)
        // =========================
        if (rangeHeader == null) {

            InputStream inputStream = new BufferedInputStream(new FileInputStream(path.toFile()));

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(new InputStreamResource(inputStream));
        }

        // =========================
        // RANGE REQUEST (STREAMING)
        // =========================
        String[] ranges = rangeHeader.replace("bytes=", "").split("-");
        long start = Long.parseLong(ranges[0]);
        long end = (ranges.length > 1 && !ranges[1].isEmpty())
                ? Long.parseLong(ranges[1])
                : fileSize - 1;

        if (end >= fileSize) {
            end = fileSize - 1;
        }

        long contentLength = end - start + 1;

        randomAccessFile.seek(start);

        InputStream inputStream = new BufferedInputStream(new InputStream() {

            private long remaining = contentLength;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) return -1;
                remaining--;
                return randomAccessFile.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) return -1;

                int toRead = (int) Math.min(len, remaining);
                int read = randomAccessFile.read(b, off, toRead);

                if (read > 0) {
                    remaining -= read;
                }

                return read;
            }

            @Override
            public void close() throws IOException {
                randomAccessFile.close();
            }
        });

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(inputStream));
    }
}