ALTER TABLE award_policies
    ADD COLUMN category_ranking_basis VARCHAR(32);

UPDATE award_policies
SET category_ranking_basis = ranking_basis;

ALTER TABLE award_policies
    ALTER COLUMN category_ranking_basis SET NOT NULL,
    ADD CONSTRAINT ck_award_policies_category_ranking_basis
        CHECK (category_ranking_basis IN ('GUN_TIME', 'CHIP_TIME', 'NONE')),
    ADD CONSTRAINT ck_award_policies_none_category_basis
        CHECK (
            (ranking_basis = 'NONE' AND category_ranking_basis = 'NONE')
            OR (ranking_basis <> 'NONE' AND category_ranking_basis <> 'NONE')
        );

ALTER TABLE categories
    ADD COLUMN ranking_basis_override VARCHAR(32),
    ADD CONSTRAINT ck_categories_ranking_basis_override
        CHECK (ranking_basis_override IS NULL OR ranking_basis_override IN ('GUN_TIME', 'CHIP_TIME'));

ALTER TABLE event_series_start_award_policy_templates
    ADD COLUMN category_ranking_basis VARCHAR(32);

UPDATE event_series_start_award_policy_templates
SET category_ranking_basis = ranking_basis;

ALTER TABLE event_series_start_award_policy_templates
    ALTER COLUMN category_ranking_basis SET NOT NULL,
    ADD CONSTRAINT ck_template_award_policies_category_ranking_basis
        CHECK (category_ranking_basis IN ('GUN_TIME', 'CHIP_TIME', 'NONE')),
    ADD CONSTRAINT ck_template_award_policies_none_category_basis
        CHECK (
            (ranking_basis = 'NONE' AND category_ranking_basis = 'NONE')
            OR (ranking_basis <> 'NONE' AND category_ranking_basis <> 'NONE')
        );

ALTER TABLE event_series_start_category_templates
    ADD COLUMN ranking_basis_override VARCHAR(32),
    ADD CONSTRAINT ck_template_categories_ranking_basis_override
        CHECK (ranking_basis_override IS NULL OR ranking_basis_override IN ('GUN_TIME', 'CHIP_TIME'));
