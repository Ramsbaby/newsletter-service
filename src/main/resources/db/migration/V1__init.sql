-- ========================================
-- Newsletter Service Database Schema
-- PostgreSQL (Supabase 호환)
-- ========================================

-- 구독자 테이블
CREATE TABLE IF NOT EXISTS subscribers (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  status VARCHAR(50) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  confirmed_at TIMESTAMP NULL,
  unsubscribed_at TIMESTAMP NULL
);

-- 인덱스: 이메일 검색 최적화
CREATE INDEX IF NOT EXISTS idx_subscribers_email ON subscribers(email);

-- 인덱스: 상태별 조회 최적화
CREATE INDEX IF NOT EXISTS idx_subscribers_status ON subscribers(status);

-- 코멘트
COMMENT ON TABLE subscribers IS '뉴스레터 구독자 목록';
COMMENT ON COLUMN subscribers.email IS '구독자 이메일 주소';
COMMENT ON COLUMN subscribers.status IS '구독 상태: pending(대기), active(활성), unsubscribed(해지)';

-- ========================================

-- 캠페인 테이블
CREATE TABLE IF NOT EXISTS campaigns (
  id BIGSERIAL PRIMARY KEY,
  source TEXT NOT NULL,
  subject TEXT NOT NULL,
  html TEXT NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'scheduled',
  scheduled_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스: source로 중복 체크
CREATE UNIQUE INDEX IF NOT EXISTS idx_campaigns_source ON campaigns(source);

-- 인덱스: 상태별 조회 최적화
CREATE INDEX IF NOT EXISTS idx_campaigns_status ON campaigns(status);

-- 코멘트
COMMENT ON TABLE campaigns IS '이메일 캠페인 (포스트별 발송 관리)';
COMMENT ON COLUMN campaigns.source IS '포스트 URL (중복 방지용)';

-- ========================================

-- 메시지 테이블
CREATE TABLE IF NOT EXISTS messages (
  id BIGSERIAL PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  subscriber_id BIGINT NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'queued',
  provider_msg_id TEXT NULL,
  error TEXT NULL,
  sent_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_messages_campaign FOREIGN KEY(campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_subscriber FOREIGN KEY(subscriber_id) REFERENCES subscribers(id) ON DELETE CASCADE
);

-- 인덱스: 캠페인별 메시지 조회
CREATE INDEX IF NOT EXISTS idx_messages_campaign_id ON messages(campaign_id);

-- 인덱스: 구독자별 메시지 조회
CREATE INDEX IF NOT EXISTS idx_messages_subscriber_id ON messages(subscriber_id);

-- 인덱스: 상태별 메시지 조회 (발송 대기열)
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);

-- 복합 인덱스: 중복 발송 방지
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_campaign_subscriber ON messages(campaign_id, subscriber_id);

-- 코멘트
COMMENT ON TABLE messages IS '발송 메시지 큐 (구독자별 발송 관리)';
COMMENT ON COLUMN messages.status IS '메시지 상태: queued(대기), sent(발송완료), failed(실패)';

