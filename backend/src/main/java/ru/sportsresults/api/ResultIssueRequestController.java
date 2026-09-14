package ru.sportsresults.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.CreateMissingResultIssueRequest;
import ru.sportsresults.api.dto.CreateResultCorrectionIssueRequest;
import ru.sportsresults.api.dto.ResultIssueCreatedDto;
import ru.sportsresults.service.ResultIssueRequestService;

@RestController
@RequestMapping("/api/events/{eventId}")
public class ResultIssueRequestController {

    private final ResultIssueRequestService service;

    public ResultIssueRequestController(ResultIssueRequestService service) {
        this.service = service;
    }

    @PostMapping("/result-issue-requests/missing")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultIssueCreatedDto createMissingResult(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateMissingResultIssueRequest request
    ) {
        return service.createMissingResult(eventId, request);
    }

    @PostMapping("/results/{resultId}/result-issue-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultIssueCreatedDto createResultCorrection(
            @PathVariable Long eventId,
            @PathVariable Long resultId,
            @Valid @RequestBody CreateResultCorrectionIssueRequest request
    ) {
        return service.createResultCorrection(eventId, resultId, request);
    }
}
