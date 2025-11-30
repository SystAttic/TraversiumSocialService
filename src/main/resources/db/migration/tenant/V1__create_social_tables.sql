-- Create comments table
CREATE TABLE comments
(
    comment_id BIGSERIAL PRIMARY KEY,
    content    TEXT                                   NOT NULL,
    user_id    BIGINT                                 NOT NULL,
    media_id   BIGINT                                 NOT NULL,
    parent_id  BIGINT,
    created_at TIMESTAMP WITH TIME ZONE               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_comment FOREIGN KEY (parent_id) REFERENCES comments (comment_id) ON DELETE CASCADE
);

-- Create indexes for comments
CREATE INDEX idx_comments_user_id ON comments (user_id);
CREATE INDEX idx_comments_media_id ON comments (media_id);
CREATE INDEX idx_comments_parent_id ON comments (parent_id);
CREATE INDEX idx_comments_media_parent_null ON comments (media_id, parent_id) WHERE parent_id IS NULL;

-- Create likes table
CREATE TABLE likes
(
    like_id   BIGSERIAL PRIMARY KEY,
    user_id   BIGINT                                 NOT NULL,
    media_id  BIGINT                                 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_media_like UNIQUE (user_id, media_id)
);

-- Create indexes for likes
CREATE INDEX idx_likes_user_id ON likes (user_id);
CREATE INDEX idx_likes_media_id ON likes (media_id);

