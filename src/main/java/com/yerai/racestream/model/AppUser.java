/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description Entidad persistente de usuario local con rol, proveedor y aceptacion legal
 */
package com.yerai.racestream.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(nullable = false)
    private boolean policiesAccepted;

    @Column(nullable = false)
    private boolean cookieConsent;

    @Column(nullable = false)
    private boolean notificationsEnabled;

    @Column(nullable = false)
    private boolean privateProfile = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Inicializa fechas antes de guardar el usuario
     */
    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public AuthProvider getProvider() { return provider; }
    public void setProvider(AuthProvider provider) { this.provider = provider; }
    public boolean isPoliciesAccepted() { return policiesAccepted; }
    public void setPoliciesAccepted(boolean policiesAccepted) { this.policiesAccepted = policiesAccepted; }
    public boolean isCookieConsent() { return cookieConsent; }
    public void setCookieConsent(boolean cookieConsent) { this.cookieConsent = cookieConsent; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public boolean isPrivateProfile() { return privateProfile; }
    public void setPrivateProfile(boolean privateProfile) { this.privateProfile = privateProfile; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
