/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 05-05-2026
 * @description Gestiona estrellas de favoritos compartidas en pilotos, escuderias, sesiones y GP
 */
class RaceStreamFavorites {

    static favorites = new Map();
    static authenticated = null;
    static loaded = false;

    static key(type, externalId) {
        return `${type || ''}`.toLowerCase().trim() + '|' + `${externalId || ''}`.toLowerCase().trim();
    }

    static button({ type, externalId, title, url, description }) {
        return `
            <button class="rs-favorite-button" type="button"
                    aria-label="Guardar en favoritos"
                    data-favorite-type="${RaceStreamFavorites.escape(type)}"
                    data-favorite-id="${RaceStreamFavorites.escape(externalId)}"
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
            rows.forEach((item) => this.favorites.set(this.key(item.type, item.externalId), item));
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
        const key = this.key(button.dataset.favoriteType, button.dataset.favoriteId);
        const current = this.favorites.get(key);
        if (current?.id) {
            await fetch(`/api/favorites/${current.id}`, { method: 'DELETE' });
            this.favorites.delete(key);
        } else {
            const created = await fetch('/api/favorites', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    type: button.dataset.favoriteType,
                    externalId: button.dataset.favoriteId,
                    title: button.dataset.favoriteTitle,
                    url: button.dataset.favoriteUrl,
                    description: button.dataset.favoriteDescription
                })
            }).then((response) => response.json());
            this.favorites.set(key, created);
        }
        document.querySelectorAll(
            `.rs-favorite-button[data-favorite-type="${CSS.escape(button.dataset.favoriteType)}"][data-favorite-id="${CSS.escape(button.dataset.favoriteId)}"]`
        ).forEach((item) => this.paint(item));
    }

    static paint(button) {
        const saved = this.favorites.has(this.key(button.dataset.favoriteType, button.dataset.favoriteId));
        button.classList.toggle('rs-favorite-button--active', saved);
        button.textContent = saved ? '★' : '☆';
        button.setAttribute('aria-label', saved ? 'Quitar de favoritos' : 'Guardar en favoritos');
        button.setAttribute('aria-pressed', String(saved));
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
