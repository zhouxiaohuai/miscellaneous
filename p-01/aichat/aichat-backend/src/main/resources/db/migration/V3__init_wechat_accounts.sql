-- Flyway migration: wechat account binding

CREATE TABLE IF NOT EXISTS wechat_accounts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  open_id VARCHAR(64) NOT NULL,
  union_id VARCHAR(64) NULL,
  nickname VARCHAR(255) NULL,
  avatar VARCHAR(1024) NULL,
  scope VARCHAR(64) NULL,
  raw_profile TEXT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wechat_openid (open_id),
  KEY idx_wechat_user (user_id),
  KEY idx_wechat_unionid (union_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

