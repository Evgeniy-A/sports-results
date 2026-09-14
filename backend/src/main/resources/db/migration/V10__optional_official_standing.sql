ALTER TABLE award_policies
    DROP CONSTRAINT ck_award_policies_ranking_basis,
    ADD CONSTRAINT ck_award_policies_ranking_basis
        CHECK (ranking_basis IN ('GUN_TIME', 'CHIP_TIME', 'NONE')),
    ADD CONSTRAINT ck_award_policies_none_consistency
        CHECK (
            ranking_basis <> 'NONE'
            OR (
                primary_standing_mode = 'NONE'
                AND absolute_prize_places = 0
                AND category_enabled = FALSE
                AND category_prize_places = 0
                AND exclude_absolute_winners_from_category = FALSE
            )
        );

ALTER TABLE races
    DROP CONSTRAINT ck_races_public_ranking_basis,
    ADD CONSTRAINT ck_races_public_ranking_basis
        CHECK (public_ranking_basis IN ('GUN_TIME', 'CHIP_TIME', 'NONE'));
