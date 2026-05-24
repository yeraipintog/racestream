/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.3
 * @created 05-05-2026
 * @modified 22-05-2026
 * @description Repositorio de usuarios para autenticacion, cuenta y restablecimiento de contrasena
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByNameIgnoreCase(String name);
    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Recupera con bloqueo el usuario del token para consumirlo una sola vez
     * @param passwordResetToken Token de recuperacion
     * @return Usuario asociado al token
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByPasswordResetToken(String passwordResetToken);
    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Comprueba si un token de recuperacion existe y no ha caducado
     * @param passwordResetToken Token de recuperacion
     * @param expiresAt Fecha minima de vigencia
     * @return true si el token esta vigente
     */
    boolean existsByPasswordResetTokenAndPasswordResetExpiresAtAfter(String passwordResetToken, Instant expiresAt);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByNameIgnoreCase(String name);
    List<AppUser> findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc(String email, String name);
}
