-- remove unique constraint from firebase_id
ALTER TABLE comments
    DROP CONSTRAINT comments_firebase_id_key;