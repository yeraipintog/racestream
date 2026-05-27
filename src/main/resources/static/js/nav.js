/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.6.6
 * @created 30-04-2026
 * @modified 26-05-2026
 * @description Pinta navbar, footer, estructura común, cookies, logout seguro,
 *              aviso admin, race strip y centro persistente de notificaciones
 */
/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 26-05-2026
 * @modified 26-05-2026
 * @description Helper global para adjuntar el token CSRF emitido por Spring
 *              Security en peticiones JSON mutables
 */
window.RaceStreamCsrf = window.RaceStreamCsrf || {
    token() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    },
    headers(headers = {}) {
        const token = this.token();
        return token ? { ...headers, 'X-XSRF-TOKEN': token } : { ...headers };
    }
};

class RaceStreamSharedLayout {

    constructor() {
        this.renderLayout();
        this.navbar = document.getElementById('mainNavbar');
        this.raceStrip = document.getElementById('raceStrip');
        this.profileMenu = document.querySelector('.rs-profile-dropdown__menu');
        this.notificationDropdown = document.getElementById('notificationDropdown');
        this.notificationList = document.getElementById('notificationList');
        this.notificationBadge = document.getElementById('notificationBadge');
        this.notificationStoreBaseKey = 'rs-active-notifications';
        this.notificationHiddenBaseKey = 'rs-hidden-notification-toasts';
        this.notificationStoreKey = `${this.notificationStoreBaseKey}:guest`;
        this.notificationHiddenKey = `${this.notificationHiddenBaseKey}:guest`;
        this.sessionNotifiedKey = 'rs-session-notified:guest';
        this.notificationsAuthenticated = false;
        this.path = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
        this.lastScrollY = Math.max(window.scrollY, 0);
        this.scrollTicking = false;
        this.bindNavigation();
        this.bindDropdowns();
        this.bindLoginHashLinks();
        this.bindNavbarVisibility();
        this.updateNavbarOffset();
        this.observeHeaderHeights();
        this.renderNotificationCenter();
        this.syncAccountMenu();
        this.renderCookieBanner();
        this.syncCookiePage();
        window.addEventListener('load', () => this.updateNavbarOffset());
        window.addEventListener('resize', () => this.updateNavbarOffset());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 06-05-2026
     * @description Inserta la estructura común reutilizable de todas las páginas
     */
    renderLayout() {
        this.replaceLayoutNode('#mainNavbar', () => this.renderNavbar());
        this.replaceLayoutNode('#raceStrip', () => this.renderRaceStripShell());
        this.replaceLayoutNode('.rs-footer', () => this.renderFooter());
    }

    renderNavbar() {
        return `
            <header class="rs-navbar" id="mainNavbar">
                <div class="rs-container rs-navbar__inner">
                    <a class="rs-navbar__brand" href="/" aria-label="Ir a Inicio">
                        <img class="rs-navbar__logo" src="/assets/img/LogoRS2.png" alt="Logotipo de RaceStream">
                    </a>
                    <nav class="rs-navbar__nav" aria-label="Navegación principal">
                        <a class="rs-navbar__link" href="/live.html">En Vivo</a>
                        <a class="rs-navbar__link" href="/calendar.html">Calendario</a>
                        <a class="rs-navbar__link" href="/sessions.html">Sesiones</a>
                        <a class="rs-navbar__link" href="/standings.html">Clasificación</a>
                        <a class="rs-navbar__link rs-navbar__link--overflow" href="/drivers.html">Pilotos</a>
                        <a class="rs-navbar__link rs-navbar__link--overflow" href="/teams.html">Escuderías</a>
                        <a class="rs-navbar__link rs-navbar__link--overflow" href="/news.html">Noticias</a>
                    </nav>
                    <div class="rs-navbar__actions">
                        <a class="rs-navbar__icon-link" href="/favorites.html" title="Favoritos" aria-label="Favoritos">&#9733;</a>
                        <div class="rs-notification-dropdown" id="notificationDropdown">
                            <button class="rs-navbar__icon-link rs-notification-dropdown__trigger rs-profile-dropdown__trigger" type="button" title="Notificaciones" aria-label="Notificaciones" aria-expanded="false">
                                🕭
                                <span class="rs-notification-dropdown__badge" id="notificationBadge" hidden>0</span>
                            </button>
                            <div class="rs-notification-dropdown__panel" id="notificationPanel">
                                <div class="rs-notification-dropdown__head">
                                    <strong>Notificaciones</strong>
                                </div>
                                <div class="rs-notification-dropdown__list" id="notificationList"></div>
                            </div>
                        </div>
                        <div class="rs-profile-dropdown" id="profileDropdown">
                            <button class="rs-profile-dropdown__trigger" type="button" aria-label="Mi cuenta">
                                <img src="/assets/img/MiCuenta.png" alt="Mi cuenta">
                            </button>
                            <div class="rs-profile-dropdown__menu">
                                <a class="rs-button rs-button--primary rs-profile-dropdown__button" href="/login.html">Iniciar sesión</a>
                                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/login.html#registro">Registrarse</a>
                            </div>
                        </div>
                        <div class="rs-navbar-mobile-menu" id="mobileMenuDropdown">
                            <button class="rs-navbar__menu-trigger" type="button" aria-label="Abrir menú">
                                <img src="/assets/img/MenuHamburguesa.png" alt="Menú">
                            </button>
                            <div class="rs-navbar-mobile-menu__panel">
                                <a class="rs-navbar-mobile-menu__overflow-link" href="/drivers.html">Pilotos</a>
                                <a class="rs-navbar-mobile-menu__overflow-link" href="/teams.html">Escuderías</a>
                                <a class="rs-navbar-mobile-menu__overflow-link" href="/news.html">Noticias</a>
                                <a class="rs-navbar-mobile-menu__desktop-link" href="/forum.html">Foro</a>
                                <a href="/help.html">Ayuda</a>
                                <a class="rs-navbar-mobile-menu__desktop-link" href="/faq.html">FAQ</a>
                                <a class="rs-navbar-mobile-menu__desktop-link" href="/contact.html">Contacto</a>
                            </div>
                        </div>
                    </div>
                </div>
            </header>
        `;
    }

    renderRaceStripShell() {
        return `
            <section class="rs-race-strip" id="raceStrip">
                <div class="rs-container rs-race-strip__inner">
                    <div class="rs-race-strip__label">
                        <span>Próximo GP</span>
                        <span id="raceStripMeta" class="rs-race-strip__meta">Actualizando datos</span>
                    </div>
                    <div class="rs-race-strip__main">
                        <div class="rs-race-strip__title-row">
                            <img id="raceStripFlag" class="rs-flag-inline rs-flag-inline--medium" src="" alt="Bandera del país" style="display:none;">
                            <span id="raceStripTitle" class="rs-race-strip__title">Pr&oacute;ximo GP</span>
                        </div>
                    </div>
                    <div id="raceStripAction" class="rs-race-strip__action"></div>
                    <div id="raceStripClocks" class="rs-race-strip__clocks">-</div>
                </div>
            </section>
        `;
    }

    renderFooter() {
        return `
            <footer class="rs-footer">
                <div class="rs-container">
                    <div class="rs-footer__inner">
                        <div class="rs-footer__brand">
                            <img class="rs-footer__logo" src="/assets/img/LogoRS2.png" alt="Logotipo de RaceStream">
                            <h3 class="rs-footer__title">RaceStream</h3>
                        </div>
                        <div class="rs-footer__column">
                            <h3>Soporte</h3>
                            <div class="rs-footer__links">
                                <a href="/forum.html">Foro</a>
                                <a href="/help.html">Ayuda</a>
                                <a href="/faq.html">FAQ</a>
                                <a href="/contact.html">Contacto</a>
                            </div>
                        </div>
                        <div class="rs-footer__column">
                            <h3>Cuenta</h3>
                            <div class="rs-footer__links">
                                <a href="/account.html">Mi Cuenta</a>
                                <a href="/favorites.html">Favoritos</a>
                                <a href="/preferences.html">Preferencias</a>
                                <a href="/privacy.html">Privacidad</a>
                            </div>
                        </div>
                    </div>
                    <div class="rs-footer__bottom">
                        <span>&copy; 2026 RaceStream. Todos los derechos reservados.</span>
                        <div class="rs-footer__bottom-links">
                            <a href="/terms.html">Términos de servicio</a>
                            <a href="/privacy-policy.html">Política de privacidad</a>
                            <a href="/cookies.html">Política de cookies</a>
                        </div>
                    </div>
                </div>
            </footer>
        `;
    }

    replaceLayoutNode(selector, html) {
        const node = document.querySelector(selector);
        if (node) node.outerHTML = html();
    }

    bindNavigation() {
        document.querySelectorAll('.rs-navbar__link').forEach((link) => {
            const href = link.getAttribute('href');
            const normalizedHref = href === '/' ? '/index.html' : href;
            const liveGroup = normalizedHref === '/live.html' && this.path.startsWith('/live');
            link.classList.toggle('rs-navbar__link--active', normalizedHref === this.path || liveGroup);
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 04-05-2026
     * @modified 22-05-2026
     * @description Activa menús del navbar común y notificaciones evitando eventos duplicados
     */
    bindDropdowns() {
        const profileDropdown = document.getElementById('profileDropdown');
        const mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
        const notificationDropdown = document.getElementById('notificationDropdown');
        if (profileDropdown && !profileDropdown.dataset.rsDropdownBound) {
            profileDropdown.dataset.rsDropdownBound = 'true';
            profileDropdown.querySelector('.rs-profile-dropdown__trigger')?.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                profileDropdown.classList.toggle('rs-profile-dropdown--open');
                mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
                notificationDropdown?.classList.remove('rs-notification-dropdown--open');
            });
            profileDropdown.querySelector('.rs-profile-dropdown__menu')?.addEventListener('click', (event) => event.stopPropagation());
        }
        if (notificationDropdown && !notificationDropdown.dataset.rsDropdownBound) {
            notificationDropdown.dataset.rsDropdownBound = 'true';
            const trigger = notificationDropdown.querySelector('.rs-notification-dropdown__trigger');
            trigger?.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                const open = notificationDropdown.classList.toggle('rs-notification-dropdown--open');
                trigger.setAttribute('aria-expanded', `${open}`);
                profileDropdown?.classList.remove('rs-profile-dropdown--open');
                mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
            });
            notificationDropdown.querySelector('.rs-notification-dropdown__panel')?.addEventListener('click', (event) => event.stopPropagation());
        }
        if (mobileMenuDropdown && !mobileMenuDropdown.dataset.rsDropdownBound) {
            mobileMenuDropdown.dataset.rsDropdownBound = 'true';
            mobileMenuDropdown.querySelector('.rs-navbar__menu-trigger')?.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                mobileMenuDropdown.classList.toggle('rs-navbar-mobile-menu--open');
                profileDropdown?.classList.remove('rs-profile-dropdown--open');
                notificationDropdown?.classList.remove('rs-notification-dropdown--open');
            });
            mobileMenuDropdown.querySelector('.rs-navbar-mobile-menu__panel')?.addEventListener('click', (event) => event.stopPropagation());
        }
        if (!document.body.dataset.rsDropdownCloseBound) {
            document.body.dataset.rsDropdownCloseBound = 'true';
            document.addEventListener('click', () => {
                this.closeDropdowns();
            });
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Cambia a registro desde login sin recargar y cerrando el desplegable de perfil
     */
    bindLoginHashLinks() {
        document.querySelectorAll('a[href*="/login.html#"], a[href^="login.html#"]').forEach((link) => {
            if (link.dataset.rsLoginHashBound) return;
            link.dataset.rsLoginHashBound = 'true';
            link.addEventListener('click', (event) => {
                const url = new URL(link.getAttribute('href'), window.location.origin);
                if (!window.location.pathname.endsWith('/login.html') || !url.hash) return;
                event.preventDefault();
                this.closeDropdowns();
                if (window.location.hash === url.hash) {
                    window.dispatchEvent(new Event('hashchange'));
                } else {
                    window.location.hash = url.hash;
                }
            });
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Oculta solo la franja de próximo GP al bajar y la recupera al subir sin mover contenido
     */
    bindNavbarVisibility() {
        if (document.body.dataset.rsNavbarScrollBound) return;
        document.body.dataset.rsNavbarScrollBound = 'true';
        window.addEventListener('scroll', () => {
            if (this.scrollTicking) return;
            this.scrollTicking = true;
            window.requestAnimationFrame(() => {
                this.handleNavbarVisibility();
                this.scrollTicking = false;
            });
        }, { passive: true });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Sincroniza la clase global de cabecera oculta según dirección de scroll
     */
    handleNavbarVisibility() {
        const currentScrollY = Math.max(window.scrollY, 0);
        const delta = currentScrollY - this.lastScrollY;
        if (currentScrollY <= 12 || delta < -6) {
            document.body.classList.remove('rs-race-strip-hidden');
        } else if (delta > 6 && currentScrollY > 120) {
            document.body.classList.add('rs-race-strip-hidden');
            this.closeDropdowns();
        }
        this.lastScrollY = currentScrollY;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Cierra desplegables de navegación cuando cambia el contexto visual
     */
    closeDropdowns() {
        document.getElementById('profileDropdown')?.classList.remove('rs-profile-dropdown--open');
        document.getElementById('mobileMenuDropdown')?.classList.remove('rs-navbar-mobile-menu--open');
        document.getElementById('notificationDropdown')?.classList.remove('rs-notification-dropdown--open');
        document.querySelector('.rs-notification-dropdown__trigger')?.setAttribute('aria-expanded', 'false');
    }

    updateNavbarOffset() {
        const adminLineHeight = document.querySelector('.rs-session-line')?.offsetHeight || 0;
        const navbarHeight = this.navbar?.offsetHeight || 74;
        const raceStripHeight = this.raceStrip?.offsetHeight || 58;
        document.documentElement.style.setProperty('--rs-admin-line-height', `${adminLineHeight}px`);
        document.documentElement.style.setProperty('--rs-navbar-height', `${navbarHeight}px`);
        document.documentElement.style.setProperty('--rs-race-strip-height', `${raceStripHeight}px`);
        document.documentElement.style.setProperty('--rs-header-offset', `${adminLineHeight + navbarHeight + raceStripHeight}px`);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Observa cambios de altura del navbar y la franja de GP para no tapar contenido
     */
    observeHeaderHeights() {
        if (!('ResizeObserver' in window)) {
            return;
        }
        const observer = new ResizeObserver(() => this.updateNavbarOffset());
        if (this.navbar) observer.observe(this.navbar);
        if (this.raceStrip) observer.observe(this.raceStrip);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 05-05-2026
     * @modified 18-05-2026
     * @description Sincroniza el menu de cuenta y restaura la decisión de cookies guardada en BBDD
     */
    async syncAccountMenu() {
        if (!this.profileMenu) return;
        try {
            const cacheKey = 'rs-auth-me';
            sessionStorage.removeItem(cacheKey);
            const user = await fetch('/api/auth/me', {
                cache: 'no-store',
                headers: { 'Cache-Control': 'no-cache' }
            }).then((response) => response.json());
            const localCookieStatus = this.readCookieConsentStatus();
            const remoteCookieStatus = this.normalizeCookieConsentStatus(user.cookieConsentStatus || (user.cookieConsent ? 'ACCEPTED' : 'UNDECIDED'));
            if (!localCookieStatus && (remoteCookieStatus === 'accepted' || remoteCookieStatus === 'rejected')) {
                this.writeCookieConsent(remoteCookieStatus);
                document.querySelector('.rs-cookie-banner')?.remove();
            }
            this.renderSessionLine(user);
            this.setNotificationContext(user);
            this.notificationsAuthenticated = Boolean(user.authenticated);
            this.renderNotificationCenter();
            if (!user.authenticated) {
                this.clearToastStack();
                return;
            }
            this.profileMenu.innerHTML = `
                <a class="rs-button rs-button--primary rs-profile-dropdown__button" href="/account.html">Mi Cuenta</a>
                ${user.role === 'ADMIN' ? '<a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/admin.html">Admin</a>' : ''}
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/preferences.html">Preferencias</a>
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/privacy.html">Privacidad</a>
                <button class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" type="button" data-logout>Cerrar sesión</button>
            `;
            this.restoreActiveNotifications();
            await this.syncServerNotifications();
            this.syncWebNotifications(user);
            this.profileMenu.querySelector('[data-logout]')?.addEventListener('click', async (event) => {
                const button = event.currentTarget;
                button.disabled = true;
                try {
                    await fetch('/api/auth/logout', {
                        method: 'POST',
                        cache: 'no-store',
                        headers: window.RaceStreamCsrf.headers({ 'Cache-Control': 'no-cache' }),
                        credentials: 'same-origin',
                        redirect: 'manual'
                    });
                } catch {
                    /* La redirección final confirma el cierre incluso si la respuesta no llega. */
                } finally {
                    this.clearToastStack();
                    sessionStorage.removeItem(cacheKey);
                    window.location.replace(`/login.html?logout=${Date.now()}`);
                }
            });
        } catch {
            this.notificationsAuthenticated = false;
            this.setNotificationContext(null);
            this.clearToastStack();
            this.renderNotificationCenter();
            /* La navegación pública no debe bloquearse si la sesión no responde. */
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Muestra una línea fija para sesiones públicas o ADMIN sin contaminar las páginas internas
     * @param {Object} user Usuario autenticado
     */
    renderSessionLine(user) {
        const existing = document.querySelector('.rs-session-line');
        const isAdmin = user?.authenticated && user?.role === 'ADMIN';
        const isPublic = !user?.authenticated;
        if (!isAdmin && !isPublic) {
            existing?.remove();
            document.body.classList.remove('rs-session-line-active', 'rs-session-line-admin', 'rs-session-line-public');
            this.updateNavbarOffset();
            return;
        }
        const text = isAdmin
            ? 'Sesión iniciada como <strong>ADMIN</strong>. Algunas acciones están restringidas.'
            : 'Regístrate <strong>GRATIS</strong> para obtener todas las ventajas.';
        const className = `rs-session-line ${isAdmin ? 'rs-session-line--admin' : 'rs-session-line--public'}`;
        if (existing) {
            existing.className = className;
            existing.innerHTML = text;
        } else {
            const line = document.createElement('div');
            line.className = className;
            line.innerHTML = text;
            document.body.prepend(line);
        }
        document.body.classList.toggle('rs-session-line-admin', isAdmin);
        document.body.classList.toggle('rs-session-line-public', isPublic);
        document.body.classList.add('rs-session-line-active');
        this.updateNavbarOffset();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Muestra avisos de sesiones guardadas dentro de la web y, si el navegador lo permite, como notificación
     * @param {Object} user Usuario autenticado con preferencias
     */
    syncWebNotifications(user) {
        if (!user?.authenticated || !user?.notificationsEnabled) return;
        const state = this.readJson('rs-session-notifications', {});
        const notified = this.readJson(this.sessionNotifiedKey, {});
        const now = Date.now();
        Object.entries(state).forEach(([key, item]) => {
            if (!item || item.enabled === false || item === false) return;
            const dateStart = typeof item === 'object' ? item.dateStart : '';
            const startsAt = new Date(dateStart).getTime();
            if (!Number.isFinite(startsAt) || startsAt < now || startsAt - now > 1800000 || notified[key]) return;
            const title = typeof item === 'object' ? item.title : 'Sesión favorita';
            const message = `La sesión ${title} empieza pronto.`;
            notified[key] = true;
            this.upsertNotification({
                id: `local:${key}`,
                title: 'Próxima sesión',
                message,
                type: 'SESSION_REMINDER',
                createdAt: new Date().toISOString()
            });
            if ('Notification' in window && Notification.permission === 'granted') {
                new Notification('RaceStream', { body: message });
            }
        });
        localStorage.setItem(this.sessionNotifiedKey, JSON.stringify(notified));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 22-05-2026
     * @description Muestra notificaciones persistidas por el backend sin marcarlas como leídas hasta el cierre manual
     */
    async syncServerNotifications() {
        try {
            const rows = await fetch('/api/user/notifications', {
                cache: 'no-store',
                headers: { 'Cache-Control': 'no-cache' }
            }).then((response) => response.ok ? response.json() : []);
            (Array.isArray(rows) ? rows : []).forEach((item) => {
                if (!item?.message || !item?.id) return;
                this.upsertNotification({
                    id: `server:${item.id}`,
                    serverId: item.id,
                    title: item.title || 'Notificación',
                    message: item.message,
                    type: item.type || 'INFO',
                    createdAt: item.createdAt || new Date().toISOString()
                });
            });
        } catch {
            /* Las notificaciones no deben bloquear la navegación. */
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @description Lee JSON local con fallback seguro
     * @param {string} key Clave de almacenamiento
     * @param {*} fallback Valor por defecto
     * @returns {*} Valor parseado
     */
    readJson(key, fallback) {
        try {
            return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
        } catch {
            return fallback;
        }
    }

    readStoredNotifications() {
        if (!this.notificationsAuthenticated) {
            return [];
        }
        const rows = this.readJson(this.notificationStoreKey, []);
        return this.sortNotifications((Array.isArray(rows) ? rows : [])
            .filter((item) => item?.id && item?.message)
        ).slice(0, 20);
    }

    writeStoredNotifications(rows) {
        if (!this.notificationsAuthenticated) {
            return;
        }
        localStorage.setItem(this.notificationStoreKey, JSON.stringify(this.sortNotifications(rows).slice(0, 20)));
    }

    upsertNotification(notification) {
        if (!this.notificationsAuthenticated) {
            return;
        }
        const rows = this.readStoredNotifications();
        const index = rows.findIndex((item) => item.id === notification.id);
        const isNew = index < 0;
        if (index >= 0) {
            rows[index] = { ...rows[index], ...notification };
        } else {
            rows.unshift(notification);
        }
        this.writeStoredNotifications(rows);
        this.renderNotificationCenter();
        if (isNew) {
            this.showPersistentNotification(notification);
        }
    }

    restoreActiveNotifications() {
        this.clearToastStack();
    }

    showPersistentNotification(notification) {
        if (!this.notificationsAuthenticated) {
            return;
        }
        if (!notification?.id
                || this.isToastHidden(notification.id)
                || document.querySelector(`[data-rs-toast-id="${this.cssEscape(notification.id)}"]`)) return;
        const toast = document.createElement('div');
        toast.className = 'rs-web-toast';
        toast.dataset.rsToastId = notification.id;
        toast.innerHTML = `
            <button class="rs-web-toast__close" type="button" aria-label="Cerrar notificación">&times;</button>
            <strong>${this.escapeHtml(notification.title || 'Notificación')}</strong>
            <span>${this.escapeHtml(notification.message)}</span>
        `;
        toast.querySelector('.rs-web-toast__close')?.addEventListener('click', () => this.closeToastOnly(notification.id));
        this.getToastStack().appendChild(toast);
    }

    getToastStack() {
        let stack = document.querySelector('.rs-web-toast-stack');
        if (!stack) {
            stack = document.createElement('div');
            stack.className = 'rs-web-toast-stack';
            document.body.appendChild(stack);
        }
        return stack;
    }

    renderNotificationCenter() {
        if (!this.notificationList || !this.notificationBadge) return;
        if (!this.notificationsAuthenticated) {
            this.notificationBadge.hidden = true;
            this.notificationBadge.textContent = '0';
            this.notificationList.innerHTML = '<p class="rs-notification-dropdown__empty">Inicia sesión para ver tus notificaciones.</p>';
            return;
        }
        const rows = this.readStoredNotifications();
        this.notificationBadge.hidden = rows.length === 0;
        this.notificationBadge.textContent = `${Math.min(rows.length, 99)}`;
        this.notificationList.innerHTML = rows.length
            ? rows.map((item) => `
                <article class="rs-notification-dropdown__item">
                    <button class="rs-notification-dropdown__close" type="button" data-notification-close="${this.escapeHtml(item.id)}" aria-label="Cerrar notificación">&times;</button>
                    <strong>${this.escapeHtml(item.title || 'Notificación')}</strong>
                    <p>${this.escapeHtml(item.message)}</p>
                    <time>${this.formatNotificationTime(item.createdAt)}</time>
                </article>
            `).join('')
            : '<p class="rs-notification-dropdown__empty">Sin notificaciones pendientes.</p>';
        this.notificationList.querySelectorAll('[data-notification-close]').forEach((button) => {
            button.addEventListener('click', () => this.dismissNotification(button.dataset.notificationClose));
        });
    }

    async dismissNotification(id) {
        if (!this.notificationsAuthenticated) return;
        const rows = this.readStoredNotifications();
        const notification = rows.find((item) => item.id === id);
        this.writeStoredNotifications(rows.filter((item) => item.id !== id));
        this.unhideToast(id);
        document.querySelector(`[data-rs-toast-id="${this.cssEscape(id)}"]`)?.remove();
        this.renderNotificationCenter();
        if (notification?.serverId) {
            await this.markNotificationsRead([notification.serverId]);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Cierra solo el aviso flotante sin eliminar la notificación del centro
     * @param {string} id Identificador local de notificación
     */
    closeToastOnly(id) {
        this.hideToast(id);
        document.querySelector(`[data-rs-toast-id="${this.cssEscape(id)}"]`)?.remove();
    }

    setNotificationContext(user) {
        if (!user?.authenticated) {
            this.notificationStoreKey = `${this.notificationStoreBaseKey}:guest`;
            this.notificationHiddenKey = `${this.notificationHiddenBaseKey}:guest`;
            this.sessionNotifiedKey = 'rs-session-notified:guest';
            return;
        }
        const identity = this.storageIdentity(user);
        this.notificationStoreKey = `${this.notificationStoreBaseKey}:${identity}`;
        this.notificationHiddenKey = `${this.notificationHiddenBaseKey}:${identity}`;
        this.sessionNotifiedKey = `rs-session-notified:${identity}`;
    }

    hiddenToasts() {
        const rows = this.readJson(this.notificationHiddenKey, []);
        return Array.isArray(rows) ? rows.filter(Boolean) : [];
    }

    isToastHidden(id) {
        return this.hiddenToasts().includes(id);
    }

    hideToast(id) {
        if (!id || !this.notificationsAuthenticated) return;
        const rows = new Set(this.hiddenToasts());
        rows.add(id);
        localStorage.setItem(this.notificationHiddenKey, JSON.stringify([...rows].slice(-80)));
    }

    unhideToast(id) {
        if (!id || !this.notificationsAuthenticated) return;
        const rows = this.hiddenToasts().filter((item) => item !== id);
        localStorage.setItem(this.notificationHiddenKey, JSON.stringify(rows));
    }

    storageIdentity(user) {
        const raw = `${user?.email || user?.name || user?.id || 'usuario'}`.trim().toLowerCase();
        const normalized = raw.normalize ? raw.normalize('NFD').replace(/[\u0300-\u036f]/g, '') : raw;
        return normalized.replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '') || 'usuario';
    }

    clearToastStack() {
        document.querySelector('.rs-web-toast-stack')?.remove();
    }

    sortNotifications(rows) {
        return [...rows].sort((left, right) => {
            const leftTime = new Date(left?.createdAt || 0).getTime();
            const rightTime = new Date(right?.createdAt || 0).getTime();
            return (Number.isFinite(rightTime) ? rightTime : 0) - (Number.isFinite(leftTime) ? leftTime : 0);
        });
    }

    async markNotificationsRead(ids) {
        if (!ids?.length) return;
        await fetch('/api/user/notifications/read', {
            method: 'POST',
            cache: 'no-store',
            headers: window.RaceStreamCsrf.headers({ 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' }),
            body: JSON.stringify({ ids })
        }).catch(() => {});
    }

    formatNotificationTime(value) {
        const date = new Date(value);
        return Number.isFinite(date.getTime())
            ? date.toLocaleString('es-ES', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
            : '';
    }

    escapeHtml(value) {
        return `${value ?? ''}`.replace(/[&<>"']/g, (char) => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        })[char]);
    }

    cssEscape(value) {
        return window.CSS && typeof CSS.escape === 'function'
            ? CSS.escape(`${value}`)
            : `${value}`.replace(/["\\]/g, '\\$&');
    }

    renderCookieBanner() {
        if (this.readCookieConsentStatus()) return;
        const banner = document.createElement('div');
        banner.className = 'rs-cookie-banner';
        banner.innerHTML = `
            <p>Usamos cookies técnicas para mantener tu sesión y recordar preferencias.</p>
            <div>
                <button class="rs-button rs-button--primary" type="button" data-cookie-accept>Aceptar</button>
                <button class="rs-button" type="button" data-cookie-reject>Rechazar</button>
                <a class="rs-link-chip" href="/cookies.html">Política</a>
            </div>
        `;
        document.body.appendChild(banner);
        const save = async (status) => {
            await fetch('/api/auth/cookies', {
                method: 'POST',
                headers: window.RaceStreamCsrf.headers({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ accepted: status === 'accepted' })
            }).catch(() => {});
            this.writeCookieConsent(status);
            banner.remove();
        };
        banner.querySelector('[data-cookie-accept]').addEventListener('click', () => save('accepted'));
        banner.querySelector('[data-cookie-reject]').addEventListener('click', () => save('rejected'));
    }

    syncCookiePage() {
        const panel = document.querySelector('[data-cookie-page]');
        if (!panel) return;
        const status = this.readCookieConsentStatus();
        const accepted = status === 'accepted';
        const decided = status === 'accepted' || status === 'rejected';
        panel.querySelector('[data-cookie-current]').textContent = decided
            ? `Elección actual: ${accepted ? 'aceptadas' : 'rechazadas'}`
            : 'Todavía no has elegido una opción.';
        panel.querySelector('[data-cookie-accept]')?.toggleAttribute('hidden', accepted);
        panel.querySelector('[data-cookie-reject]')?.toggleAttribute('hidden', decided && !accepted);
        panel.querySelectorAll('[data-cookie-accept], [data-cookie-reject]').forEach((button) => {
            button.addEventListener('click', async () => {
                const statusChoice = button.hasAttribute('data-cookie-accept') ? 'accepted' : 'rejected';
                await fetch('/api/auth/cookies', {
                    method: 'POST',
                    headers: window.RaceStreamCsrf.headers({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify({ accepted: statusChoice === 'accepted' })
                }).catch(() => {});
                this.writeCookieConsent(statusChoice);
                window.location.reload();
            });
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 12-05-2026
     * @modified 18-05-2026
     * @description Guarda accepted o rejected aunque la sincronización remota falle
     * @param {boolean|string} value Preferencia del usuario
     */
    writeCookieConsent(value) {
        const status = this.normalizeCookieConsentStatus(value);
        if (!status || status === 'undecided') return;
        document.cookie = `rs_cookie_consent=${status}; path=/; max-age=15552000; SameSite=Lax`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Lee la cookie técnica local y descarta valores que no sean una decisión real
     * @returns {string} Estado accepted, rejected o cadena vacía
     */
    readCookieConsentStatus() {
        const match = document.cookie.match(/(?:^|;\s*)rs_cookie_consent=([^;]+)/);
        const status = this.normalizeCookieConsentStatus(match ? decodeURIComponent(match[1]) : '');
        return status === 'accepted' || status === 'rejected' ? status : '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Normaliza estados nuevos y booleaños legacy a valores seguros de navegador
     * @param {boolean|string} value Estado recibido
     * @returns {string} Estado normalizado
     */
    normalizeCookieConsentStatus(value) {
        if (value === true) return 'accepted';
        if (value === false) return 'rejected';
        const normalized = `${value || ''}`.trim().toLowerCase();
        if (normalized === 'accepted' || normalized === 'true') return 'accepted';
        if (normalized === 'rejected' || normalized === 'false') return 'rejected';
        if (normalized === 'undecided') return 'undecided';
        return '';
    }

    static boot() {
        const start = () => {
            if (!window.raceStreamSharedLayout && document.getElementById('mainNavbar')) {
                window.raceStreamSharedLayout = new RaceStreamSharedLayout();
            }
        };
        if (document.getElementById('mainNavbar')) {
            start();
        } else {
            document.addEventListener('DOMContentLoaded', start, { once: true });
        }
    }
}

RaceStreamSharedLayout.boot();
