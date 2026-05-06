package com.nc.FinalProject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendShareEmail(String to, String link) {

        System.out.println("========== SHARE LINK ==========");
        System.out.println(link);
        System.out.println("================================");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("File Shared With You");
        message.setText(
                "A file has been shared with you.\n\n" +
                        "Open Link:\n" + link + "\n\n" +
                        "This link may expire or have limited access."
        );

        mailSender.send(message);
    }
}