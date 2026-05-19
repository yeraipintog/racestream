/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 05-05-2026
 * @modified 18-05-2026
 * @description Entidad persistente de usuario local con rol, proveedor, preferencias, recuperacion y consentimiento de cookies
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CookieConsentStatus cookieConsentStatus = CookieConsentStatus.UNDECIDED;

    @Column(nullable = false)
    private boolean notificationsEnabled;

    @Column(nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(nullable = false)
    private boolean favoriteDigestEnabled;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean favoriteDigestEmailEnabled;

    @Column(nullable = false)
    private boolean privateProfile = true;

    @Column(length = 96)
    private String passwordResetToken;

    private Instant passwordResetExpiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 05-05-2026
     * @modified 18-05-2026
     * @description Inicializa fechas y sincroniza el estado explicito de cookies antes de persistir
     */
    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        syncCookieConsentState();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Mantiene compatibilidad con el boolean legacy sin perder el estado sin decision
     */
    @PreUpdate
    void preUpdate() {
        syncCookieConsentState();
    }

    private void syncCookieConsentState() {
        if (cookieConsentStatus == null) {
            cookieConsentStatus = cookieConsent ? CookieConsentStatus.ACCEPTED : CookieConsentStatus.UNDECIDED;
        }
        cookieConsent = cookieConsentStatus == CookieConsentStatus.ACCEPTED;
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
    public boolean isCookieConsent() { return getCookieConsentStatus() == CookieConsentStatus.ACCEPTED; }
    public void setCookieConsent(boolean cookieConsent) {
        this.cookieConsent = cookieConsent;
        if (cookieConsent) {
            this.cookieConsentStatus = CookieConsentStatus.ACCEPTED;
        } else if (this.cookieConsentStatus == null) {
            this.cookieConsentStatus = CookieConsentStatus.UNDECIDED;
        }
    }
    public CookieConsentStatus getCookieConsentStatus() {
        if (cookieConsentStatus != null) {
            return cookieConsentStatus;
        }
        return cookieConsent ? CookieConsentStatus.ACCEPTED : CookieConsentStatus.UNDECIDED;
    }
    public void setCookieConsentStatus(CookieConsentStatus cookieConsentStatus) {
        this.cookieConsentStatus = cookieConsentStatus == null ? CookieConsentStatus.UNDECIDED : cookieConsentStatus;
        this.cookieConsent = this.cookieConsentStatus == CookieConsentStatus.ACCEPTED;
    }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }
    public boolean isFavoriteDigestEnabled() { return favoriteDigestEnabled; }
    public void setFavoriteDigestEnabled(boolean favoriteDigestEnabled) { this.favoriteDigestEnabled = favoriteDigestEnabled; }
    public boolean isFavoriteDigestEmailEnabled() { return favoriteDigestEmailEnabled; }
    public void setFavoriteDigestEmailEnabled(boolean favoriteDigestEmailEnabled) { this.favoriteDigestEmailEnabled = favoriteDigestEmailEnabled; }
    public boolean isPrivateProfile() { return privateProfile; }
    public void setPrivateProfile(boolean privateProfile) { this.privateProfile = privateProfile; }
    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }
    public Instant getPasswordResetExpiresAt() { return passwordResetExpiresAt; }
    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) { this.passwordResetExpiresAt = passwordResetExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
