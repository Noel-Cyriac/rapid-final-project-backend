package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.response.ProfileResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    @Value("${file.storage.location}")
    private String uploadDir;

    public ProfileResponse updateProfile(
            Users user,
            String firstName,
            String lastName,
            String dob,
            MultipartFile profilePic
    ) {

        try {

            if (firstName != null && !firstName.isBlank()) {
                user.setFirstName(firstName);
            }

            if (lastName != null && !lastName.isBlank()) {
                user.setLastName(lastName);
            }

            if (dob != null && !dob.isBlank()) {
                user.setDob(LocalDate.parse(dob));
            }

            if (profilePic != null && !profilePic.isEmpty()) {

                Path profileDir =
                        Paths.get(uploadDir, "profile");

                Files.createDirectories(profileDir);

                String fileName =
                        System.currentTimeMillis()
                                + "_"
                                + profilePic.getOriginalFilename();

                Path path =
                        profileDir.resolve(fileName);

                profilePic.transferTo(path);

                user.setProfilePicture(fileName);
            }

            Users saved =
                    userRepository.save(user);

            return map(saved);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public ProfileResponse getProfile(
            Users user
    ) {
        return map(user);
    }

    public ResponseEntity<Resource> getProfilePicture(
            Users user
    ) {

        try {

            if (user.getProfilePicture() == null ||
                    user.getProfilePicture().isBlank()) {

                return ResponseEntity.notFound().build();
            }

            Path path =
                    Paths.get(
                            user.getProfilePicture()
                    );

            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource =
                    new InputStreamResource(
                            Files.newInputStream(path)
                    );

            String contentType =
                    Files.probeContentType(path);

            if (contentType == null) {
                contentType =
                        "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(
                            MediaType.parseMediaType(
                                    contentType
                            )
                    )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" +
                                    path.getFileName() +
                                    "\""
                    )
                    .body(resource);

        } catch (Exception e) {
            throw new RuntimeException(
                    e.getMessage()
            );
        }
    }
    private ProfileResponse map(
            Users user
    ) {

        return new ProfileResponse(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getDob(),
                user.getProfilePicture() != null
                        ? "/api/profile/picture"
                        : null
        );
    }
}