package ru.sportsresults.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.domain.AdminUserRole;
import ru.sportsresults.repository.AdminUserRepository;
import ru.sportsresults.repository.EventUserAccessRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;

@Service("eventAccessService")
public class EventAccessService {

    private final AdminUserRepository userRepository;
    private final EventUserAccessRepository accessRepository;
    private final RaceRepository raceRepository;
    private final ResultRepository resultRepository;
    private final RegistrationRepository registrationRepository;

    public EventAccessService(
            AdminUserRepository userRepository,
            EventUserAccessRepository accessRepository,
            RaceRepository raceRepository,
            ResultRepository resultRepository,
            RegistrationRepository registrationRepository
    ) {
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.raceRepository = raceRepository;
        this.resultRepository = resultRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional(readOnly = true)
    public boolean canAccessEvent(Authentication authentication, Long eventId) {
        if (authentication == null || !authentication.isAuthenticated() || eventId == null) {
            return false;
        }
        if (hasRole(authentication, AdminUserRole.ORGANIZER)) {
            return true;
        }
        return hasRole(authentication, AdminUserRole.TIMEKEEPER)
                && accessRepository.existsByUserUsernameIgnoreCaseAndEventId(authentication.getName(), eventId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessRace(Authentication authentication, Long raceId) {
        if (raceId == null) {
            return false;
        }
        if (hasRole(authentication, AdminUserRole.ORGANIZER)) {
            return true;
        }
        return raceRepository.findEventIdByRaceId(raceId)
                .map(eventId -> canAccessEvent(authentication, eventId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessResult(Authentication authentication, Long resultId) {
        if (resultId == null) {
            return false;
        }
        if (hasRole(authentication, AdminUserRole.ORGANIZER)) {
            return true;
        }
        return resultRepository.findEventIdByResultId(resultId)
                .map(eventId -> canAccessEvent(authentication, eventId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessRegistration(Authentication authentication, Long registrationId) {
        if (registrationId == null) {
            return false;
        }
        if (hasRole(authentication, AdminUserRole.ORGANIZER)) {
            return true;
        }
        return registrationRepository.findEventIdByRegistrationId(registrationId)
                .map(eventId -> canAccessEvent(authentication, eventId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public String eventScopeUsername(String username) {
        AdminUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated administrative user is missing"));
        return user.getRole() == AdminUserRole.ORGANIZER ? null : user.getUsername();
    }

    private static boolean hasRole(Authentication authentication, AdminUserRole role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
