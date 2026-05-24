/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.1
 * @created 06-05-2026
 * @modified 22-05-2026
 * @description Inicializa el acceso administrador y datos reales de prueba, foro, likes y favoritos sin duplicados
 */
package com.yerai.racestream.config;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.AuthProvider;
import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.model.CookieConsentStatus;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.ForumPostLike;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.model.UserFavorite;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ContactMessageRepository;
import com.yerai.racestream.repository.ForumPostLikeRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminUserInitializer {

    public static final String ADMIN_LOGIN = "admin";
    public static final String ADMIN_EMAIL = "contact.racestream@gmail.com";

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 06-05-2026
     * @description Crea o actualiza el usuario admin con rol ADMIN y password local
     *              controlada
     * @param appUserRepository Repositorio de usuarios
     * @param passwordEncoder   Codificador de contrasenas
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

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 19-05-2026
     * @modified 19-05-2026
     * @description Crea usuarios, publicaciones, likes y favoritos de prueba con datos de F1 reales sin duplicar registros
     * @param appUserRepository Repositorio de usuarios
     * @param contactMessageRepository Repositorio de contacto
     * @param forumPostRepository Repositorio del foro
     * @param passwordEncoder Codificador de contrasenas
     * @return Runner de inicializacion de contenido
     */
    @Bean
    public CommandLineRunner ensureDemoData(
            AppUserRepository appUserRepository,
            ContactMessageRepository contactMessageRepository,
            ForumPostRepository forumPostRepository,
            ForumPostLikeRepository forumPostLikeRepository,
            UserFavoriteRepository userFavoriteRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            AppUser marina = ensureDemoUser(appUserRepository, passwordEncoder, "MarinaF1", "marina.f1.demo@racestream.local");
            AppUser nico = ensureDemoUser(appUserRepository, passwordEncoder, "NicoBox", "nico.box.demo@racestream.local");
            AppUser laura = ensureDemoUser(appUserRepository, passwordEncoder, "LauraPitwall", "laura.pitwall.demo@racestream.local");
            AppUser yerai = ensureDemoUser(appUserRepository, passwordEncoder, "YeraiPinto", "pintoyerai05@gmail.com");
            yerai.setNotificationsEnabled(true);
            yerai.setEmailNotificationsEnabled(true);
            yerai.setFavoriteDigestEnabled(true);
            yerai.setFavoriteDigestEmailEnabled(true);
            appUserRepository.save(yerai);

            ensureContactMessage(contactMessageRepository, marina, "Página", "Calendario móvil",
                    "En móvil conviene mantener visible el GP seleccionado y sus sesiones sin perder el contexto de fecha.",
                    "demo-contact-calendar-mobile");
            ensureContactMessage(contactMessageRepository, nico, "Grand Prix", "GP España",
                    "El Circuit de Barcelona-Catalunya debería destacar longitud, curvas y vuelta rápida para entender mejor el fin de semana.",
                    "demo-contact-spain-gp");
            ensureContactMessage(contactMessageRepository, laura, "Sesión", "Sprint",
                    "En los fines de semana Sprint sería útil diferenciar la clasificación sprint de la clasificación de carrera.",
                    "demo-contact-sprint-session");

            ForumPost madridPost = ensureForumPost(forumPostRepository, marina, "Grand Prix", "GP Madrid 2026",
                    "El Madring será un circuito urbano en IFEMA Madrid con rectas largas y zonas de frenada fuertes. Puede ser clave explicar los sectores antes del debut.",
                    "demo-forum-madrid-2026", null);
            ensureForumPost(forumPostRepository, nico, "Grand Prix", "Re: GP Madrid 2026",
                    "También ayudaría comparar el trazado con circuitos urbanos actuales para que un usuario nuevo entienda dónde pueden aparecer adelantamientos.",
                    "demo-forum-madrid-2026-reply", madridPost);

            ForumPost tyresPost = ensureForumPost(forumPostRepository, laura, "Sesión", "Neumáticos 2026",
                    "Pirelli suele condicionar mucho la estrategia: compuesto, degradación y ventana de parada deberían verse juntos en las sesiones.",
                    "demo-forum-tyres-2026", null);
            ensureForumPost(forumPostRepository, marina, "Sesión", "Re: Neumáticos",
                    "Me parece clave que RaceStream explique la diferencia entre gap, intervalo y ritmo de vuelta con ejemplos durante la carrera.",
                    "demo-forum-tyres-2026-reply", tyresPost);
            ForumPost canadaPost = ensureForumPost(forumPostRepository, nico, "Grand Prix", "Canadá 2026",
                    "Montreal suele combinar frenadas fuertes, muros cerca y cambios de ritmo. Para usuarios nuevos ayuda explicar por qué el coche de seguridad puede cambiar toda la carrera.",
                    "demo-forum-canada-2026", null);
            ensureForumPost(forumPostRepository, laura, "Datos", "Top 3 post",
                    "El resumen tras cada sesión debería incluir top 3, gap principal, neumáticos y una frase sencilla que explique qué ha pasado.",
                    "demo-forum-session-summary", null);

            ensureLike(forumPostLikeRepository, madridPost, nico);
            ensureLike(forumPostLikeRepository, madridPost, laura);
            ensureLike(forumPostLikeRepository, madridPost, yerai);
            ensureLike(forumPostLikeRepository, tyresPost, marina);
            ensureLike(forumPostLikeRepository, tyresPost, nico);
            ensureLike(forumPostLikeRepository, canadaPost, marina);
            ensureLike(forumPostLikeRepository, canadaPost, yerai);

            ensureFavorite(userFavoriteRepository, marina, "GP", "canada-2026", 2026, "GP de Canadá 2026", "/calendar.html?year=2026", "Montreal, Canadá");
            ensureFavorite(userFavoriteRepository, nico, "Piloto", "alonso", 2026, "Fernando Alonso", "/drivers.html?year=2026&driverId=alonso", "Aston Martin");
            ensureFavorite(userFavoriteRepository, laura, "Escudería", "ferrari", 2026, "Ferrari", "/teams.html?year=2026&constructorId=ferrari", "Escudería 2026");
            ensureFavorite(userFavoriteRepository, yerai, "GP", "canada-2026", 2026, "GP de Canadá 2026", "/calendar.html?year=2026", "Montreal, Canadá");
            ensureFavorite(userFavoriteRepository, yerai, "Piloto", "alonso", 2026, "Fernando Alonso", "/drivers.html?year=2026&driverId=alonso", "Aston Martin");
        };
    }

    private AppUser ensureDemoUser(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            String name,
            String email) {
        return appUserRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setName(name);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("RaceStream2026!"));
            user.setRole(UserRole.USER);
            user.setProvider(AuthProvider.LOCAL);
            user.setPoliciesAccepted(true);
            user.setCookieConsentStatus(CookieConsentStatus.ACCEPTED);
            user.setNotificationsEnabled(true);
            user.setEmailNotificationsEnabled(false);
            user.setFavoriteDigestEnabled(true);
            user.setFavoriteDigestEmailEnabled(false);
            return appUserRepository.save(user);
        });
    }

    private void ensureContactMessage(
            ContactMessageRepository contactMessageRepository,
            AppUser user,
            String topic,
            String subject,
            String body,
            String clientRequestId) {
        ContactMessage message = contactMessageRepository.findByUserAndClientRequestId(user, clientRequestId).orElseGet(ContactMessage::new);
        if (message.getId() == null) {
            message.setUser(user);
            message.setClientRequestId(clientRequestId);
        }
        message.setTopic(topic);
        message.setSubject(subject);
        message.setMessage(body);
        contactMessageRepository.save(message);
    }

    private ForumPost ensureForumPost(
            ForumPostRepository forumPostRepository,
            AppUser user,
            String category,
            String title,
            String content,
            String clientRequestId,
            ForumPost parentPost) {
        ForumPost post = forumPostRepository.findByAuthorAndClientRequestId(user, clientRequestId).orElseGet(ForumPost::new);
        if (post.getId() == null) {
            post.setAuthor(user);
            post.setClientRequestId(clientRequestId);
        }
        post.setCategory(category);
        post.setTitle(title);
        post.setContent(content);
        post.setParentPost(parentPost);
        return forumPostRepository.save(post);
    }

    private void ensureLike(ForumPostLikeRepository forumPostLikeRepository, ForumPost post, AppUser user) {
        forumPostLikeRepository.findByPostAndUser(post, user).orElseGet(() -> {
            ForumPostLike like = new ForumPostLike();
            like.setPost(post);
            like.setUser(user);
            return forumPostLikeRepository.save(like);
        });
    }

    private void ensureFavorite(
            UserFavoriteRepository userFavoriteRepository,
            AppUser user,
            String type,
            String externalId,
            Integer seasonYear,
            String title,
            String url,
            String description) {
        UserFavorite favorite = userFavoriteRepository
                .findByUserAndTypeIgnoreCaseAndExternalIdIgnoreCaseAndSeasonYear(user, type, externalId, seasonYear)
                .orElseGet(UserFavorite::new);
        favorite.setUser(user);
        favorite.setType(type);
        favorite.setExternalId(externalId);
        favorite.setSeasonYear(seasonYear);
        favorite.setTitle(title);
        favorite.setUrl(url);
        favorite.setDescription(description);
        userFavoriteRepository.save(favorite);
    }
}
