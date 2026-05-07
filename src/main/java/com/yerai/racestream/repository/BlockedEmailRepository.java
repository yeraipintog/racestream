/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 06-05-2026
 * @description Repositorio de correos bloqueados por administracion
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.BlockedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlockedEmailRepository extends JpaRepository<BlockedEmail, Long> {
    Optional<BlockedEmail> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    void deleteByEmailIgnoreCase(String email);
}
