/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 05-05-2026
 * @description Repositorio de mensajes enviados desde Contacto
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
}
