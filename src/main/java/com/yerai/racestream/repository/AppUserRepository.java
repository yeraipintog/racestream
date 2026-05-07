/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 05-05-2026
 * @modified 07-05-2026
 * @description Repositorio de usuarios para autenticacion, cuenta y restablecimiento de contrasena
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByNameIgnoreCase(String name);
    Optional<AppUser> findByPasswordResetToken(String passwordResetToken);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByNameIgnoreCase(String name);
    List<AppUser> findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc(String email, String name);
}
