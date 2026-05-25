/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 22-05-2026
 * @modified 22-05-2026
 * @description Verifica avisos reales de sesiones favoritas sin endpoints de prueba
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerai.racestream.model.AppNotification;
import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.UserFavorite;
import com.yerai.racestream.repository.AppNotificationRepository;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.SessionNotificationLogRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionNotificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Comprueba el formato final de inicio y resumen Top 3
     * @throws Exception Si falla la construcción JSON de datos simulados
     */
    @Test
    @SuppressWarnings("unchecked")
    void checkSessionNotificationsUsesFinalStartAndSummaryMessages() throws Exception {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        UserFavoriteRepository userFavoriteRepository = mock(UserFavoriteRepository.class);
        AppNotificationRepository appNotificationRepository = mock(AppNotificationRepository.class);
        SessionNotificationLogRepository logRepository = mock(SessionNotificationLogRepository.class);
        F1ScheduleService f1ScheduleService = mock(F1ScheduleService.class);
        OpenF1Service openF1Service = mock(OpenF1Service.class);
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);

        AppUser user = new AppUser();
        user.setEmail("piloto@racestream.local");
        user.setNotificationsEnabled(true);
        user.setEmailNotificationsEnabled(true);

        UserFavorite favorite = new UserFavorite();
        favorite.setType("gp");
        favorite.setExternalId("1285");
        favorite.setSeasonYear(Year.now().getValue());
        favorite.setTitle("Canadian Grand Prix");
        favorite.setDescription("Montréal, Canada");

        Instant now = Instant.now();
        String start = now.minus(Duration.ofMinutes(1)).toString();
        String end = now.minus(Duration.ofMinutes(2)).toString();

        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(userFavoriteRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(favorite));
        when(logRepository.findByUserAndEventKey(any(), anyString())).thenReturn(Optional.empty());
        when(f1ScheduleService.getMeetingByKey(1285)).thenReturn(objectMapper.readTree("""
                {"location":"Montréal","country_name":"Canada"}
                """));
        when(f1ScheduleService.getSessionsByMeeting(1285)).thenReturn(objectMapper.readTree("""
                [{"session_key":11280,"session_name":"Race","date_start":"%s","date_end":"%s"}]
                """.formatted(start, end)));
        when(openF1Service.getSessionResults(11280)).thenReturn(objectMapper.readTree("""
                [
                  {"position":1,"driver_number":1},
                  {"position":2,"driver_number":4},
                  {"position":3,"driver_number":16}
                ]
                """));
        when(openF1Service.getDrivers(11280)).thenReturn(objectMapper.readTree("""
                [
                  {"driver_number":1,"full_name":"Max Verstappen"},
                  {"driver_number":4,"full_name":"Lando Norris"},
                  {"driver_number":16,"full_name":"Charles Leclerc"}
                ]
                """));

        List<AppNotification> saved = new ArrayList<>();
        when(appNotificationRepository.save(any(AppNotification.class))).thenAnswer(invocation -> {
            AppNotification notification = invocation.getArgument(0);
            saved.add(notification);
            return notification;
        });

        SessionNotificationService service = new SessionNotificationService(
                appUserRepository,
                userFavoriteRepository,
                appNotificationRepository,
                logRepository,
                f1ScheduleService,
                openF1Service,
                mailSenderProvider,
                "",
                false);

        service.checkSessionNotifications();

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getTitle()).isEqualTo("Carrera en directo en Montréal, Canada");
        assertThat(saved.get(0).getMessage())
                .contains("Accede al panel 'En Vivo' para más información.");
        assertThat(saved.get(1).getTitle()).isEqualTo("Resumen de Carrera en Montréal, Canada");
        assertThat(saved.get(1).getMessage())
                .contains("1. Max Verstappen, 2. Lando Norris, 3. Charles Leclerc");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Comprueba que un aviso ya creado reintenta el correo si SMTP falló antes
     * @throws Exception Si falla la construcción JSON de datos simulados
     */
    @Test
    @SuppressWarnings("unchecked")
    void checkSessionNotificationsRetriesMissingEmailForExistingLog() throws Exception {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        UserFavoriteRepository userFavoriteRepository = mock(UserFavoriteRepository.class);
        AppNotificationRepository appNotificationRepository = mock(AppNotificationRepository.class);
        SessionNotificationLogRepository logRepository = mock(SessionNotificationLogRepository.class);
        F1ScheduleService f1ScheduleService = mock(F1ScheduleService.class);
        OpenF1Service openF1Service = mock(OpenF1Service.class);
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);

        AppUser user = new AppUser();
        user.setEmail("piloto@racestream.local");
        user.setNotificationsEnabled(false);
        user.setEmailNotificationsEnabled(true);

        UserFavorite favorite = new UserFavorite();
        favorite.setType("gp");
        favorite.setExternalId("1285");
        favorite.setSeasonYear(Year.now().getValue());
        favorite.setTitle("Canadian Grand Prix");

        Instant now = Instant.now();
        String start = now.minus(Duration.ofMinutes(1)).toString();
        String end = now.plus(Duration.ofHours(2)).toString();

        com.yerai.racestream.model.SessionNotificationLog existingLog = new com.yerai.racestream.model.SessionNotificationLog();
        existingLog.setUser(user);
        existingLog.setEventKey("session-start:11280");
        existingLog.setType("SESSION_START");
        existingLog.setMailSent(false);

        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(userFavoriteRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(favorite));
        when(logRepository.findByUserAndEventKey(any(), anyString())).thenReturn(Optional.of(existingLog));
        when(f1ScheduleService.getMeetingByKey(1285)).thenReturn(objectMapper.readTree("""
                {"location":"Montreal","country_name":"Canada"}
                """));
        when(f1ScheduleService.getSessionsByMeeting(1285)).thenReturn(objectMapper.readTree("""
                [{"session_key":11280,"session_name":"Race","date_start":"%s","date_end":"%s"}]
                """.formatted(start, end)));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        SessionNotificationService service = new SessionNotificationService(
                appUserRepository,
                userFavoriteRepository,
                appNotificationRepository,
                logRepository,
                f1ScheduleService,
                openF1Service,
                mailSenderProvider,
                "contact@racestream.local",
                true);

        service.checkSessionNotifications();

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(existingLog.isMailSent()).isTrue();
    }
}
