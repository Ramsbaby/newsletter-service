package app.ramsbaby.newsletter.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 메시지 큐잉 및 발송 서비스
 * 
 * 캠페인이 생성되면 활성 구독자에게 메시지를 큐잉하고,
 * 스케줄러가 주기적으로 큐에서 메시지를 꺼내 발송합니다.
 */
@Service
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;

    public MessageService(JdbcTemplate jdbcTemplate, JavaMailSender mailSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
    }

    /**
     * 캠페인에 대한 메시지 큐잉
     * 
     * 활성 구독자(status='active')에게 메시지를 생성합니다.
     * 
     * @param campaignId 캠페인 ID
     * @return 생성된 메시지 개수
     */
    public int queueMessagesForCampaign(long campaignId) {
        // 활성 구독자 ID 조회
        List<Long> activeSubscriberIds = jdbcTemplate.queryForList(
            "SELECT id FROM newsletter_subscribers WHERE status = 'active'",
            Long.class
        );

        if (activeSubscriberIds.isEmpty()) {
            log.warn("No active subscribers found for campaign ID={}", campaignId);
            return 0;
        }

        // 각 구독자에게 메시지 생성 (중복 방지)
        int count = 0;
        for (Long subscriberId : activeSubscriberIds) {
            try {
                // PostgreSQL: ON CONFLICT DO NOTHING (중복 메시지 방지)
                jdbcTemplate.update(
                    "INSERT INTO messages(campaign_id, subscriber_id, status) VALUES(?, ?, 'queued') " +
                    "ON CONFLICT (campaign_id, subscriber_id) DO NOTHING",
                    campaignId, subscriberId
                );
                count++;
            } catch (Exception e) {
                log.error("Failed to queue message for subscriber ID={}: {}", subscriberId, e.getMessage());
            }
        }

        log.info("Queued {} messages for campaign ID={}", count, campaignId);
        return count;
    }

    /**
     * 큐에서 메시지를 꺼내 발송
     * 
     * 'queued' 상태인 메시지를 일괄 처리합니다.
     * 
     * @param batchSize 한 번에 처리할 메시지 개수
     * @return 발송 성공한 메시지 개수
     */
    public int sendQueuedMessages(int batchSize) {
        // 큐에서 메시지 조회
        List<QueuedMessage> messages = jdbcTemplate.query(
            "SELECT m.id, m.campaign_id, m.subscriber_id, c.subject, c.html, s.email " +
            "FROM messages m " +
            "JOIN campaigns c ON m.campaign_id = c.id " +
            "JOIN newsletter_subscribers s ON m.subscriber_id = s.id " +
            "WHERE m.status = 'queued' " +
            "LIMIT ?",
            (rs, rowNum) -> new QueuedMessage(
                rs.getLong("id"),
                rs.getLong("campaign_id"),
                rs.getLong("subscriber_id"),
                rs.getString("email"),
                rs.getString("subject"),
                rs.getString("html")
            ),
            batchSize
        );

        if (messages.isEmpty()) {
            log.debug("No queued messages to send");
            return 0;
        }

        log.info("Processing {} queued messages...", messages.size());

        int successCount = 0;
        for (QueuedMessage msg : messages) {
            boolean success = sendMessage(msg);
            if (success) {
                markAsSent(msg.id());
                successCount++;
            } else {
                markAsFailed(msg.id(), "Failed to send email");
            }
        }

        log.info("Sent {}/{} messages", successCount, messages.size());
        return successCount;
    }

    /**
     * 단일 메시지 발송
     */
    private boolean sendMessage(QueuedMessage msg) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(msg.email());
            mail.setSubject(msg.subject());
            mail.setText(stripHtml(msg.html())); // TODO: HTML 메일로 개선
            
            mailSender.send(mail);
            log.info("Sent message ID={} to {}", msg.id(), msg.email());
            return true;
        } catch (Exception e) {
            log.error("Failed to send message ID={} to {}: {}", msg.id(), msg.email(), e.getMessage());
            return false;
        }
    }

    /**
     * 메시지 상태를 'sent'로 업데이트
     */
    private void markAsSent(long messageId) {
        jdbcTemplate.update(
            "UPDATE messages SET status = 'sent', sent_at = ? WHERE id = ?",
            Timestamp.from(Instant.now()),
            messageId
        );
    }

    /**
     * 메시지 상태를 'failed'로 업데이트
     */
    private void markAsFailed(long messageId, String error) {
        jdbcTemplate.update(
            "UPDATE messages SET status = 'failed', error = ? WHERE id = ?",
            error,
            messageId
        );
    }

    /**
     * HTML 태그 제거 (간단한 구현)
     * TODO: HTML 이메일 지원으로 개선
     */
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * 큐에 있는 메시지 DTO
     */
    private record QueuedMessage(
        long id,
        long campaignId,
        long subscriberId,
        String email,
        String subject,
        String html
    ) {}
}

