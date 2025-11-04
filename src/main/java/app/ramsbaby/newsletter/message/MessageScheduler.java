package app.ramsbaby.newsletter.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 메시지 발송 스케줄러
 * 
 * 주기적으로 메시지 큐를 확인하고 발송합니다.
 */
@Component
public class MessageScheduler {
    private static final Logger log = LoggerFactory.getLogger(MessageScheduler.class);
    
    private final MessageService messageService;
    
    // 한 번에 처리할 메시지 개수
    private static final int BATCH_SIZE = 50;

    public MessageScheduler(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 메시지 발송 (5분마다)
     * 
     * - 초기 지연: 2분
     * - 반복 간격: 5분 (300,000ms)
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void sendMessages() {
        try {
            log.debug("Checking message queue...");
            int sentCount = messageService.sendQueuedMessages(BATCH_SIZE);
            
            if (sentCount > 0) {
                log.info("Message batch sent: {}/{} success", sentCount, BATCH_SIZE);
            }
        } catch (Exception e) {
            log.error("Message sending failed: {}", e.getMessage(), e);
        }
    }
}

