/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 30-04-2026
 * @modified 05-05-2026
 * @description Activa navbar, estado de cuenta, cookies y altura responsive en todas las paginas
 */
document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
    const navbar = document.getElementById('mainNavbar');
    const profileMenu = document.querySelector('.rs-profile-dropdown__menu');
    let lastScrollY = window.scrollY;

    document.querySelectorAll('.rs-navbar__link').forEach((link) => {
        const href = link.getAttribute('href');
        const normalizedHref = href === '/' ? '/index.html' : href;
        link.classList.toggle('rs-navbar__link--active', normalizedHref === path);

        if (path === '/index.html' && normalizedHref === '/index.html') {
            link.remove();
        }
    });

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Sincroniza la franja de GP con la altura real del navbar
     */
    const updateNavbarOffset = () => {
        document.documentElement.style.setProperty('--rs-navbar-height', `${navbar?.offsetHeight || 74}px`);
    };

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Oculta el navbar principal al bajar y lo muestra al subir
     */
    const handleNavbarVisibility = () => {
        const currentY = window.scrollY;
        document.body.classList.toggle('rs-nav-hidden', currentY > lastScrollY && currentY > 120);
        lastScrollY = Math.max(currentY, 0);
    };

    updateNavbarOffset();
    window.addEventListener('load', updateNavbarOffset);
    window.addEventListener('resize', updateNavbarOffset);
    window.addEventListener('scroll', handleNavbarVisibility, { passive: true });

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Sincroniza el menu de cuenta con la sesion actual
     */
    const syncAccountMenu = async () => {
        if (!profileMenu) return;
        try {
            const response = await fetch('/api/auth/me', { cache: 'no-store' });
            const user = await response.json();
            if (!user.authenticated) return;
            profileMenu.innerHTML = `
                <a class="rs-button rs-button--primary rs-profile-dropdown__button" href="/account.html">Mi Cuenta</a>
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/preferences.html">Preferencias</a>
                <a class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" href="/privacy.html">Privacidad</a>
                <button class="rs-button rs-profile-dropdown__button rs-profile-dropdown__button--secondary" type="button" data-logout>Cerrar sesión</button>
            `;
            profileMenu.querySelector('[data-logout]')?.addEventListener('click', async () => {
                await fetch('/api/auth/logout', { method: 'POST' });
                window.location.href = '/login.html?logout';
            });
        } catch {
            /* La navegacion publica no debe bloquearse si la sesion no responde. */
        }
    };

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Muestra consentimiento de cookies si todavia no se ha decidido
     */
    const renderCookieBanner = () => {
        if (document.cookie.includes('rs_cookie_consent=')) return;
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
        const save = async (accepted) => {
            await fetch('/api/auth/cookies', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ accepted })
            }).catch(() => {});
            banner.remove();
        };
        banner.querySelector('[data-cookie-accept]').addEventListener('click', () => save(true));
        banner.querySelector('[data-cookie-reject]').addEventListener('click', () => save(false));
    };

    const syncCookiePage = () => {
        const panel = document.querySelector('[data-cookie-page]');
        if (!panel) return;
        const match = document.cookie.match(/(?:^|;\s*)rs_cookie_consent=([^;]+)/);
        const value = match ? decodeURIComponent(match[1]) : '';
        const accepted = value === 'accepted';
        const decided = value === 'accepted' || value === 'rejected';
        panel.querySelector('[data-cookie-current]').textContent = decided
            ? `Elección actual: ${accepted ? 'aceptadas' : 'rechazadas'}`
            : 'Todavía no has elegido una opción.';
        panel.querySelector('[data-cookie-accept]')?.toggleAttribute('hidden', accepted);
        panel.querySelector('[data-cookie-reject]')?.toggleAttribute('hidden', decided && !accepted);
        panel.querySelectorAll('[data-cookie-accept], [data-cookie-reject]').forEach((button) => {
            button.addEventListener('click', async () => {
                const acceptedChoice = button.hasAttribute('data-cookie-accept');
                await fetch('/api/auth/cookies', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ accepted: acceptedChoice })
                }).catch(() => {});
                document.cookie = `rs_cookie_consent=${acceptedChoice ? 'accepted' : 'rejected'}; path=/; max-age=15552000; SameSite=Lax`;
                window.location.reload();
            });
        });
    };

    syncAccountMenu();
    renderCookieBanner();
    syncCookiePage();
});
