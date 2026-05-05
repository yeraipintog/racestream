/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description Gestiona cuenta, favoritos, preferencias, foro y contacto
 */
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.rsPrivatePage;

    const api = async (url, options = {}) => {
        const response = await fetch(url, {
            ...options,
            headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'No se pudo cargar la información');
        return data;
    };

    const escape = (value) => `${value ?? ''}`
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');

    const loadMe = async () => {
        const user = await api('/api/auth/me');
        document.querySelectorAll('[data-user-name]').forEach((node) => node.textContent = user.name || 'Usuario');
        document.querySelectorAll('[data-user-email]').forEach((node) => node.textContent = user.email || '-');
        document.querySelectorAll('[name="name"]').forEach((node) => node.value = user.name || '');
        document.querySelectorAll('[name="email"]').forEach((node) => node.value = user.email || '');
        document.querySelectorAll('[name="notificationsEnabled"]').forEach((node) => {
            node.checked = Boolean(user.notificationsEnabled);
        });
        return user;
    };

    const getFavoriteUrl = (item) => {
        if (item.url) return item.url;
        const id = encodeURIComponent(item.externalId || '');
        if (item.type === 'Piloto') return `/drivers.html?driverId=${id}`;
        if (item.type === 'Escudería') return `/teams.html?constructorId=${id}`;
        if (item.type === 'Sesión') return `/sessions.html?sessionKey=${id}`;
        return '/calendar.html';
    };

    const renderFavorites = async () => {
        const target = document.getElementById('favoritesList');
        if (!target) return;
        const rows = await api('/api/favorites');
        target.innerHTML = rows.length ? rows.map((item) => `
            <article class="rs-favorite-card" data-favorite-open="${escape(getFavoriteUrl(item))}">
                <strong>${escape(item.title)}</strong>
                <span>${escape(item.type)} · ${escape(item.description || item.externalId)}</span>
                <button class="rs-favorite-card__remove" type="button" data-remove-favorite="${item.id}" aria-label="Quitar favorito">×</button>
            </article>
        `).join('') : '<p class="empty-state">Todavía no has guardado favoritos.</p>';
        target.querySelectorAll('[data-favorite-open]').forEach((card) => {
            card.addEventListener('click', () => window.location.href = card.dataset.favoriteOpen);
        });
        target.querySelectorAll('[data-remove-favorite]').forEach((button) => {
            button.addEventListener('click', async (event) => {
                event.preventDefault();
                event.stopPropagation();
                await api(`/api/favorites/${button.dataset.removeFavorite}`, { method: 'DELETE' });
                renderFavorites();
            });
        });
    };

    const isStrongPassword = (password) =>
        password.length >= 8
        && /[a-z]/.test(password)
        && /[A-Z]/.test(password)
        && /\d/.test(password)
        && /[^A-Za-z0-9]/.test(password);

    const updatePasswordRules = (form) => {
        const password = form?.querySelector('[name="password"]')?.value || '';
        form?.querySelectorAll('[data-password-rule]').forEach((rule) => {
            const type = rule.dataset.passwordRule;
            const ok = {
                length: password.length >= 8,
                lower: /[a-z]/.test(password),
                upper: /[A-Z]/.test(password),
                number: /\d/.test(password),
                symbol: /[^A-Za-z0-9]/.test(password)
            }[type];
            rule.classList.toggle('rs-password-rule--ok', ok);
        });
    };

    const bindProfileForms = () => {
        const profileForm = document.getElementById('profileForm');
        const passwordForm = document.getElementById('passwordForm');
        const alert = document.getElementById('profileAlert') || document.getElementById('passwordAlert');
        profileForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = new FormData(profileForm);
            try {
                await api('/api/user/profile', {
                    method: 'PUT',
                    body: JSON.stringify({
                        name: data.get('name'),
                        email: data.get('email'),
                        notificationsEnabled: data.get('notificationsEnabled') === 'on'
                    })
                });
                alert.textContent = 'Datos guardados.';
            } catch (error) {
                alert.textContent = error.message;
            }
        });
        passwordForm?.querySelectorAll('[type="password"]').forEach((input) => {
            input.addEventListener('input', () => updatePasswordRules(passwordForm));
        });
        passwordForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = new FormData(passwordForm);
            const password = `${data.get('password') || ''}`;
            const confirmPassword = `${data.get('confirmPassword') || ''}`;
            if (password !== confirmPassword) {
                alert.textContent = 'Las contraseñas no coinciden.';
                return;
            }
            if (!isStrongPassword(password)) {
                alert.textContent = 'Completa todos los requisitos de seguridad.';
                return;
            }
            try {
                await api('/api/user/password', { method: 'PUT', body: JSON.stringify({ password }) });
                passwordForm.reset();
                updatePasswordRules(passwordForm);
                alert.textContent = 'Contraseña actualizada.';
            } catch (error) {
                alert.textContent = error.message;
            }
        });
        updatePasswordRules(passwordForm);
    };

    const bindForum = async () => {
        const list = document.getElementById('forumList');
        const form = document.getElementById('forumForm');
        const alert = document.getElementById('forumAlert');
        const load = async () => {
            if (!list) return;
            const posts = await api('/api/forum');
            list.innerHTML = posts.length ? posts.map((post) => `
                <article class="rs-forum-card">
                    <strong>${escape(post.title)}</strong>
                    <span>${escape(post.category)} · ${escape(post.author)} · ${new Date(post.createdAt).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })}</span>
                    <p>${escape(post.content)}</p>
                    <button class="rs-link-chip" type="button" data-like-post="${post.id}">Me gusta · ${post.likes}</button>
                </article>
            `).join('') : '<p class="empty-state">Sé el primero en abrir debate.</p>';
            list.querySelectorAll('[data-like-post]').forEach((button) => {
                button.addEventListener('click', async () => {
                    await api(`/api/forum/${button.dataset.likePost}/like`, { method: 'POST' });
                    load();
                });
            });
        };
        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = new FormData(form);
            try {
                await api('/api/forum', {
                    method: 'POST',
                    body: JSON.stringify({
                        category: data.get('category'),
                        title: data.get('title'),
                        content: data.get('content')
                    })
                });
                form.reset();
                alert.textContent = 'Publicado.';
                load();
            } catch (error) {
                alert.textContent = error.message;
            }
        });
        await load();
    };

    const bindContact = () => {
        const form = document.getElementById('contactForm');
        const alert = document.getElementById('contactAlert');
        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = new FormData(form);
            try {
                const response = await api('/api/contact', {
                    method: 'POST',
                    body: JSON.stringify({ subject: data.get('subject'), message: data.get('message') })
                });
                form.reset();
                alert.textContent = `Mensaje enviado correctamente a ${response.recipient || 'soporte'}.`;
            } catch (error) {
                alert.textContent = error.message;
            }
        });
    };

    loadMe().then(() => {
        if (page === 'favorites') renderFavorites();
        bindProfileForms();
        bindForum();
        bindContact();
    }).catch(() => {
        window.location.href = '/login.html';
    });
});
