/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description Repositorio de favoritos asociados a usuarios registrados
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {
    List<UserFavorite> findByUserOrderByCreatedAtDesc(AppUser user);
    Optional<UserFavorite> findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCase(AppUser user, String type, String externalId);
    Optional<UserFavorite> findByIdAndUser(Long id, AppUser user);
    void deleteByUser(AppUser user);
}
