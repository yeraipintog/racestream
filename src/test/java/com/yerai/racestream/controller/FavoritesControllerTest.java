/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 13-05-2026
 * @modified 13-05-2026
 * @description Tests de favoritos con temporada seleccionada
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.UserFavorite;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoritesControllerTest {

    private AppUser user;
    private UserFavoriteRepository favoriteRepository;
    private FavoritesController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        favoriteRepository = mock(UserFavoriteRepository.class);
        controller = new FavoritesController(appUserRepository, favoriteRepository);
        authentication = mock(Authentication.class);
        user = new AppUser();
        user.setEmail("user@test.com");
        when(authentication.getName()).thenReturn("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(favoriteRepository.save(any(UserFavorite.class))).thenAnswer(invocation -> {
            UserFavorite favorite = invocation.getArgument(0);
            if (favorite.getCreatedAt() == null) {
                favorite.setCreatedAt(Instant.now());
            }
            return favorite;
        });
    }

    @Test
    void sameExternalIdCanBeSavedInDifferentSeasons() {
        when(favoriteRepository.findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, "driver", "alonso", 2024))
                .thenReturn(Optional.empty());
        when(favoriteRepository.findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, "driver", "alonso", 2026))
                .thenReturn(Optional.empty());

        var first = controller.create(new FavoritesController.FavoriteRequest(
                "driver", "alonso", 2024, "Fernando Alonso", "drivers.html?year=2024", "Piloto"), authentication);
        var second = controller.create(new FavoritesController.FavoriteRequest(
                "driver", "alonso", 2026, "Fernando Alonso", "drivers.html?year=2026", "Piloto"), authentication);

        assertThat(((Map<?, ?>) first.getBody()).get("seasonYear")).isEqualTo(2024);
        assertThat(((Map<?, ?>) second.getBody()).get("seasonYear")).isEqualTo(2026);
        verify(favoriteRepository).findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, "driver", "alonso", 2024);
        verify(favoriteRepository).findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, "driver", "alonso", 2026);
    }

    @Test
    void identicalFavoriteReusesExistingSeasonRecord() {
        UserFavorite existing = new UserFavorite();
        existing.setUser(user);
        existing.setType("team");
        existing.setExternalId("ferrari");
        existing.setSeasonYear(2005);
        existing.setTitle("Ferrari");
        existing.setCreatedAt(Instant.now());
        when(favoriteRepository.findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, "team", "ferrari", 2005))
                .thenReturn(Optional.of(existing));

        var response = controller.create(new FavoritesController.FavoriteRequest(
                "team", "ferrari", 2005, "Ferrari", "teams.html?year=2005", "Escudería"), authentication);

        assertThat(((Map<?, ?>) response.getBody()).get("seasonYear")).isEqualTo(2005);
        verify(favoriteRepository).save(eq(existing));
    }
}
