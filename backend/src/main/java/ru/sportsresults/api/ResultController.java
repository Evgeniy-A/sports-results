package ru.sportsresults.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.ResultDetailsDto;
import ru.sportsresults.api.dto.ResultListItemDto;
import ru.sportsresults.service.ResultQueryService;

@RestController
@RequestMapping("/api")
public class ResultController {

    private final ResultQueryService resultQueryService;

    public ResultController(ResultQueryService resultQueryService) {
        this.resultQueryService = resultQueryService;
    }

    @GetMapping("/events/{eventId}/results")
    public PageResponse<ResultListItemDto> searchResults(
            @PathVariable Long eventId,
            @RequestParam Long raceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String bib,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long clusterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "place") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return resultQueryService.search(
                eventId,
                raceId,
                name,
                bib,
                gender,
                categoryId,
                clusterId,
                status,
                page,
                size,
                sort,
                direction
        );
    }

    @GetMapping("/results/{resultId}")
    public ResultDetailsDto getResult(@PathVariable Long resultId) {
        return resultQueryService.getPublishedResult(resultId);
    }
}
