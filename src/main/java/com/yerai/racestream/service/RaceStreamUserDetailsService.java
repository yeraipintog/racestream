/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 05-05-2026
 * @description Servicio de carga de usuarios locales para Spring Security
 */
package com.yerai.racestream.service;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RaceStreamUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public RaceStreamUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Carga el usuario por email y adapta su rol a Spring Security
     * @param username Email del usuario
     * @return Usuario autenticable
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}
