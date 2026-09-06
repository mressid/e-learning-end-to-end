-- Authentication tokens: refreshing a session, verifying an address, resetting
-- a password.
--
-- Every one of these stores a SHA-256 of the token, never the token itself. A
-- leaked dump of this table must not hand out live sessions or let someone take
-- over an account mid-reset. SHA-256 rather than bcrypt is deliberate and is the
-- opposite of the choice made for `users.password_hash`: these are 256-bit
-- values the server generated, so there is no dictionary to run against them and
-- nothing for a slow KDF to buy -- while a per-request bcrypt on the refresh
-- path would be a real cost on every token rotation.
--
-- The hash is UNIQUE so a token is looked up by its hash directly. There is no
-- index on user_id alone for lookup; that is for revoking a user's sessions.

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    -- Rotation chains tokens together. Every refresh revokes the presented
    -- token and issues its successor into the same family, so a family is one
    -- login session. Presenting an already-revoked token means either a replay
    -- or a stolen token being used alongside the real one -- and the family is
    -- the unit that has to be killed, because the thief and the victim both
    -- hold descendants of it.
    family_id  uuid        NOT NULL,
    issued_at  timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
-- Supports the expiry sweep without scanning live sessions.
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;

CREATE TABLE email_verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    -- Single use. Kept rather than deleted so a second click on the same link
    -- can be answered precisely instead of looking like an invalid token.
    consumed_at timestamptz,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
