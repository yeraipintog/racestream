/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 30-04-2026
 * @modified 03-05-2026
 * @description Activa el navbar, calcula su altura y lo oculta al bajar en todas las paginas
 */
document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
    const navbar = document.getElementById('mainNavbar');
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
});
