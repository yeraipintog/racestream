/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 05-05-2026
 * @modified 13-05-2026
 * @description Repositorio de mensajes enviados desde Contacto con lectura para administración
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.model.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    @EntityGraph(attributePaths = "user")
    List<ContactMessage> findTop20ByOrderByCompletedAscCreatedAtDesc();
    Optional<ContactMessage> findByUserAndClientRequestId(AppUser user, String clientRequestId);
    void deleteByUser(AppUser user);
}
