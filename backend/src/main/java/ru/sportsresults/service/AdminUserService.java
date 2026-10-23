package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AdminUserDto;
import ru.sportsresults.api.dto.AdminUserEventAccessDto;
import ru.sportsresults.api.dto.CreateAdminUserRequest;
import ru.sportsresults.api.dto.CurrentAdminUserDto;
import ru.sportsresults.api.dto.ReplaceAdminUserEventsRequest;
import ru.sportsresults.api.dto.UpdateAdminUserPasswordRequest;
import ru.sportsresults.api.dto.UpdateAdminUserRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.domain.AdminUserRole;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventUserAccess;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.AdminUserRepository;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.EventUserAccessRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminUserService {

    private final AdminUserRepository userRepository;
    private final EventUserAccessRepository accessRepository;
    private final EventRepository eventRepository;
    private final AdminChangeLogRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AdminUserRepository userRepository,
            EventUserAccessRepository accessRepository,
            EventRepository eventRepository,
            AdminChangeLogRepository auditRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.eventRepository = eventRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> list() {
        List<AdminUser> users = userRepository.findAllByOrderByDisplayNameAscUsernameAsc();
        Map<Long, Long> counts = new HashMap<>();
        if (!users.isEmpty()) {
            for (Object[] row : accessRepository.countByUserIds(users.stream().map(AdminUser::getId).toList())) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return users.stream().map(user -> toDto(user, counts.getOrDefault(user.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDto get(Long userId) {
        AdminUser user = requireUser(userId);
        return toDto(user, accessRepository.findEventIdsByUserId(userId).size());
    }

    @Transactional(readOnly = true)
    public CurrentAdminUserDto current(String username) {
        AdminUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_USER_NOT_FOUND", "Administrative user not found"));
        return new CurrentAdminUserDto(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isActive()
        );
    }

    @Transactional
    public AdminUserDto create(CreateAdminUserRequest request, String actor) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new RequestConflictException("ADMIN_USERNAME_EXISTS", "Administrative username already exists");
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().strip());
        user.setRole(request.role());
        user.setActive(request.active());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("ADMIN_USERNAME_EXISTS", "Administrative username already exists");
        }
        audit(actor, user.getId(), "created", null, user.getRole() + ":" + user.isActive());
        return toDto(user, 0);
    }

    @Transactional
    public AdminUserDto update(Long userId, UpdateAdminUserRequest request, String actor) {
        AdminUser user = requireUser(userId);
        String username = normalizeUsername(request.username());
        userRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new RequestConflictException("ADMIN_USERNAME_EXISTS", "Administrative username already exists");
                });
        ensureOrganizerRemains(user, request.role(), request.active());
        String oldUsername = user.getUsername();
        String oldDisplayName = user.getDisplayName();
        AdminUserRole oldRole = user.getRole();
        boolean oldActive = user.isActive();
        user.setUsername(username);
        user.setDisplayName(request.displayName().strip());
        user.setRole(request.role());
        user.setActive(request.active());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("ADMIN_USERNAME_EXISTS", "Administrative username already exists");
        }
        auditChange(actor, userId, "username", oldUsername, user.getUsername());
        auditChange(actor, userId, "displayName", oldDisplayName, user.getDisplayName());
        auditChange(actor, userId, "role", oldRole, user.getRole());
        auditChange(actor, userId, "active", oldActive, user.isActive());
        return toDto(user, accessRepository.findEventIdsByUserId(userId).size());
    }

    @Transactional
    public void updatePassword(Long userId, UpdateAdminUserPasswordRequest request, String actor) {
        AdminUser user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        audit(actor, userId, "password", null, "changed");
    }

    @Transactional(readOnly = true)
    public AdminUserEventAccessDto events(Long userId) {
        requireUser(userId);
        return new AdminUserEventAccessDto(userId, accessRepository.findEventIdsByUserId(userId));
    }

    @Transactional
    public AdminUserEventAccessDto replaceEvents(
            Long userId,
            ReplaceAdminUserEventsRequest request,
            String actor
    ) {
        AdminUser user = requireUser(userId);
        if (user.getRole() != AdminUserRole.TIMEKEEPER) {
            throw new InvalidRequestException(
                    "ORGANIZER_ASSIGNMENTS_NOT_ALLOWED", "Event assignments are only valid for timekeepers"
            );
        }
        Set<Long> requestedIds = new HashSet<>(request.eventIds());
        if (requestedIds.size() != request.eventIds().size()) {
            throw new InvalidRequestException("DUPLICATE_EVENT_ASSIGNMENT", "eventIds must not contain duplicates");
        }
        List<Event> events = eventRepository.findAllById(requestedIds);
        if (events.size() != requestedIds.size()) {
            throw new InvalidRequestException("EVENT_ASSIGNMENT_NOT_FOUND", "One or more events do not exist");
        }
        List<Long> previous = accessRepository.findEventIdsByUserId(userId);
        accessRepository.deleteAllByUserId(userId);
        accessRepository.flush();
        accessRepository.saveAll(events.stream().map(event -> {
            EventUserAccess access = new EventUserAccess();
            access.setUser(user);
            access.setEvent(event);
            return access;
        }).toList());
        List<Long> current = requestedIds.stream().sorted().toList();
        audit(actor, userId, "eventAssignments", previous.toString(), current.toString());
        return new AdminUserEventAccessDto(userId, current);
    }

    private void ensureOrganizerRemains(AdminUser current, AdminUserRole nextRole, boolean nextActive) {
        boolean removesActiveOrganizer = current.getRole() == AdminUserRole.ORGANIZER && current.isActive()
                && (nextRole != AdminUserRole.ORGANIZER || !nextActive);
        if (removesActiveOrganizer && userRepository.countByRoleAndActiveTrue(AdminUserRole.ORGANIZER) <= 1) {
            throw new RequestConflictException(
                    "LAST_ACTIVE_ORGANIZER", "The system must retain at least one active organizer"
            );
        }
    }

    private AdminUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_USER_NOT_FOUND", "Administrative user not found"));
    }

    private static String normalizeUsername(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static AdminUserDto toDto(AdminUser user, long eventCount) {
        return new AdminUserDto(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isActive(),
                user.getRole() == AdminUserRole.ORGANIZER ? 0 : eventCount,
                user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    private void auditChange(String actor, Long userId, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            audit(actor, userId, field, oldValue, newValue);
        }
    }

    private void audit(String actor, Long userId, String field, Object oldValue, Object newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.ADMIN_USER);
        log.setEntityId(userId);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        auditRepository.save(log);
    }
}
