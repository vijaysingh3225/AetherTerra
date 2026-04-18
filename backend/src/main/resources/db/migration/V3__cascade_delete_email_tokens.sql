ALTER TABLE email_verification_tokens
    DROP CONSTRAINT email_verification_tokens_user_id_fkey;

ALTER TABLE email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
