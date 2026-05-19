/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 14-05-2026
 * @description Gestiona favoritos compartidos y activa notificaciones de sesiones al guardar un GP
 */
class RaceStreamFavorites {

    static favorites = new Map();
    static authenticated = null;
    static loaded = false;
    static SESSION_NOTIFICATIONS_KEY = 'rs-session-notifications';

    static key(type, externalId, seasonYear = '') {
        return `${type || ''}`.toLowerCase().trim()
            + '|' + `${externalId || ''}`.toLowerCase().trim()
            + '|' + `${seasonYear || ''}`.trim();
    }

    static button({ type, externalId, seasonYear = '', title, url, description }) {
        return `
            <button class="rs-favorite-button" type="button"
                    aria-label="Guardar en favoritos"
                    data-favorite-type="${RaceStreamFavorites.escape(type)}"
                    data-favorite-id="${RaceStreamFavorites.escape(externalId)}"
                    data-favorite-season-year="${RaceStreamFavorites.escape(seasonYear)}"
                    data-favorite-title="${RaceStreamFavorites.escape(title)}"
                    data-favorite-url="${RaceStreamFavorites.escape(url)}"
                    data-favorite-description="${RaceStreamFavorites.escape(description)}">☆</button>
        `;
    }

    static async bind(root = document) {
        await this.load();
        root.querySelectorAll('.rs-favorite-button').forEach((button) => {
            this.paint(button);
            if (button.dataset.favoriteBound) return;
            button.dataset.favoriteBound = 'true';
            button.addEventListener('click', (event) => this.toggle(event, button));
        });
    }

    static async load() {
        if (this.loaded) return;
        this.loaded = true;
        try {
            const session = await fetch('/api/auth/me', { cache: 'no-store' }).then((response) => response.json());
            this.authenticated = Boolean(session.authenticated);
            if (!this.authenticated) return;
            const rows = await fetch('/api/favorites', { cache: 'no-store' }).then((response) => response.json());
            rows.forEach((item) => this.favorites.set(this.key(item.type, item.externalId, item.seasonYear), item));
        } catch {
            this.authenticated = false;
        }
    }

    static async toggle(event, button) {
        event.preventDefault();
        event.stopPropagation();
        await this.load();
        if (!this.authenticated) {
            window.location.href = `/login.html?redirect=${encodeURIComponent(window.location.href)}`;
            return;
        }
        const key = this.key(button.dataset.favoriteType, button.dataset.favoriteId, button.dataset.favoriteSeasonYear);
        const current = this.favorites.get(key);
        if (current?.id) {
            await fetch(`/api/favorites/${current.id}`, { method: 'DELETE' });
            this.favorites.delete(key);
            if (this.isGpFavorite(current)) {
                this.clearGpSessionNotifications(this.getMeetingKey(current, button));
            }
        } else {
            const created = await fetch('/api/favorites', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    type: button.dataset.favoriteType,
                    externalId: button.dataset.favoriteId,
                    seasonYear: button.dataset.favoriteSeasonYear ? Number(button.dataset.favoriteSeasonYear) : null,
                    title: button.dataset.favoriteTitle,
                    url: button.dataset.favoriteUrl,
                    description: button.dataset.favoriteDescription
                })
            }).then((response) => response.json());
            this.favorites.set(key, created);
            if (this.isGpFavorite(created)) {
                this.enableGpSessionNotifications(created).catch(() => {});
            }
        }
        document.querySelectorAll(
            `.rs-favorite-button[data-favorite-type="${CSS.escape(button.dataset.favoriteType)}"][data-favorite-id="${CSS.escape(button.dataset.favoriteId)}"][data-favorite-season-year="${CSS.escape(button.dataset.favoriteSeasonYear || '')}"]`
        ).forEach((item) => this.paint(item));
    }

    static paint(button) {
        const saved = this.favorites.has(this.key(button.dataset.favoriteType, button.dataset.favoriteId, button.dataset.favoriteSeasonYear));
        button.classList.toggle('rs-favorite-button--active', saved);
        button.textContent = saved ? '★' : '☆';
        button.setAttribute('aria-label', saved ? 'Quitar de favoritos' : 'Guardar en favoritos');
        button.setAttribute('aria-pressed', String(saved));
    }

    static isGpFavorite(item) {
        return `${item?.type || ''}`.toLowerCase() === 'gp';
    }

    static getMeetingKey(item, button = null) {
        const url = item?.url || button?.dataset.favoriteUrl || '';
        const params = new URLSearchParams((url.split('?')[1] || '').split('#')[0]);
        return params.get('meetingKey') || item?.externalId || button?.dataset.favoriteId || '';
    }

    static getNotificationKey(meetingKey, session) {
        const id = session?.session_key || `${meetingKey}-${session?.session_name || ''}-${session?.date_start || ''}`;
        return `${meetingKey}|${id}`;
    }

    static isSessionNotifiable(session) {
        if (session?.is_cancelled) return false;
        const end = new Date(session?.date_end).getTime();
        return !Number.isFinite(end) || Date.now() <= end;
    }

    static readSessionNotifications() {
        try {
            return JSON.parse(localStorage.getItem(this.SESSION_NOTIFICATIONS_KEY) || '{}');
        } catch {
            return {};
        }
    }

    static saveSessionNotifications(state) {
        localStorage.setItem(this.SESSION_NOTIFICATIONS_KEY, JSON.stringify(state));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Activa por defecto las notificaciones de sesiones al guardar un GP favorito
     * @param {Object} favorite GP guardado como favorito
     */
    static async enableGpSessionNotifications(favorite) {
        const meetingKey = this.getMeetingKey(favorite);
        if (!meetingKey) return;
        const sessions = await fetch(`/api/f1/schedule/meetings/${encodeURIComponent(meetingKey)}/sessions`, { cache: 'no-store' })
            .then((response) => response.ok ? response.json() : [])
            .catch(() => []);
        const notifiable = (Array.isArray(sessions) ? sessions : []).filter(this.isSessionNotifiable);
        if (!notifiable.length) return;
        const state = this.readSessionNotifications();
        let changed = false;
        notifiable.forEach((session) => {
            const notificationKey = this.getNotificationKey(meetingKey, session);
            const item = {
                enabled: true,
                title: `${session.session_name || 'Sesión'} · ${favorite.title || 'GP favorito'}`,
                dateStart: session.date_start || ''
            };
            if (state[notificationKey] === true) {
                state[notificationKey] = item;
                changed = true;
                return;
            }
            if (state[notificationKey] !== undefined) return;
            state[notificationKey] = item;
            changed = true;
        });
        if (changed) this.saveSessionNotifications(state);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Limpia notificaciones locales de un GP cuando deja de ser favorito
     * @param {string} meetingKey Identificador del GP
     */
    static clearGpSessionNotifications(meetingKey) {
        if (!meetingKey) return;
        const state = this.readSessionNotifications();
        const prefix = `${meetingKey}|`;
        let changed = false;
        Object.keys(state).forEach((key) => {
            if (!key.startsWith(prefix)) return;
            delete state[key];
            changed = true;
        });
        if (changed) this.saveSessionNotifications(state);
    }

    static escape(value) {
        return `${value ?? ''}`
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
}

window.RaceStreamFavorites = RaceStreamFavorites;
document.addEventListener('DOMContentLoaded', () => RaceStreamFavorites.bind());
