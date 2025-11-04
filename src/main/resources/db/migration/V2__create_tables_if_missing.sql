-- ========================================
-- V2: 테이블이 없는 경우 강제 생성
-- (V1이 실행되었지만 테이블이 없는 경우 대비)
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

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_subscribers_email ON subscribers(email);
CREATE INDEX IF NOT EXISTS idx_subscribers_status ON subscribers(status);

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

-- 인덱스
CREATE UNIQUE INDEX IF NOT EXISTS idx_campaigns_source ON campaigns(source);
CREATE INDEX IF NOT EXISTS idx_campaigns_status ON campaigns(status);

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

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_messages_campaign_id ON messages(campaign_id);
CREATE INDEX IF NOT EXISTS idx_messages_subscriber_id ON messages(subscriber_id);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_campaign_subscriber ON messages(campaign_id, subscriber_id);

