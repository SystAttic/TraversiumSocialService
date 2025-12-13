-- Add firebase_id column to comments table
ALTER TABLE comments
    ADD COLUMN firebase_id VARCHAR(255) UNIQUE;
