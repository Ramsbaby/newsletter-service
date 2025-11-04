package app.ramsbaby.newsletter.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * 캠페인 관리 서비스
 * 
 * 새로운 포스트가 발행되면 이메일 캠페인을 생성하고 관리합니다.
 */
@Service
public class CampaignService {
    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    private final JdbcTemplate jdbcTemplate;

    public CampaignService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 새 캠페인 생성
     * 
     * PostgreSQL: ON CONFLICT를 사용하여 중복 방지 및 ID 반환
     * 
     * @param source RSS entry link 또는 포스트 URL
     * @param subject 이메일 제목
     * @param htmlBody 이메일 HTML 본문
     * @return 생성된 또는 기존 캠페인 ID
     */
    public long createCampaign(String source, String subject, String htmlBody) {
        try {
            // PostgreSQL: INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING id
            // 중복 시에도 항상 ID를 반환 (새 캠페인 or 기존 캠페인)
            Long campaignId = jdbcTemplate.queryForObject(
                "INSERT INTO campaigns(source, subject, html, status, scheduled_at) " +
                "VALUES(?, ?, ?, 'scheduled', ?) " +
                "ON CONFLICT (source) DO UPDATE SET source = EXCLUDED.source " +
                "RETURNING id",
                Long.class,
                source, subject, htmlBody, Timestamp.from(Instant.now())
            );
            
            if (campaignId == null) {
                throw new RuntimeException("Failed to retrieve campaign ID");
            }
            
            log.info("Campaign ID={} for source={}", campaignId, source);
            return campaignId;
        } catch (Exception e) {
            log.error("Failed to create campaign for source={}: {}", source, e.getMessage());
            // Fallback: 기존 캠페인 조회
            Optional<Long> existing = findCampaignIdBySource(source);
            if (existing.isPresent()) {
                log.info("Using existing campaign ID={}", existing.get());
                return existing.get();
            }
            throw new RuntimeException("Failed to create or retrieve campaign", e);
        }
    }

    /**
     * Source로 캠페인 ID 조회
     */
    private Optional<Long> findCampaignIdBySource(String source) {
        try {
            Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM campaigns WHERE source = ? LIMIT 1",
                Long.class,
                source
            );
            return Optional.ofNullable(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 캠페인 상태 업데이트
     * 
     * @param campaignId 캠페인 ID
     * @param status 새 상태 (scheduled, sent, failed)
     */
    public void updateStatus(long campaignId, String status) {
        jdbcTemplate.update(
            "UPDATE campaigns SET status = ? WHERE id = ?",
            status, campaignId
        );
        log.info("Updated campaign ID={} status={}", campaignId, status);
    }

    /**
     * 예약된 캠페인 목록 조회
     * 
     * @return 'scheduled' 상태인 캠페인 ID 리스트
     */
    public java.util.List<Long> getScheduledCampaignIds() {
        return jdbcTemplate.queryForList(
            "SELECT id FROM campaigns WHERE status = 'scheduled' ORDER BY created_at ASC",
            Long.class
        );
    }
}

