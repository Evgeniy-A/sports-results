package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultStatus;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class ResultFactualMutationService {

    public static final long MAX_RESULT_TIME_MS = 2_592_000_000L;

    private final ResultRepository resultRepository;
    private final AdminChangeLogRepository changeLogRepository;

    public ResultFactualMutationService(
            ResultRepository resultRepository,
            AdminChangeLogRepository changeLogRepository
    ) {
        this.resultRepository = resultRepository;
        this.changeLogRepository = changeLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(
            Result result,
            String requestedStatus,
            Long gunTimeMs,
            Long chipTimeMs,
            String actor
    ) {
        String status = normalizeStatus(requestedStatus);
        validateTime(gunTimeMs, "desiredGunTimeMs");
        validateTime(chipTimeMs, "desiredChipTimeMs");
        List<AdminChangeLog> changes = new ArrayList<>();
        change(changes, actor, result.getId(), "status", result.getStatus(), status, result::setStatus);
        change(changes, actor, result.getId(), "gunTimeMs", milliseconds(result.getGunTime()), gunTimeMs,
                value -> result.setGunTime(duration(value)));
        change(changes, actor, result.getId(), "chipTimeMs", milliseconds(result.getChipTime()), chipTimeMs,
                value -> result.setChipTime(duration(value)));
        if (changes.isEmpty()) {
            return false;
        }
        resultRepository.saveAndFlush(result);
        changeLogRepository.saveAll(changes);
        return true;
    }

    public String normalizeStatus(String status) {
        return ResultStatus.normalize(status)
                .orElseThrow(() -> new InvalidRequestException(
                        "INVALID_RESULT_STATUS",
                        "Unknown result status. Allowed values: " + ResultStatus.allowedCodes()
                ));
    }

    public void validateTime(Long value, String field) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            throw new InvalidRequestException("INVALID_RESULT_TIME", field + " must not be negative");
        }
        if (value > MAX_RESULT_TIME_MS) {
            throw new InvalidRequestException(
                    "RESULT_TIME_TOO_LARGE",
                    field + " must not exceed " + MAX_RESULT_TIME_MS + " milliseconds"
            );
        }
    }

    private static <T> void change(
            List<AdminChangeLog> changes,
            String actor,
            Long resultId,
            String field,
            T oldValue,
            T newValue,
            Consumer<T> setter
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        setter.accept(newValue);
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.RESULT);
        log.setEntityId(resultId);
        log.setFieldName(field);
        log.setOldValue(stringValue(oldValue));
        log.setNewValue(stringValue(newValue));
        changes.add(log);
    }

    private static Long milliseconds(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    private static Duration duration(Long milliseconds) {
        return milliseconds == null ? null : Duration.ofMillis(milliseconds);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
