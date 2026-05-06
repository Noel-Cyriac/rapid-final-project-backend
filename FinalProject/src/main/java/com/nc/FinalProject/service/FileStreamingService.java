package com.nc.FinalProject.service;
import com.nc.FinalProject.dto.FileViewResponse;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.*;

@Service
public class FileStreamingService {

    public ResponseEntity<Resource> streamFile(FileViewResponse file, HttpServletRequest request) throws IOException {

        Path path = Paths.get(file.path());
        long fileSize = Files.size(path);

        String contentType = file.type();
        String rangeHeader = request.getHeader("Range");

        // ✅ No Range header → full file
        if (rangeHeader == null) {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(resource);
        }

        // ✅ Parse Range
        String[] ranges = rangeHeader.replace("bytes=", "").split("-");
        long start = Long.parseLong(ranges[0]);
        long end = (ranges.length > 1 && !ranges[1].isEmpty())
                ? Long.parseLong(ranges[1])
                : fileSize - 1;

        // ✅ Safety check
        if (end >= fileSize) {
            end = fileSize - 1;
        }

        long chunkSize = end - start + 1;

        // ✅ Use RandomAccessFile (CORRECT way)
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        raf.seek(start);

        InputStream inputStream = new InputStream() {
            private long bytesRemaining = chunkSize;

            @Override
            public int read() throws IOException {
                if (bytesRemaining <= 0) return -1;
                bytesRemaining--;
                return raf.read();
            }

            @Override
            public void close() throws IOException {
                raf.close();
            }
        };

        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .contentLength(chunkSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}