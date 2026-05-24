/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 19-05-2026
 * @modified 19-05-2026
 * @description Elimina datos dependientes de una cuenta sin dejar referencias huérfanas, incluyendo notificaciones
 */
package com.yerai.racestream.service;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.repository.ContactMessageRepository;
import com.yerai.racestream.repository.AppNotificationRepository;
import com.yerai.racestream.repository.ForumPostLikeRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import com.yerai.racestream.repository.SessionNotificationLogRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAccountDeletionService {

    private final ContactMessageRepository contactMessageRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final ForumPostLikeRepository forumPostLikeRepository;
    private final ForumPostRepository forumPostRepository;
    private final SessionNotificationLogRepository sessionNotificationLogRepository;
    private final UserFavoriteRepository userFavoriteRepository;

    public UserAccountDeletionService(
            ContactMessageRepository contactMessageRepository,
            AppNotificationRepository appNotificationRepository,
            ForumPostLikeRepository forumPostLikeRepository,
            ForumPostRepository forumPostRepository,
            SessionNotificationLogRepository sessionNotificationLogRepository,
            UserFavoriteRepository userFavoriteRepository) {
        this.contactMessageRepository = contactMessageRepository;
        this.appNotificationRepository = appNotificationRepository;
        this.forumPostLikeRepository = forumPostLikeRepository;
        this.forumPostRepository = forumPostRepository;
        this.sessionNotificationLogRepository = sessionNotificationLogRepository;
        this.userFavoriteRepository = userFavoriteRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 19-05-2026
     * @modified 19-05-2026
     * @description Borra favoritos, contacto, notificaciones, publicaciones, respuestas y likes asociados a un usuario
     * @param user Cuenta que se va a eliminar
     */
    @Transactional
    public void deleteUserData(AppUser user) {
        List<ForumPost> userPosts = forumPostRepository.findByAuthor(user);
        List<ForumPost> repliesToUserPosts = userPosts.isEmpty()
                ? List.of()
                : forumPostRepository.findByParentPostIn(userPosts);
        if (!userPosts.isEmpty()) {
            forumPostLikeRepository.deleteByPostIn(userPosts);
        }
        if (!repliesToUserPosts.isEmpty()) {
            forumPostLikeRepository.deleteByPostIn(repliesToUserPosts);
        }
        forumPostLikeRepository.deleteByUser(user);
        forumPostRepository.deleteAll(repliesToUserPosts);
        Set<Long> deletedReplyIds = repliesToUserPosts.stream()
                .map(ForumPost::getId)
                .collect(Collectors.toSet());
        forumPostRepository.deleteAll(userPosts.stream()
                .filter(post -> !deletedReplyIds.contains(post.getId()))
                .toList());
        contactMessageRepository.deleteByUser(user);
        appNotificationRepository.deleteByUser(user);
        sessionNotificationLogRepository.deleteByUser(user);
        userFavoriteRepository.deleteByUser(user);
    }
}
