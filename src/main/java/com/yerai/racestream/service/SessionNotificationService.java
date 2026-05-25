/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 20-05-2026
 * @modified 24-05-2026
 * @description Genera avisos de inicio y resumen de sesiones favoritas en app y por correo
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.model.AppNotification;
import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.SessionNotificationLog;
import com.yerai.racestream.model.UserFavorite;
import com.yerai.racestream.repository.AppNotificationRepository;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.SessionNotificationLogRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SessionNotificationService {

    private static final Duration START_WINDOW = Duration.ofMinutes(12);
    private static final Duration SUMMARY_WINDOW = Duration.ofMinutes(45);

    private final AppUserRepository appUserRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final SessionNotificationLogRepository logRepository;
    private final F1ScheduleService f1ScheduleService;
    private final OpenF1Service openF1Service;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromEmail;
    private final boolean mailEnabled;

    public SessionNotificationService(
            AppUserRepository appUserRepository,
            UserFavoriteRepository userFavoriteRepository,
            AppNotificationRepository appNotificationRepository,
            SessionNotificationLogRepository logRepository,
            F1ScheduleService f1ScheduleService,
            OpenF1Service openF1Service,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${racestream.notifications.mail-enabled:${racestream.contact.mail-enabled:false}}") boolean mailEnabled) {
        this.appUserRepository = appUserRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.appNotificationRepository = appNotificationRepository;
        this.logRepository = logRepository;
        this.f1ScheduleService = f1ScheduleService;
        this.openF1Service = openF1Service;
        this.mailSenderProvider = mailSenderProvider;
        this.fromEmail = fromEmail;
        this.mailEnabled = mailEnabled;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Revisa periódicamente sesiones de GP favoritos sin bloquear el arranque
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    @Transactional
    public void checkSessionNotifications() {
        Instant now = Instant.now();
        int currentYear = Year.now().getValue();
        appUserRepository.findAll().stream()
                .filter(user -> user.isNotificationsEnabled() || user.isEmailNotificationsEnabled())
                .forEach(user -> notifyFavoriteSessions(user, currentYear, now));
    }

    private void notifyFavoriteSessions(AppUser user, int currentYear, Instant now) {
        userFavoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(favorite -> isCurrentGpFavorite(favorite, currentYear))
                .forEach(favorite -> readSessions(favorite).forEach(context -> {
                    JsonNode session = context.session();
                    Instant start = parseInstant(session.path("date_start").asText(""));
                    Instant end = parseInstant(session.path("date_end").asText(""));
                    String sessionName = translateSessionName(session.path("session_name").asText("Sesión"));
                    String place = resolvePlaceLabel(context.meeting(), favorite);
                    String sessionKey = session.path("session_key").asText(favorite.getExternalId() + ":" + sessionName);
                    if (isInWindow(now, start, START_WINDOW)) {
                        String message = "Accede al panel 'En Vivo' para más información.";
                        deliverOnce(user, "session-start:" + sessionKey, sessionName + " en directo en " + place, message, "SESSION_START");
                    }
                    if (isInWindow(now, end, SUMMARY_WINDOW)) {
                        String topThree = buildTopThreeResults(session.path("session_key").asText(""));
                        if (topThree == null) return;
                        String message = topThree;
                        deliverOnce(user, "session-summary:" + sessionKey, "Resumen de " + sessionName + " en " + place, message, "SESSION_SUMMARY");
                    }
                }));
    }

    private List<SessionContext> readSessions(UserFavorite favorite) {
        try {
            Integer meetingKey = resolveMeetingKey(favorite);
            if (meetingKey == null) return List.of();
            JsonNode meeting = f1ScheduleService.getMeetingByKey(meetingKey);
            JsonNode sessions = f1ScheduleService.getSessionsByMeeting(meetingKey);
            List<SessionContext> rows = new ArrayList<>();
            if (sessions != null && sessions.isArray()) {
                sessions.forEach(session -> rows.add(new SessionContext(meeting, session)));
            }
            return rows;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private Integer resolveMeetingKey(UserFavorite favorite) {
        try {
            return Integer.parseInt(favorite.getExternalId());
        } catch (RuntimeException ignored) {
            JsonNode meetings = f1ScheduleService.getCalendarMeetingsByYear(favorite.getSeasonYear());
            String source = normalize(favorite.getTitle() + " " + favorite.getDescription());
            if (meetings != null && meetings.isArray()) {
                for (JsonNode meeting : meetings) {
                    String candidate = normalize(meeting.path("meeting_name").asText() + " "
                            + meeting.path("country_name").asText() + " "
                            + meeting.path("location").asText());
                    String country = normalize(meeting.path("country_name").asText());
                    if (!source.isBlank() && (candidate.contains(source) || source.contains(candidate)
                            || (!country.isBlank() && source.contains(country)))) {
                        int key = meeting.path("meeting_key").asInt(0);
                        return key == 0 ? null : key;
                    }
                }
            }
            return null;
        }
    }

    private boolean isCurrentGpFavorite(UserFavorite favorite, int currentYear) {
        return favorite != null
                && "gp".equalsIgnoreCase(favorite.getType())
                && (favorite.getSeasonYear() == null || favorite.getSeasonYear() == currentYear);
    }

    private boolean isInWindow(Instant now, Instant eventTime, Duration window) {
        return eventTime != null && !eventTime.isAfter(now) && eventTime.plus(window).isAfter(now);
    }

    private void deliverOnce(AppUser user, String eventKey, String title, String message, String type) {
        SessionNotificationLog existingLog = logRepository.findByUserAndEventKey(user, eventKey).orElse(null);
        if (existingLog != null) {
            retryMissingEmail(user, title, message, existingLog);
            return;
        }
        boolean mailSent = false;
        if (user.isNotificationsEnabled()) {
            saveAppNotification(user, title, message, type);
        }
        if (user.isEmailNotificationsEnabled()) {
            mailSent = sendEmail(user, title, message);
        }
        SessionNotificationLog log = new SessionNotificationLog();
        log.setUser(user);
        log.setEventKey(eventKey);
        log.setType(type);
        log.setMailSent(mailSent);
        logRepository.save(log);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @description Reintenta el correo si el aviso web ya existia pero el envio SMTP fallo
     * @param user Usuario destinatario
     * @param title Titulo del aviso
     * @param message Mensaje del aviso
     * @param log Marca existente de notificacion
     */
    private void retryMissingEmail(AppUser user, String title, String message, SessionNotificationLog log) {
        if (!user.isEmailNotificationsEnabled() || log.isMailSent()) {
            return;
        }
        boolean mailSent = sendEmail(user, title, message);
        if (mailSent) {
            log.setMailSent(true);
            logRepository.save(log);
        }
    }

    private void saveAppNotification(AppUser user, String title, String message, String type) {
        AppNotification notification = new AppNotification();
        notification.setUser(user);
        notification.setTitle(truncate(title, 120));
        notification.setMessage(truncate(message, 500));
        notification.setType(type);
        appNotificationRepository.save(notification);
    }

    private boolean sendEmail(AppUser user, String title, String message) {
        if (!mailEnabled) return false;
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) return false;
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(user.getEmail());
            if (!fromEmail.isBlank()) email.setFrom(fromEmail);
            email.setSubject("[RaceStream] " + truncate(title, 90));
            email.setText("""
                    RaceStream
                    %s

                    Hola, %s:

                    %s

                    --
                    Equipo de RaceStream
                    Este mensaje automático se envía porque tienes activadas las notificaciones de sesiones favoritas. Puedes cambiarlo desde tus preferencias.
                    """.formatted(truncate(title, 90), user.getName(), message));
            mailSender.send(email);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String buildTopThreeResults(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return null;
        }
        try {
            JsonNode results = openF1Service.getSessionResults(Integer.parseInt(sessionKey));
            JsonNode drivers = openF1Service.getDrivers(Integer.parseInt(sessionKey));
            List<JsonNode> rows = new ArrayList<>();
            if (results != null && results.isArray()) results.forEach(rows::add);
            if (rows.isEmpty()) return null;
            rows.sort(Comparator.comparingInt(row -> row.path("position").asInt(999)));
            return rows.stream().limit(3)
                    .map(row -> row.path("position").asText("-") + ". " + driverName(row.path("driver_number").asInt(0), drivers))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolvePlaceLabel(JsonNode meeting, UserFavorite favorite) {
        String location = clean(meeting == null ? "" : meeting.path("location").asText(""));
        String country = clean(meeting == null ? "" : meeting.path("country_name").asText(""));
        if (!location.isBlank() && !country.isBlank()) {
            return location + ", " + country;
        }
        String description = clean(favorite == null ? "" : favorite.getDescription());
        if (!description.isBlank()) {
            return description;
        }
        return clean(favorite == null ? "Gran Premio" : favorite.getTitle());
    }

    private String driverName(int number, JsonNode drivers) {
        if (drivers != null && drivers.isArray()) {
            for (JsonNode driver : drivers) {
                if (driver.path("driver_number").asInt(-1) == number) {
                    return driver.path("full_name").asText(driver.path("name_acronym").asText("#" + number));
                }
            }
        }
        return "#" + number;
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String translateSessionName(String name) {
        return Map.of(
                "Practice 1", "Libres 1",
                "Practice 2", "Libres 2",
                "Practice 3", "Libres 3",
                "Qualifying", "Clasificación",
                "Race", "Carrera",
                "Sprint", "Sprint",
                "Sprint Qualifying", "Clasificación sprint",
                "Sprint Shootout", "Clasificación sprint"
        ).getOrDefault(name, name == null || name.isBlank() ? "Sesión" : name);
    }

    private String truncate(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record SessionContext(JsonNode meeting, JsonNode session) {}
}
