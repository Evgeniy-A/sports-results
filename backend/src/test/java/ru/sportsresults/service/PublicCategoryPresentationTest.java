package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.Category;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicCategoryPresentationTest {

    private final PublicCategoryPresentation presentation = new PublicCategoryPresentation();

    @Test
    void recognizesNormalizedBaseCategories() {
        assertThat(List.of("18+ Male", " male (18+) ", "18+М", "М 18+"))
                .allMatch(presentation::isBaseCategory);
        assertThat(List.of("18+ Female", "female_18+", "18+ Ж", "Ж(18+)"))
                .allMatch(presentation::isBaseCategory);
        assertThat(presentation.isBaseCategory("30-39 Male")).isFalse();
    }

    @Test
    void localizesPublicPresentationWithoutChangingStoredValues() {
        Category base = category("18+ Female", "18+ Female");
        Category age = category("30–39 Female", "30–39 Female");

        assertThat(presentation.publicName(base)).isEqualTo("18+ Ж");
        assertThat(presentation.publicName(age)).isEqualTo("30–39 Ж");
        assertThat(base.getSourceName()).isEqualTo("18+ Female");
        assertThat(base.getDisplayName()).isEqualTo("18+ Female");
    }

    private static Category category(String sourceName, String displayName) {
        Category category = new Category();
        category.setSourceName(sourceName);
        category.setDisplayName(displayName);
        return category;
    }
}
