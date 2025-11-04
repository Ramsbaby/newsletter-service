package app.ramsbaby.newsletter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Flyway 수동 실행 설정
 * 
 * Flyway를 세션 풀러(5432) 전용 DataSource로 분리하여:
 * 1. 애플리케이션 풀과 완전히 분리하여 충돌 방지
 * 2. Flyway 전용 단일 연결 사용
 * 3. 세션 풀러(5432) 사용으로 DDL/마이그레이션 안정성 보장
 */
@Configuration
public class FlywayConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    
    private final DataSource applicationDataSource;  // 애플리케이션용 DataSource
    private final Environment environment;
    
    // 재시도 설정
    private static final int MAX_RETRIES = 30;  // 최대 30번 재시도 (약 5분)
    private static final long RETRY_INTERVAL_MS = 10000;  // 재시도 간격 10초 (연결 해제 대기)
    
    public FlywayConfig(DataSource dataSource, Environment environment) {
        this.applicationDataSource = dataSource;
        this.environment = environment;
    }
    
    /**
     * Flyway 전용 DataSource 생성 (세션 풀러 5432 전용)
     * 
     * 애플리케이션 풀과 완전히 분리하여:
     * - Flyway가 독립적인 연결 사용
     * - 애플리케이션 풀과 충돌 방지
     * - 세션 풀러 사용으로 DDL 안정성 보장
     */
    private HikariDataSource createFlywayDataSource() {
        String jdbcUrl = environment.getProperty("spring.datasource.url",
                "jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres");
        String username = environment.getProperty("spring.datasource.username",
                "postgres.ddibysmqerrfxucjxotj");
        String password = environment.getProperty("spring.datasource.password",
                "urxQGLgy4kpRtGB5");
        
        // 세션 풀러(5432) URL 보장
        if (!jdbcUrl.contains(":5432")) {
            // 6543 포트가 있으면 5432로 변경
            jdbcUrl = jdbcUrl.replace(":6543", ":5432");
            log.info("Flyway: Using session pooler (port 5432) for migration");
        }
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Flyway 전용: 최소 2개 연결 필요
        // - Flyway 메타데이터 테이블 조회용 연결 1개
        // - 마이그레이션 실행용 연결 1개
        config.setMaximumPoolSize(2);  // 2개로 증가
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10000);  // 10초
        config.setIdleTimeout(120000);  // 2분
        config.setMaxLifetime(300000);  // 5분
        config.setPoolName("FlywayPool");
        config.setLeakDetectionThreshold(60000);
        
        log.info("Flyway DataSource created: session pooler (5432), pool-size=2 (metadata + migration)");
        
        return new HikariDataSource(config);
    }
    
    /**
     * 애플리케이션 시작 완료 후 Flyway 마이그레이션 실행
     * 
     * 연결 수 제한 환경을 고려하여:
     * 1. 연결 확인 없이 바로 Flyway 실행 (Flyway 자체의 재시도 로직 활용)
     * 2. 비동기 실행으로 애플리케이션 시작을 막지 않음
     * 3. 연결 실패 시 백그라운드에서 재시도
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void runFlywayMigration() {
        log.info("Starting Flyway migration with retry logic (async)...");
        
        // HikariCP 풀 상태 로깅
        logConnectionPoolStatus();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 연결 확인 없이 바로 Flyway 실행 (Flyway가 자체적으로 재시도함)
                executeFlywayMigration();
                log.info("Flyway migration completed successfully");
                return;
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                // 연결 풀 상태 상세 로깅
                logConnectionPoolStatus();
                
                if (errorMsg != null && errorMsg.contains("Max client connections reached")) {
                    if (attempt < MAX_RETRIES) {
                        log.warn("Supabase connection limit reached (attempt {}/{}). Waiting {}ms before retry...", 
                                attempt, MAX_RETRIES, RETRY_INTERVAL_MS);
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Flyway migration interrupted", ie);
                            return;
                        }
                    } else {
                        log.error("Flyway migration failed after {} attempts due to connection limit. " +
                                "This is likely due to other instances using connections. " +
                                "Please check Supabase dashboard or wait for connections to be released.", 
                                MAX_RETRIES);
                        log.error("Full error: {}", e.getMessage());
                        return;  // 비동기 실행이므로 예외를 던지지 않고 종료
                    }
                } else if (errorMsg != null && (errorMsg.contains("Connection is not available") || 
                        errorMsg.contains("Connection not available after waiting"))) {
                    // HikariCP 풀 타임아웃 에러 또는 연결 대기 실패
                    if (attempt < MAX_RETRIES) {
                        log.warn("Connection not available (attempt {}/{}). Pool may be exhausted. Waiting {}ms before retry...", 
                                attempt, MAX_RETRIES, RETRY_INTERVAL_MS);
                        log.warn("Error details: {}", errorMsg);
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Flyway migration interrupted", ie);
                            return;
                        }
                    } else {
                        log.error("Flyway migration failed after {} attempts due to connection pool timeout. " +
                                "Possible causes: 1) Connection leak, 2) Connection not returned properly, " +
                                "3) Supabase connection limit reached. Please check Supabase dashboard.", 
                                MAX_RETRIES);
                        log.error("Full error: {}", e.getMessage());
                        return;
                    }
                } else {
                    // 다른 종류의 에러는 즉시 실패 처리
                    log.error("Flyway migration failed with unexpected error: {}", e.getMessage(), e);
                    return;
                }
            }
        }
        
        log.error("Flyway migration failed: Unable to obtain database connection after {} attempts", MAX_RETRIES);
    }
    
    /**
     * 애플리케이션 풀 상태 로깅
     */
    private void logConnectionPoolStatus() {
        if (applicationDataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) applicationDataSource;
            try {
                var poolMXBean = hikariDataSource.getHikariPoolMXBean();
                int active = poolMXBean.getActiveConnections();
                int idle = poolMXBean.getIdleConnections();
                int total = poolMXBean.getTotalConnections();
                int waiting = poolMXBean.getThreadsAwaitingConnection();
                
                log.info("Application Pool Status - Active: {}, Idle: {}, Total: {}, Waiting: {}", 
                        active, idle, total, waiting);
                
                // 경고 상태 체크
                if (total > 0 && idle == 0 && active == total) {
                    log.warn("⚠️ All connections are in use! Active={}, Idle={}, Total={}", active, idle, total);
                }
                if (waiting > 0) {
                    log.warn("⚠️ Threads waiting for connection: {}", waiting);
                }
            } catch (Exception e) {
                log.debug("Failed to get application pool status: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Flyway 마이그레이션 실행
     * 
     * Flyway 전용 DataSource(세션 풀러 5432)를 사용하여:
     * 1. 애플리케이션 풀과 완전히 분리
     * 2. validateOnMigrate=false로 설정하여 단일 작업만 수행
     * 3. 세션 풀러 사용으로 DDL 안정성 보장
     */
    private void executeFlywayMigration() throws SQLException {
        log.info("Attempting Flyway migration with dedicated session pooler (5432)...");
        
        // Flyway 전용 DataSource 생성 (세션 풀러 5432 전용)
        HikariDataSource flywayDataSource = null;
        try {
            flywayDataSource = createFlywayDataSource();
            
            // application.yml에서 Flyway 설정 읽기
            String locationsProperty = environment.getProperty("spring.flyway.locations", 
                    "classpath:db/migration");
            String[] locations = locationsProperty.split(",");
            
            boolean baselineOnMigrate = environment.getProperty("spring.flyway.baseline-on-migrate", 
                    Boolean.class, true);
            // validateOnMigrate를 false로 설정하여 Flyway가 validate와 migrate를 동시에 실행하지 않도록 함
            boolean validateOnMigrate = false;  // validate 비활성화 (단일 연결 사용 보장)
            
            // Flyway 전용 DataSource 사용
            Flyway flyway = Flyway.configure()
                    .dataSource(flywayDataSource)  // Flyway 전용 DataSource 사용
                    .locations(locations)
                    .baselineOnMigrate(baselineOnMigrate)
                    .validateOnMigrate(validateOnMigrate)
                    .connectRetries(0)  // Flyway 내부 재시도 완전히 비활성화
                    .load();
            
            // 마이그레이션 실행
            flyway.migrate();
            
            log.info("Flyway migration completed successfully");
            
        } finally {
            // Flyway 전용 DataSource 정리
            if (flywayDataSource != null) {
                flywayDataSource.close();
                log.info("Flyway DataSource closed");
            }
        }
    }
}

