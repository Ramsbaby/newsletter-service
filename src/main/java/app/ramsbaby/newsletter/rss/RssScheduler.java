package app.ramsbaby.newsletter.rss;

import app.ramsbaby.newsletter.campaign.CampaignService;
import app.ramsbaby.newsletter.config.AppProps;
import app.ramsbaby.newsletter.message.MessageService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * RSS 피드 스케줄러
 * 
 * 주기적으로 RSS 피드를 폴링하여 신규 포스트를 감지하고,
 * 새 포스트가 발견되면 캠페인을 생성하고 메시지를 큐잉합니다.
 */
@Component
public class RssScheduler {
    private static final Logger log = LoggerFactory.getLogger(RssScheduler.class);
    
    private final AppProps props;
    private final CampaignService campaignService;
    private final MessageService messageService;
    
    // 마지막 폴링 시간 (서버 재시작 시 최근 24시간 포스트 감지)
    private Instant lastPolled = Instant.now().minus(24, ChronoUnit.HOURS);

    public RssScheduler(AppProps props, CampaignService campaignService, MessageService messageService) {
        this.props = props;
        this.campaignService = campaignService;
        this.messageService = messageService;
    }

    /**
     * RSS 피드 폴링 (15분마다)
     * 
     * - 초기 지연: 1분
     * - 반복 간격: 15분 (900,000ms)
     */
    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void poll() {
        if (props.getRssUrl() == null || props.getRssUrl().isEmpty()) {
            log.debug("RSS URL not configured, skipping poll");
            return;
        }

        try {
            log.info("Polling RSS feed: {}", props.getRssUrl());
            URL url = new URL(props.getRssUrl());
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(url));
            
            int newPostCount = 0;
            for (SyndEntry entry : feed.getEntries()) {
                boolean isNew = processEntry(entry);
                if (isNew) {
                    newPostCount++;
                }
            }
            
            // 마지막 폴링 시간 업데이트
            lastPolled = Instant.now();
            
            if (newPostCount > 0) {
                log.info("Detected {} new posts", newPostCount);
            } else {
                log.debug("No new posts found");
            }
        } catch (Exception e) {
            log.error("RSS polling failed: {}", e.getMessage(), e);
        }
    }

    /**
     * RSS 엔트리 처리
     * 
     * @param entry RSS 엔트리
     * @return 신규 포스트 여부
     */
    private boolean processEntry(SyndEntry entry) {
        try {
            String link = entry.getLink();
            String title = entry.getTitle();
            Date publishedDate = entry.getPublishedDate();
            
            // 발행일이 없으면 스킵
            if (publishedDate == null) {
                log.debug("Entry '{}' has no published date, skipping", title);
                return false;
            }
            
            Instant published = publishedDate.toInstant();
            
            // 마지막 폴링 이후에 발행된 포스트만 처리
            if (published.isBefore(lastPolled)) {
                log.debug("Entry '{}' is old ({}), skipping", title, published);
                return false;
            }
            
            log.info("New post detected: {}", title);
            
            // 캠페인 생성
            String subject = "새 포스트: " + title;
            String htmlBody = buildEmailBody(entry);
            long campaignId = campaignService.createCampaign(link, subject, htmlBody);
            
            // 메시지 큐잉
            int messageCount = messageService.queueMessagesForCampaign(campaignId);
            log.info("Campaign created (ID={}) with {} messages", campaignId, messageCount);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to process entry '{}': {}", entry.getTitle(), e.getMessage());
            return false;
        }
    }

    /**
     * 이메일 본문 생성
     * 
     * TODO: Thymeleaf 템플릿으로 개선
     */
    private String buildEmailBody(SyndEntry entry) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: sans-serif;'>");
        html.append("<h2>").append(entry.getTitle()).append("</h2>");
        
        if (entry.getDescription() != null) {
            html.append("<p>").append(entry.getDescription().getValue()).append("</p>");
        }
        
        html.append("<p><a href='").append(entry.getLink()).append("' ");
        html.append("style='background:#2563eb;color:white;padding:12px 24px;text-decoration:none;border-radius:6px;display:inline-block;'>");
        html.append("포스트 읽기</a></p>");
        
        html.append("<hr style='margin-top:32px;border:none;border-top:1px solid #e5e7eb;'>");
        html.append("<p style='color:#6b7280;font-size:12px;'>");
        html.append("이 이메일은 Ramsbaby 블로그 뉴스레터 구독자에게 발송되었습니다.<br>");
        html.append("더 이상 받고 싶지 않으시면 <a href='{{unsubscribe_link}}'>구독 해제</a>를 클릭하세요.");
        html.append("</p>");
        html.append("</body></html>");
        
        return html.toString();
    }
}


