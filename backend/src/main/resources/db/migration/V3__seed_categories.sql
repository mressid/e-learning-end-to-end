-- Categories are curated reference data, not user content: the browse tree only
-- means something if one authority decides what is in it, and V1 has no admin
-- role to grant that. Seeding them here is what makes the read-only category
-- API usable at all -- without this the table is empty and nothing can be
-- categorised.
--
-- Slugs are the stable identifier (uq_categories_slug), so ON CONFLICT DO
-- NOTHING makes this migration safe to land on a database where an operator has
-- already inserted some of these by hand.

INSERT INTO categories (id, parent_id, name, slug) VALUES
    (gen_random_uuid(), NULL, 'Development',        'development'),
    (gen_random_uuid(), NULL, 'Business',           'business'),
    (gen_random_uuid(), NULL, 'Design',             'design'),
    (gen_random_uuid(), NULL, 'Data Science',       'data-science'),
    (gen_random_uuid(), NULL, 'IT & Software',      'it-software'),
    (gen_random_uuid(), NULL, 'Marketing',          'marketing'),
    (gen_random_uuid(), NULL, 'Personal Development', 'personal-development'),
    (gen_random_uuid(), NULL, 'Languages',          'languages')
ON CONFLICT (slug) DO NOTHING;

-- Second level. The parent is looked up by slug rather than hardcoded, because
-- the ids above are generated and cannot be referenced literally.
INSERT INTO categories (id, parent_id, name, slug)
SELECT gen_random_uuid(), p.id, child.name, child.slug
FROM (VALUES
    ('development',          'Web Development',        'web-development'),
    ('development',          'Mobile Development',     'mobile-development'),
    ('development',          'Programming Languages',  'programming-languages'),
    ('development',          'Game Development',       'game-development'),
    ('business',             'Entrepreneurship',       'entrepreneurship'),
    ('business',             'Management',             'management'),
    ('business',             'Finance & Accounting',   'finance-accounting'),
    ('design',              'Graphic Design',          'graphic-design'),
    ('design',              'UX & UI Design',          'ux-ui-design'),
    ('data-science',        'Machine Learning',        'machine-learning'),
    ('data-science',        'Data Analysis',           'data-analysis'),
    ('it-software',         'Cloud Computing',         'cloud-computing'),
    ('it-software',         'Cyber Security',          'cyber-security'),
    ('it-software',         'Networking',              'networking'),
    ('marketing',           'Digital Marketing',       'digital-marketing'),
    ('marketing',           'Content Marketing',       'content-marketing'),
    ('personal-development', 'Productivity',           'productivity'),
    ('personal-development', 'Leadership',             'leadership')
) AS child(parent_slug, name, slug)
JOIN categories p ON p.slug = child.parent_slug
ON CONFLICT (slug) DO NOTHING;
