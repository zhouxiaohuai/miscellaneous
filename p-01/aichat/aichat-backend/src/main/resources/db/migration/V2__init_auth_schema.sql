-- Flyway migration: initialize auth schema (minimal)
-- Stage-A: only users table; WeChat binding table will be added in later iterations.

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

