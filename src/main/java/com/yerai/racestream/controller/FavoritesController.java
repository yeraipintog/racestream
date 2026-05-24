/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 05-05-2026
 * @modified 13-05-2026
 * @description API privada de favoritos de RaceStream
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.UserFavorite;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoritesController {

    private final AppUserRepository appUserRepository;
    private final UserFavoriteRepository favoriteRepository;

    public FavoritesController(AppUserRepository appUserRepository, UserFavoriteRepository favoriteRepository) {
        this.appUserRepository = appUserRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Lista favoritos del usuario autenticado
     * @param authentication Sesión actual
     * @return Favoritos ordenados por fecha
     */
    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        AppUser user = currentUser(authentication);
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Crea un favorito privado
     * @param request Datos del favorito
     * @param authentication Sesión actual
     * @return Favorito guardado
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody FavoriteRequest request, Authentication authentication) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos del favorito obligatorios"));
        }
        if (isBlank(request.type()) || isBlank(request.externalId()) || isBlank(request.title())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo, identificador y título son obligatorios"));
        }
        AppUser user = currentUser(authentication);
        UserFavorite favorite = favoriteRepository
                .findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, request.type().trim(), request.externalId().trim(), request.seasonYear())
                .orElseGet(UserFavorite::new);
        favorite.setUser(user);
        favorite.setType(request.type().trim());
        favorite.setExternalId(request.externalId().trim());
        favorite.setSeasonYear(request.seasonYear());
        favorite.setTitle(request.title().trim());
        favorite.setUrl(blankToNull(request.url()));
        favorite.setDescription(blankToNull(request.description()));
        return ResponseEntity.ok(toResponse(favoriteRepository.save(favorite)));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Elimina un favorito del usuario autenticado
     * @param id Identificador interno
     * @param authentication Sesión actual
     * @return Estado de borrado
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        AppUser user = currentUser(authentication);
        return favoriteRepository.findByIdAndUser(id, user).map(favorite -> {
            favoriteRepository.delete(favorite);
            return ResponseEntity.ok(Map.of("deleted", true));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(UserFavorite favorite) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", favorite.getId());
        response.put("type", favorite.getType());
        response.put("externalId", favorite.getExternalId());
        response.put("seasonYear", favorite.getSeasonYear());
        response.put("title", favorite.getTitle());
        response.put("url", favorite.getUrl() == null ? "" : favorite.getUrl());
        response.put("description", favorite.getDescription() == null ? "" : favorite.getDescription());
        response.put("createdAt", favorite.getCreatedAt().toString());
        return response;
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public record FavoriteRequest(String type, String externalId, Integer seasonYear, String title, String url, String description) {}
}
