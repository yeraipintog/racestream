/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 20-05-2026
 * @modified 20-05-2026
 * @description Repositorio de marcas de envío para evitar avisos duplicados
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.SessionNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionNotificationLogRepository extends JpaRepository<SessionNotificationLog, Long> {
    Optional<SessionNotificationLog> findByUserAndEventKey(AppUser user, String eventKey);
    void deleteByUser(AppUser user);
}
