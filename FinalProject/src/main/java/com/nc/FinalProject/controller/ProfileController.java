package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.response.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;

    private Users user(Authentication auth) {
        return userRepository
                .findByEmail(auth.getName())
                .orElseThrow();
    }

    @PutMapping("/update")
    public ResponseEntity<SuccessResponse> updateProfile(
            Authentication auth,

            @RequestParam(required = false)
            String firstName,

            @RequestParam(required = false)
            String lastName,

            @RequestParam(required = false)
            String dob,

            @RequestParam(required = false)
            MultipartFile profilePic
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Profile updated successfully",
                        profileService.updateProfile(
                                user(auth),
                                firstName,
                                lastName,
                                dob,
                                profilePic
                        )
                )
        );
    }

    @GetMapping
    public ResponseEntity<SuccessResponse> getProfile(
            Authentication auth
    ) {

        return ResponseEntity.ok(
                new SuccessResponse(
                        "Profile fetched successfully",
                        profileService.getProfile(
                                user(auth)
                        )
                )
        );
    }

    @GetMapping("/picture")
    public ResponseEntity<Resource> profilePicture(
            Authentication auth
    ) {

        return profileService.getProfilePicture(
                user(auth)
        );
    }
}