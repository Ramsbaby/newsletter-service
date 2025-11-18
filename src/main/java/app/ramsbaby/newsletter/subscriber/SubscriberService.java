package app.ramsbaby.newsletter.subscriber;

import app.ramsbaby.newsletter.mail.MailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class SubscriberService {
    private final JdbcTemplate jdbcTemplate;
    private final MailService mailService;

    public SubscriberService(JdbcTemplate jdbcTemplate, MailService mailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailService = mailService;
    }

    public void subscribe(String email) {
        // PostgreSQL: INSERT ... ON CONFLICT ... DO NOTHING
        jdbcTemplate.update("INSERT INTO newsletter_subscribers(email,status) VALUES(?, 'pending') ON CONFLICT (email) DO NOTHING", email);
        mailService.sendConfirm(email);
    }

    public void confirm(String token) {
        String email = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        jdbcTemplate.update("UPDATE newsletter_subscribers SET status='active', confirmed_at=CURRENT_TIMESTAMP WHERE email=?", email);
    }

    public void unsubscribe(String token) {
        String email = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        jdbcTemplate.update("UPDATE newsletter_subscribers SET status='unsubscribed', unsubscribed_at=CURRENT_TIMESTAMP WHERE email=?", email);
        mailService.sendUnsubscribeNotice(email);
    }

    public List<SubscriberDto> listAll() {
        return jdbcTemplate.query(
                "SELECT id, email, status, created_at, confirmed_at, unsubscribed_at FROM newsletter_subscribers ORDER BY id DESC",
                (rs, rowNum) -> new SubscriberDto(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("status"),
                        rs.getString("created_at"),
                        rs.getString("confirmed_at"),
                        rs.getString("unsubscribed_at")
                )
        );
    }

    /**
     * 구독자 완전 삭제 (Admin 전용)
     * 
     * CASCADE 설정으로 관련 messages도 자동 삭제됨
     * 
     * @param id 구독자 ID
     * @return 삭제된 행 수 (1이면 성공, 0이면 없음)
     */
    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM newsletter_subscribers WHERE id = ?", id);
    }

    /**
     * 이메일로 구독자 완전 삭제 (Admin 전용)
     * 
     * @param email 구독자 이메일
     * @return 삭제된 행 수 (1이면 성공, 0이면 없음)
     */
    public int deleteByEmail(String email) {
        return jdbcTemplate.update("DELETE FROM newsletter_subscribers WHERE email = ?", email);
    }
}
