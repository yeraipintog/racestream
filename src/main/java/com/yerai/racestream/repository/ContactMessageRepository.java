/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 05-05-2026
 * @modified 06-05-2026
 * @description Repositorio de mensajes enviados desde Contacto con lectura para administracion
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.model.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    @EntityGraph(attributePaths = "user")
    List<ContactMessage> findTop20ByOrderByCompletedAscCreatedAtDesc();
    void deleteByUser(AppUser user);
}
