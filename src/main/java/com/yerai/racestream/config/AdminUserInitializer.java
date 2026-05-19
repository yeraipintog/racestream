/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 06-05-2026
 * @modified 18-05-2026
 * @description Inicializa el acceso especial de administrador local para RaceStream con cookies tecnicas aceptadas
 */
package com.yerai.racestream.config;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.AuthProvider;
import com.yerai.racestream.model.CookieConsentStatus;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminUserInitializer {

    public static final String ADMIN_LOGIN = "admin";
    public static final String ADMIN_EMAIL = "admin@racestream.local";

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Crea o actualiza el usuario admin con rol ADMIN y password local controlada
     * @param appUserRepository Repositorio de usuarios
     * @param passwordEncoder Codificador de contrasenas
     * @return Runner de inicializacion
     */
    @Bean
    public CommandLineRunner ensureAdminUser(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            AppUser admin = appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseGet(AppUser::new);
            admin.setName(ADMIN_LOGIN);
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_LOGIN));
            admin.setRole(UserRole.ADMIN);
            admin.setProvider(AuthProvider.LOCAL);
            admin.setPoliciesAccepted(true);
            admin.setCookieConsentStatus(CookieConsentStatus.ACCEPTED);
            appUserRepository.save(admin);
        };
    }
}
