package com.marketai.dashboard.service;

import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Centralised email service.
 *
 * Supported email types:
 *  1. Welcome email (on registration)
 *  2. Price anomaly alert
 *  3. AI signal alert (BUY / SELL)
 *  4. Account disabled notification
 *
 * All methods are @Async to avoid blocking the request thread.
 * Configure SMTP in application.yml → spring.mail.*
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.application.name:Market Dashboard}")
    private String appName;

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── 1. Welcome email ──────────────────────────────────────────────────────

    @Async("taskExecutor")
    public void sendWelcomeEmail(User user) {
        if (!isConfigured()) return;
        try {
            String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                  <div style="background:#0f172a;padding:24px;border-radius:8px 8px 0 0">
                    <h1 style="color:#38bdf8;margin:0">📈 Market Dashboard</h1>
                  </div>
                  <div style="padding:24px;border:1px solid #e2e8f0;border-radius:0 0 8px 8px">
                    <h2>Welcome, %s! 🎉</h2>
                    <p>Your account has been created successfully.</p>
                    <ul>
                      <li>Track real-time crypto &amp; stock prices</li>
                      <li>Add symbols to your watchlist</li>
                      <li>Get AI-powered BUY/SELL signals</li>
                      <li>Receive price anomaly alerts</li>
                    </ul>
                    <a href="http://localhost:5173/dashboard"
                       style="display:inline-block;background:#38bdf8;color:#fff;
                              padding:12px 24px;border-radius:6px;
                              text-decoration:none;font-weight:bold">
                      Open Dashboard →
                    </a>
                    <p style="color:#64748b;font-size:12px;margin-top:24px">
                      Default admin: admin@marketdashboard.com / Admin@1234
                    </p>
                  </div>
                </div>
                """.formatted(user.getUsername());

            sendHtmlEmail(user.getEmail(),
                    "Welcome to " + appName + "! 🚀", html);

        } catch (Exception e) {
            log.error("❌ Welcome email failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ── 2. Price anomaly alert ────────────────────────────────────────────────

    @Async("taskExecutor")
    public void sendAnomalyAlert(String toEmail, AlertNotification alert) {
        if (!isConfigured()) return;
        try {
            String direction  = alert.getPriceChange() > 0 ? "📈 UP" : "📉 DOWN";
            String color      = alert.getPriceChange() > 0 ? "#22c55e" : "#ef4444";
            String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                  <div style="background:#0f172a;padding:16px;border-radius:8px 8px 0 0">
                    <h2 style="color:#f59e0b;margin:0">⚠️ Market Alert</h2>
                  </div>
                  <div style="padding:24px;border:1px solid #e2e8f0;border-radius:0 0 8px 8px">
                    <h3 style="color:#1e293b">%s — %s</h3>
                    <table style="width:100%%;border-collapse:collapse">
                      <tr>
                        <td style="padding:8px;background:#f8fafc;font-weight:bold">Symbol</td>
                        <td style="padding:8px">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;background:#f8fafc;font-weight:bold">Price</td>
                        <td style="padding:8px">$%.2f</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;background:#f8fafc;font-weight:bold">Change</td>
                        <td style="padding:8px;color:%s;font-weight:bold">%+.2f%%</td>
                      </tr>
                    </table>
                    <a href="http://localhost:5173/dashboard"
                       style="display:inline-block;margin-top:16px;background:#0f172a;
                              color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none">
                      View Dashboard →
                    </a>
                  </div>
                </div>
                """.formatted(
                    alert.getSymbol(), direction,
                    alert.getSymbol(),
                    alert.getPrice(),
                    color, alert.getPriceChange());

            sendHtmlEmail(toEmail,
                    "⚠️ " + alert.getSymbol() + " Price Alert — " + direction,
                    html);

        } catch (Exception e) {
            log.error("❌ Anomaly email failed for {}: {}", toEmail, e.getMessage());
        }
    }

    // ── 3. AI signal alert ────────────────────────────────────────────────────

    @Async("taskExecutor")
    public void sendSignalAlert(String toEmail, String symbol,
                                String signal, String summary) {
        if (!isConfigured()) return;
        try {
            String emoji = switch (signal) {
                case "BUY"  -> "🟢";
                case "SELL" -> "🔴";
                default     -> "🟡";
            };
            String color = switch (signal) {
                case "BUY"  -> "#22c55e";
                case "SELL" -> "#ef4444";
                default     -> "#f59e0b";
            };

            String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                  <div style="background:#0f172a;padding:16px;border-radius:8px 8px 0 0">
                    <h2 style="color:#a78bfa;margin:0">🤖 AI Signal Alert</h2>
                  </div>
                  <div style="padding:24px;border:1px solid #e2e8f0;border-radius:0 0 8px 8px">
                    <h2>%s <span style="color:%s">%s</span> — %s</h2>
                    <p style="color:#475569">%s</p>
                    <a href="http://localhost:5173/dashboard"
                       style="display:inline-block;margin-top:16px;background:#7c3aed;
                              color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none">
                      View AI Analysis →
                    </a>
                  </div>
                </div>
                """.formatted(emoji, color, signal, symbol, summary);

            sendHtmlEmail(toEmail,
                    emoji + " " + signal + " Signal — " + symbol, html);

        } catch (Exception e) {
            log.error("❌ Signal email failed for {}: {}", toEmail, e.getMessage());
        }
    }

    // ── 4. Account disabled notification ─────────────────────────────────────

    @Async("taskExecutor")
    public void sendAccountDisabledEmail(User user) {
        if (!isConfigured()) return;
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(user.getEmail());
            mail.setSubject("Your " + appName + " account has been suspended");
            mail.setText("""
                Hi %s,
                
                Your account has been suspended by an administrator.
                Please contact support if you believe this is an error.
                
                — %s Team
                """.formatted(user.getUsername(), appName));
            mailSender.send(mail);
        } catch (Exception e) {
            log.error("❌ Account disabled email failed for {}: {}",
                    user.getEmail(), e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String html)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);  // true = HTML
        mailSender.send(message);
        log.info("📧 Email sent to {} — {}", to, subject);
    }

    private boolean isConfigured() {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.debug("⚠️ Email not configured (MAIL_USER not set) — skipping");
            return false;
        }
        return true;
    }
}