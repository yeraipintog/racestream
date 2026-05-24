CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'USER') NOT NULL,
    provider ENUM('LOCAL') NOT NULL,
    policies_accepted BOOLEAN NOT NULL,
    cookie_consent BOOLEAN NOT NULL,
    cookie_consent_status ENUM('ACCEPTED', 'REJECTED', 'UNDECIDED') NULL DEFAULT 'UNDECIDED',
    notifications_enabled BOOLEAN NOT NULL,
    email_notifications_enabled BOOLEAN NOT NULL,
    favorite_digest_enabled BOOLEAN NOT NULL,
    favorite_digest_email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    private_profile BOOLEAN NOT NULL,
    password_reset_token VARCHAR(96) NULL,
    password_reset_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_name UNIQUE (name),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blocked_emails (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(160) NOT NULL,
    reason VARCHAR(240) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_blocked_emails_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS contact_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic VARCHAR(40) NULL DEFAULT 'Otro',
    subject VARCHAR(120) NOT NULL,
    message VARCHAR(1400) NOT NULL,
    client_request_id VARCHAR(80) NULL,
    completed BOOLEAN NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_contact_request UNIQUE (user_id, client_request_id),
    CONSTRAINT fk_contact_messages_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS forum_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    parent_post_id BIGINT NULL,
    category VARCHAR(60) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    client_request_id VARCHAR(80) NULL,
    likes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_forum_request UNIQUE (author_id, client_request_id),
    CONSTRAINT fk_forum_posts_author
        FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_forum_posts_parent
        FOREIGN KEY (parent_post_id) REFERENCES forum_posts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS forum_post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_forum_post_likes_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_forum_post_likes_post
        FOREIGN KEY (post_id) REFERENCES forum_posts (id),
    CONSTRAINT fk_forum_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    season_year INT NULL,
    title VARCHAR(180) NOT NULL,
    url VARCHAR(360) NULL,
    description VARCHAR(360) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_favorite_season UNIQUE (user_id, type, external_id, season_year),
    CONSTRAINT fk_user_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_app_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS session_notification_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    mail_sent BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_session_notification_user_event UNIQUE (user_id, event_key),
    CONSTRAINT fk_session_notification_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
