-- Platform administration.
--
-- V1 defines no admin concept: "staff" everywhere in the code means *course*
-- staff, derived from ownership or co-instructorship. That is the right shape
-- for authority over one course (§11), but it leaves nobody able to moderate a
-- review or revoke a certificate on a course they do not own.
--
-- The division of labour: relationships decide authority over a specific
-- resource, permissions decide authority over the platform. A permission is a
-- fallback that ADDS to a relationship check, never one that replaces it.
--
-- Administrators live in their own table rather than in `users`. Administering
-- the platform and learning on it are different jobs, and separating them means
-- a self-registered learner has no path to a role at all -- a structural
-- guarantee rather than a rule somebody has to remember.

CREATE TABLE admin_users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(320) NOT NULL,
    username      varchar(64)  NOT NULL,
    password_hash varchar(255) NOT NULL,
    -- No PENDING: admins do not self-register, so there is no address to prove
    -- and no consent to establish -- a super admin created the account.
    status        varchar(32)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    CONSTRAINT uq_admin_users_email    UNIQUE (email),
    CONSTRAINT uq_admin_users_username UNIQUE (username)
);

-- Mirrors refresh_tokens, including family_id rotation and reuse detection.
-- A separate table rather than a polymorphic subject column on the original,
-- because a polymorphic reference cannot carry a foreign key.
CREATE TABLE admin_refresh_tokens (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id uuid        NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    token_hash    varchar(64) NOT NULL,
    family_id     uuid        NOT NULL,
    issued_at     timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    revoked_at    timestamptz,
    CONSTRAINT uq_admin_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_admin_refresh_tokens_user   ON admin_refresh_tokens (admin_user_id);
CREATE INDEX idx_admin_refresh_tokens_family ON admin_refresh_tokens (family_id);

CREATE TABLE permissions (
    code        varchar(64) PRIMARY KEY,
    description varchar(255) NOT NULL
);

CREATE TABLE roles (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(80) NOT NULL,
    slug        varchar(80) NOT NULL,
    description varchar(255),
    -- All permissions, including ones added by later migrations. Enumerating
    -- them into role_permissions instead would mean a permission added next
    -- year is silently not granted, and super admins quietly lose a capability.
    is_super    boolean     NOT NULL DEFAULT false,
    -- Seeded roles the platform depends on; these cannot be deleted.
    is_system   boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_slug UNIQUE (slug)
);

CREATE TABLE role_permissions (
    role_id         uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_code varchar(64) NOT NULL REFERENCES permissions (code) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);

CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_code);

CREATE TABLE admin_user_roles (
    admin_user_id uuid NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    role_id       uuid NOT NULL REFERENCES roles (id)       ON DELETE CASCADE,
    -- Who handed this out. An audit trail for privilege is worth the column,
    -- and NULL marks the seeded grant that had no granter.
    granted_by    uuid REFERENCES admin_users (id) ON DELETE SET NULL,
    granted_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (admin_user_id, role_id)
);

CREATE INDEX idx_admin_user_roles_role ON admin_user_roles (role_id);

-- --- the permission catalog -------------------------------------------------
-- Derived from the admin dashboard's page map, not invented: each code below
-- corresponds to a screen or an action that dashboard offers.
--
-- No `submission.grade`: assignment_submissions.graded_by references `users`,
-- so an admin cannot be recorded as a grader. Marking stays with the course's
-- instructors, and admins get read access to the queue instead.

INSERT INTO permissions (code, description) VALUES
    ('course.read',         'View any course, including drafts'),
    ('course.write',        'Edit any course and its structure'),
    ('course.publish',      'Publish, unpublish or archive any course'),
    ('course.delete',       'Delete sections and items of any course'),
    ('category.manage',     'Manage the category and tag taxonomy'),
    ('user.read',           'View the student and instructor directories'),
    ('user.suspend',        'Suspend or reinstate a learner account'),
    ('certificate.read',    'View any issued certificate'),
    ('certificate.revoke',  'Revoke any certificate'),
    ('submission.read',     'View any assignment submission or quiz attempt'),
    ('review.moderate',     'Moderate reviews on any course'),
    ('discussion.moderate', 'Moderate discussion threads on any course'),
    ('media.read',          'Browse the central media library'),
    ('media.delete',        'Delete media objects'),
    ('settings.manage',     'Change platform settings'),
    ('audit.read',          'Read the audit trail')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (name, slug, description, is_super, is_system)
VALUES ('Super Admin', 'super-admin', 'Every permission, including ones added later', true, true)
ON CONFLICT (slug) DO NOTHING;

-- --- the bootstrap account --------------------------------------------------
-- Chicken and egg: the endpoint that grants roles requires a super admin, so
-- the first one cannot come through the API. Values are Flyway placeholders, so
-- development works untouched while production supplies its own by environment
-- rather than by editing a migration.

INSERT INTO admin_users (email, username, password_hash, status)
VALUES ('${superAdminEmail}', '${superAdminUsername}', '${superAdminPasswordHash}', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO admin_user_roles (admin_user_id, role_id, granted_by)
SELECT a.id, r.id, NULL
FROM admin_users a, roles r
WHERE a.email = '${superAdminEmail}' AND r.slug = 'super-admin'
ON CONFLICT (admin_user_id, role_id) DO NOTHING;
