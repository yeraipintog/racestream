/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.4
 * @created 05-05-2026
 * @modified 27-05-2026
 * @description Servicio de carga de usuarios locales, alias admin y bloqueo administrativo para Spring Security
 */
package com.yerai.racestream.service;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.config.AdminUserInitializer;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RaceStreamUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final BlockedEmailRepository blockedEmailRepository;

    public RaceStreamUserDetailsService(AppUserRepository appUserRepository, BlockedEmailRepository blockedEmailRepository) {
        this.appUserRepository = appUserRepository;
        this.blockedEmailRepository = blockedEmailRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.4
     * @created 05-05-2026
     * @modified 27-05-2026
     * @description Carga el usuario por email, nombre visible o alias admin,
     *              valida el bloqueo y adapta su rol a Spring Security
     * @param username Email, nombre visible o alias del usuario
     * @return Usuario autenticable
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        String login = AdminUserInitializer.ADMIN_LOGIN.equalsIgnoreCase(username)
                ? AdminUserInitializer.ADMIN_EMAIL
                : username == null ? "" : username.trim();
        AppUser user = appUserRepository.findByEmailIgnoreCase(login)
                .or(() -> appUserRepository.findByNameIgnoreCase(login))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        if (blockedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new DisabledException("Cuenta bloqueada por administración");
        }
        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}
