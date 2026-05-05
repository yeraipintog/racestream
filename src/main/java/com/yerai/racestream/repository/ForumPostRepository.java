/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 05-05-2026
 * @description Repositorio del foro privado de RaceStream
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    List<ForumPost> findAllByOrderByCreatedAtDesc();
}
