ALTER TABLE ai_assistance_run
    ADD COLUMN chat_model VARCHAR(120) NULL,
    ADD COLUMN embedding_model VARCHAR(120) NULL,
    ADD COLUMN prompt_version VARCHAR(40) NULL;
