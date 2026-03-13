package com.marketai.dashboard.service;

import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.AlertRepository;
import com.marketai.dashboard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Value("${spring.mail.username:}")
    private String fromEmail;

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;

    public NotificationService(@Autowired(required = false) JavaMailSender mailSender,
                               UserRepository userRepository,
                               AlertRepository alertRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
    }

    @Async("taskExecutor")
    public void sendAnomalyEmailAsync(AlertNotification alert) {
        if (mailSender == null) {
            log.warn("⚠️ JavaMailSender not configured — skipping email for {}", alert.getSymbol());
            return;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("⚠️ Email not configured — skipping notification for {}", alert.getSymbol());
            return;
        }
        List<User> usersToNotify = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(User::isEmailNotifications)
                .filter(u -> u.getWatchlist().contains(alert.getSymbol()))
                .toList();
        if (usersToNotify.isEmpty()) {
            log.debug("📭 No users watching {} — skipping email", alert.getSymbol());
            return;
        }
        for (User user : usersToNotify) {
            try {
                sendEmail(user.getEmail(), alert);
                alert.setEmailSent(true);
                alertRepository.save(alert);
                log.info("📧 Alert email sent to {} for {}", user.getEmail(), alert.getSymbol());
            } catch (Exception e) {
                log.error("❌ Failed to send email to {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private void sendEmail(String toEmail, AlertNotification alert) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromEmail);
        mail.setTo(toEmail);
        mail.setSubject(String.format("⚠️ Market Alert — %s", alert.getSymbol()));
        mail.setText(String.format(
                """
                Market Dashboard Alert
                
                %s
                
                Symbol:  %s
                Price:   $%.2f
                Change:  %.2f%%
                Time:    %s
                
                — Market Dashboard Team
                """,
                alert.getMessage(),
                alert.getSymbol(),
                alert.getPrice(),
                alert.getPriceChange(),
                alert.getTimestamp()
        ));
        mailSender.send(mail);
    }
}
