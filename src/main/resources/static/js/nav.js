/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.4.0
 * @created 30-04-2026
 * @modified 18-05-2026
 * @description Pinta navbar, footer, estructura comun, cookies con estado explicito, ocultacion de race strip y avisos web
 */
class RaceStreamSharedLayout {

    constructor() {
        this.renderLayout();
        this.navbar = document.getElementById('mainNavbar');
        this.raceStrip = document.getElementById('raceStrip');
        this.profileMenu = document.querySelector('.rs-profile-dropdown__menu');
        this.path = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
        this.lastScrollY = Math.max(window.scrollY, 0);
        this.scrollTicking = false;
        this.bindNavigation();
        this.bindDropdowns();
        this.bindLoginHashLinks();
        this.bindNavbarVisibility();
        this.updateNavbarOffset();
        this.observeHeaderHeights();
        this.syncAccountMenu();
        this.renderCookieBanner();
        this.syncCookiePage();
        window.addEventListener('load', () => this.updateNavbarOffset());
        window.addEventListener('resize', () => this.updateNavbarOffset());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Inserta la estructura comun reutilizable de todas las paginas
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
                    <div class="rs-race-strip__label"><span>Próximo GP</span></div>
                    <div class="rs-race-strip__main">
                        <div class="rs-race-strip__title-row">
                            <img id="raceStripFlag" class="rs-flag-inline rs-flag-inline--medium" src="" alt="Bandera del país" style="display:none;">
                            <span id="raceStripTitle" class="rs-race-strip__title">Pr&oacute;ximo GP</span>
                        </div>
                        <span id="raceStripMeta" class="rs-race-strip__meta">Actualizando datos</span>
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
            link.classList.toggle('rs-navbar__link--active', normalizedHref === this.path);
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 06-05-2026
     * @description Activa menus del navbar comun evitando eventos duplicados
     */
    bindDropdowns() {
        const profileDropdown = document.getElementById('profileDropdown');
        const mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
        if (profileDropdown && !profileDropdown.dataset.rsDropdownBound) {
            profileDropdown.dataset.rsDropdownBound = 'true';
            profileDropdown.querySelector('.rs-profile-dropdown__trigger')?.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                profileDropdown.classList.toggle('rs-profile-dropdown--open');
                mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
            });
            profileDropdown.querySelector('.rs-profile-dropdown__menu')?.addEventListener('click', (event) => event.stopPropagation());
        }
        if (mobileMenuDropdown && !mobileMenuDropdown.dataset.rsDropdownBound) {
            mobileMenuDropdown.dataset.rsDropdownBound = 'true';
            mobileMenuDropdown.querySelector('.rs-navbar__menu-trigger')?.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                mobileMenuDropdown.classList.toggle('rs-navbar-mobile-menu--open');
                profileDropdown?.classList.remove('rs-profile-dropdown--open');
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
    }

    updateNavbarOffset() {
        const navbarHeight = this.navbar?.offsetHeight || 74;
        const raceStripHeight = this.raceStrip?.offsetHeight || 58;
        document.documentElement.style.setProperty('--rs-navbar-height', `${navbarHeight}px`);
        document.documentElement.style.setProperty('--rs-race-strip-height', `${raceStripHeight}px`);
        document.documentElement.style.setProperty('--rs-header-offset', `${navbarHeight + raceStripHeight}px`);
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
     * @description Sincroniza el menu de cuenta y restaura la decision de cookies guardada en BBDD
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
            if (!user.authenticated) return;
            this.profileMenu.innerHTML = `
                <a class="rs-button rs-button--primary rs-profile-dropdown__button" href="/account.html">Mi Cuenta</a>
                ${user.role === 'ADMIN' ? '<a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/admin.html">Admin</a>' : ''}
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/preferences.html">Preferencias</a>
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/privacy.html">Privacidad</a>
                <button class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" type="button" data-logout>Cerrar sesión</button>
            `;
            this.syncWebNotifications(user);
            this.profileMenu.querySelector('[data-logout]')?.addEventListener('click', async () => {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    cache: 'no-store',
                    headers: { 'Cache-Control': 'no-cache' }
                });
                sessionStorage.removeItem(cacheKey);
                window.location.href = '/login.html?logout';
            });
        } catch {
            /* La navegacion publica no debe bloquearse si la sesion no responde. */
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Muestra avisos de sesiones guardadas dentro de la web y, si el navegador lo permite, como notificacion
     * @param {Object} user Usuario autenticado con preferencias
     */
    syncWebNotifications(user) {
        if (!user?.notificationsEnabled) return;
        const state = JSON.parse(localStorage.getItem('rs-session-notifications') || '{}');
        const notified = JSON.parse(localStorage.getItem('rs-session-notified') || '{}');
        const now = Date.now();
        Object.entries(state).forEach(([key, item]) => {
            if (!item || item.enabled === false || item === false) return;
            const dateStart = typeof item === 'object' ? item.dateStart : '';
            const startsAt = new Date(dateStart).getTime();
            if (!Number.isFinite(startsAt) || startsAt < now || startsAt - now > 1800000 || notified[key]) return;
            const title = typeof item === 'object' ? item.title : 'Sesión favorita';
            notified[key] = true;
            this.showWebNotification(`La sesión ${title} empieza pronto.`);
            if ('Notification' in window && Notification.permission === 'granted') {
                new Notification('RaceStream', { body: `La sesión ${title} empieza pronto.` });
            }
        });
        localStorage.setItem('rs-session-notified', JSON.stringify(notified));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Inserta un aviso temporal no bloqueante dentro de la interfaz
     * @param {string} message Mensaje visible
     */
    showWebNotification(message) {
        const toast = document.createElement('div');
        toast.className = 'rs-web-toast';
        toast.textContent = message;
        document.body.appendChild(toast);
        window.setTimeout(() => toast.remove(), 8000);
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
                headers: { 'Content-Type': 'application/json' },
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
                    headers: { 'Content-Type': 'application/json' },
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
     * @description Guarda accepted o rejected aunque la sincronizacion remota falle
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
     * @description Lee la cookie tecnica local y descarta valores que no sean una decision real
     * @returns {string} Estado accepted, rejected o cadena vacia
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
     * @description Normaliza estados nuevos y booleanos legacy a valores seguros de navegador
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
