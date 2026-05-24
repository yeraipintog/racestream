/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 20-05-2026
 * @modified 20-05-2026
 * @description Repositorio de notificaciones internas de RaceStream
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppNotification;
import com.yerai.racestream.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop10ByUserAndReadAtIsNullOrderByCreatedAtDesc(AppUser user);
    List<AppNotification> findByIdInAndUser(Collection<Long> ids, AppUser user);
    void deleteByUser(AppUser user);
}
