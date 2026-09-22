package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.EventSeriesStartCategoryTemplate;

import java.util.List;
import java.util.Optional;

public interface EventSeriesStartCategoryTemplateRepository
        extends JpaRepository<EventSeriesStartCategoryTemplate, Long> {

    List<EventSeriesStartCategoryTemplate> findAllByTemplateStartIdOrderByDisplayOrderAscIdAsc(Long templateStartId);

    Optional<EventSeriesStartCategoryTemplate> findByIdAndTemplateStartId(Long id, Long templateStartId);

    Optional<EventSeriesStartCategoryTemplate> findByTemplateStartIdAndSourceName(
            Long templateStartId,
            String sourceName
    );

    @Query("select category from EventSeriesStartCategoryTemplate category "
            + "join fetch category.templateStart template "
            + "where template.eventSeries.id = :eventSeriesId "
            + "order by template.displayOrder, category.displayOrder, category.id")
    List<EventSeriesStartCategoryTemplate> findAllByEventSeriesId(
            @Param("eventSeriesId") Long eventSeriesId
    );
}
