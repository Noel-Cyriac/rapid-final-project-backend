package com.nc.FinalProject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendShareEmail(
            String to,
            String link,
            String customMessage
    ) {
        System.out.println("EMAIL BODY MESSAGE = " + customMessage);

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(to);

        message.setSubject("File Shared With You");

        String body =
                "A file has been shared with you.\n\n";

        if (customMessage != null &&
                !customMessage.isBlank()) {

            body += "Message from sender:\n"
                    + customMessage + "\n\n";
        }

        body +=
                "Open Link:\n" + link +
                        "\n\nThis link may expire or have limited access.";

        message.setText(body);

        mailSender.send(message);
    }

    public void sendResetPasswordEmail(String to, String resetLink) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject("Reset Your Password");

        String body =
                "We received a request to reset your password.\n\n" +
                        "Click the link below to reset it:\n\n" +
                        resetLink + "\n\n" +
                        "If you did not request this, you can safely ignore this email.\n\n" +
                        "This link may expire soon for security reasons.";

        message.setText(body);

        mailSender.send(message);
    }
}