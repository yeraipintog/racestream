/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.5
 * @created 05-05-2026
 * @modified 07-05-2026
 * @description Gestiona cuenta, favoritos, preferencias, avisos web/correo, foro y contacto
 */
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.rsPrivatePage;

    const api = async (url, options = {}) => {
        const response = await fetch(url, {
            ...options,
            headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'Error');
        return data;
    };

    const shortAlert = (value) => {
        const text = `${value || ''}`.toLowerCase();
        if (text.includes('bloque')) return 'Usuario bloqueado';
        if (text.includes('smtp')) return 'SMTP desactivado';
        if (text.includes('contrase') && text.includes('coinc')) return 'No coinciden';
        if (text.includes('requisito') || text.includes('caracter')) return 'Requisitos';
        if (text.includes('nombre')) return 'Nombre usado';
        if (text.includes('email')) return 'Email usado';
        return value && `${value}`.length <= 18 ? value : 'Error';
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
        document.querySelectorAll('[name="emailNotificationsEnabled"]').forEach((node) => {
            node.checked = Boolean(user.emailNotificationsEnabled);
        });
        document.querySelectorAll('[name="favoriteDigest"]').forEach((node) => {
            node.checked = Boolean(user.favoriteDigestEnabled);
        });
        document.querySelectorAll('[name="favoriteDigestEmail"]').forEach((node) => {
            node.checked = Boolean(user.favoriteDigestEmailEnabled);
        });
        return user;
    };

    const getFavoriteUrl = (item) => {
        if (item.url) return item.url;
        const id = encodeURIComponent(item.externalId || '');
        if (item.type === 'Piloto') return `/drivers.html?driverId=${id}`;
        if (item.type === 'Escudería') return `/teams.html?constructorId=${id}`;
        return '/calendar.html';
    };

    const isSessionFavorite = (item) => `${item.type || ''}`.toLowerCase().includes('ses');
    const isGpFavorite = (item) => `${item.type || ''}`.toLowerCase() === 'gp';
    const getNotificationKey = (meetingKey, session) => {
        const id = session.session_key || `${meetingKey}-${session.session_name || ''}-${session.date_start || ''}`;
        return `${meetingKey}|${id}`;
    };
    const readSessionNotifications = () => JSON.parse(localStorage.getItem('rs-session-notifications') || '{}');
    const saveSessionNotifications = (state) => localStorage.setItem('rs-session-notifications', JSON.stringify(state));

    const renderFavorites = async () => {
        const target = document.getElementById('favoritesList');
        if (!target) return;
        const rows = await api('/api/favorites');
        target.innerHTML = rows.length ? rows.map((item) => `
            <article class="rs-favorite-card ${isSessionFavorite(item) ? 'rs-favorite-card--static' : ''}" ${isSessionFavorite(item) ? '' : `data-favorite-open="${escape(getFavoriteUrl(item))}"`}>
                <strong>${escape(item.title)}</strong>
                <span>${escape(item.type)} · ${escape(item.description || item.externalId)}</span>
                ${isGpFavorite(item) ? '<div class="rs-favorite-card__sessions" data-gp-sessions></div>' : ''}
                ${isSessionFavorite(item) ? '<span>Las sesiones se gestionan como notificaciones dentro del GP favorito.</span>' : ''}
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
        hydrateGpSessionNotifications(target, rows);
    };

    const hydrateGpSessionNotifications = async (target, rows) => {
        const gpFavorites = rows.filter(isGpFavorite);
        [...target.querySelectorAll('[data-gp-sessions]')].forEach(async (container, index) => {
            container.addEventListener('click', (event) => event.stopPropagation());
            const favorite = gpFavorites[index];
            const params = new URLSearchParams((favorite.url || '').split('?')[1] || '');
            const meetingKey = params.get('meetingKey') || favorite.externalId;
            if (!meetingKey) return;
            container.innerHTML = '<span class="loading-state">Cargando sesiones para notificar...</span>';
            const sessions = await fetch(`/api/f1/schedule/meetings/${meetingKey}/sessions`, { cache: 'no-store' })
                .then((response) => response.ok ? response.json() : [])
                .catch(() => []);
            if (!Array.isArray(sessions) || !sessions.length) {
                container.innerHTML = '<span class="empty-state">No hay sesiones disponibles para notificar.</span>';
                return;
            }
            const state = readSessionNotifications();
            const sessionByKey = new Map(sessions.map((session) => [getNotificationKey(meetingKey, session), session]));
            container.innerHTML = sessions.map((session) => {
                const key = getNotificationKey(meetingKey, session);
                const date = session.date_start
                    ? new Date(session.date_start).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
                    : 'Horario pendiente';
                const checked = typeof state[key] === 'object' ? state[key].enabled : state[key];
                return `
                    <label class="rs-notification-option">
                        <input type="checkbox" data-session-notify="${escape(key)}" ${checked ? 'checked' : ''}>
                        <span>${escape(session.session_name || 'Sesión')} · ${escape(date)}</span>
                    </label>
                `;
            }).join('');
            container.querySelectorAll('[data-session-notify]').forEach((checkbox) => {
                checkbox.addEventListener('click', (event) => event.stopPropagation());
                checkbox.addEventListener('change', () => {
                    const nextState = readSessionNotifications();
                    const session = sessionByKey.get(checkbox.dataset.sessionNotify) || {};
                    nextState[checkbox.dataset.sessionNotify] = {
                        enabled: checkbox.checked,
                        title: `${session.session_name || 'Sesión'} · ${favorite.title || 'GP favorito'}`,
                        dateStart: session.date_start || ''
                    };
                    saveSessionNotifications(nextState);
                });
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
            const payload = {
                name: data.get('name'),
                email: data.get('email')
            };
            if (profileForm.querySelector('[name="notificationsEnabled"]')) {
                payload.notificationsEnabled = data.get('notificationsEnabled') === 'on';
            }
            if (profileForm.querySelector('[name="emailNotificationsEnabled"]')) {
                payload.emailNotificationsEnabled = data.get('emailNotificationsEnabled') === 'on';
            }
            if (profileForm.querySelector('[name="favoriteDigest"]')) {
                payload.favoriteDigestEnabled = data.get('favoriteDigest') === 'on';
                payload.favoriteDigestEmailEnabled = data.get('favoriteDigestEmail') === 'on';
            }
            try {
                await api('/api/user/profile', {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                sessionStorage.removeItem('rs-auth-me');
                alert.textContent = 'Guardado';
                if (payload.notificationsEnabled && 'Notification' in window && Notification.permission === 'default') {
                    await Notification.requestPermission();
                }
            } catch (error) {
                alert.textContent = shortAlert(error.message);
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
                alert.textContent = 'No coinciden';
                return;
            }
            if (!isStrongPassword(password)) {
                alert.textContent = 'Requisitos';
                return;
            }
            try {
                await api('/api/user/password', { method: 'PUT', body: JSON.stringify({ password }) });
                passwordForm.reset();
                updatePasswordRules(passwordForm);
                alert.textContent = 'Actualizada';
            } catch (error) {
                alert.textContent = shortAlert(error.message);
            }
        });
        updatePasswordRules(passwordForm);
    };

    document.querySelectorAll('[data-password-toggle]').forEach((button) => {
        button.addEventListener('click', () => {
            const input = button.closest('.rs-password-field')?.querySelector('input');
            if (!input) return;
            const visible = input.type === 'text';
            input.type = visible ? 'password' : 'text';
            button.setAttribute('aria-pressed', String(!visible));
            button.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
        });
    });

    const bindForum = async () => {
        const list = document.getElementById('forumList');
        const form = document.getElementById('forumForm');
        const alert = document.getElementById('forumAlert');
        const load = async () => {
            if (!list) return;
            const posts = await api('/api/forum');
            list.innerHTML = posts.length ? posts.map((post) => `
                <article class="rs-forum-card" data-post-id="${escape(post.id)}">
                    ${post.canDelete ? '<button class="rs-forum-card__delete" type="button" aria-label="Eliminar mensaje del foro" data-delete-post>×</button>' : ''}
                    <strong>${escape(post.title)}</strong>
                    <span>${escape(post.category)} · ${escape(post.author)} · ${new Date(post.createdAt).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })}</span>
                    <p>${escape(post.content)}</p>
                    <div class="rs-forum-card__actions">
                        <button class="rs-forum-like ${post.likedByMe ? 'rs-forum-like--active' : ''}" type="button" data-like-post="${post.id}" aria-pressed="${post.likedByMe}">
                            <span class="rs-forum-like__icon" aria-hidden="true">${post.likedByMe ? '♥' : '♡'}</span>
                            <span>${post.likes}</span>
                        </button>
                        <button class="rs-link-chip" type="button" data-reply-toggle="${post.id}">Responder</button>
                    </div>
                    <form class="rs-forum-reply" data-reply-form="${post.id}" hidden>
                        <textarea name="content" placeholder="Escribe tu respuesta..." required></textarea>
                        <button class="rs-button" type="submit">Responder</button>
                    </form>
                </article>
            `).join('') : '<p class="empty-state">Sé el primero en abrir debate.</p>';
            list.querySelectorAll('[data-like-post]').forEach((button) => {
                button.addEventListener('click', async () => {
                    await api(`/api/forum/${button.dataset.likePost}/like`, { method: 'POST' });
                    load();
                });
            });
            list.querySelectorAll('[data-delete-post]').forEach((button) => {
                button.addEventListener('click', async () => {
                    const card = button.closest('[data-post-id]');
                    if (!card || !confirm('¿Eliminar este mensaje del foro?')) return;
                    await api(`/api/forum/${card.dataset.postId}`, { method: 'DELETE' });
                    load();
                });
            });
            list.querySelectorAll('[data-reply-toggle]').forEach((button) => {
                button.addEventListener('click', () => {
                    const replyForm = list.querySelector(`[data-reply-form="${button.dataset.replyToggle}"]`);
                    replyForm?.toggleAttribute('hidden');
                });
            });
            list.querySelectorAll('[data-reply-form]').forEach((replyForm) => {
                replyForm.addEventListener('submit', async (event) => {
                    event.preventDefault();
                    const post = posts.find((item) => `${item.id}` === replyForm.dataset.replyForm);
                    const data = new FormData(replyForm);
                    await api('/api/forum', {
                        method: 'POST',
                        body: JSON.stringify({
                            category: 'Respuesta',
                            title: `Re: ${post?.title || 'publicación'}`,
                            content: data.get('content')
                        })
                    });
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
                alert.textContent = 'Publicado';
                load();
            } catch (error) {
                alert.textContent = shortAlert(error.message);
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
                alert.textContent = response.mailSent ? 'Enviado' : 'Guardado';
            } catch (error) {
                alert.textContent = shortAlert(error.message);
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
