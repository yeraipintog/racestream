/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.3
 * @created 05-05-2026
 * @modified 22-05-2026
 * @description Gestiona cuenta, borrado, favoritos, preferencias, notificaciones de GP, foro y contacto sin avisos duplicados de ADMIN
 */
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.rsPrivatePage;
    const PASSWORD_REQUIREMENTS_MESSAGE = 'La contraseña no cumple los requisitos';
    const PASSWORD_MISMATCH_MESSAGE = 'Las contraseñas no coinciden';
    const SAME_PASSWORD_MESSAGE = 'La contraseña introducida es la registrada actualmente';

    const api = async (url, options = {}) => {
        const response = await fetch(url, {
            ...options,
            cache: 'no-store',
            headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-cache', ...(options.headers || {}) }
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            const error = new Error(data.error || 'Error');
            error.field = data.field || '';
            error.error = data.error || 'Error';
            error.status = response.status;
            throw error;
        }
        return data;
    };

    const shortAlert = (value) => {
        const text = `${value || ''}`.toLowerCase();
        if (text.includes('bloque')) return 'Usuario bloqueado';
        if (text.includes('smtp')) return 'SMTP desactivado';
        if (text.includes('contrase') && text.includes('coinc')) return PASSWORD_MISMATCH_MESSAGE;
        if (text.includes('actual') && (text.includes('guardad') || text.includes('misma') || text.includes('ya es'))) return SAME_PASSWORD_MESSAGE;
        if (text.includes('actual') && text.includes('oblig')) return 'Contraseña actual obligatoria';
        if (text.includes('actual') && (text.includes('correct') || text.includes('incorrect'))) return 'Contraseña actual incorrecta';
        if (text.includes('admin')) return 'Acción no permitida';
        if (text.includes('requisito') || text.includes('caracter')) return PASSWORD_REQUIREMENTS_MESSAGE;
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

    const bindAutoGrowTextarea = (textarea) => {
        if (!textarea) return;
        const update = () => {
            textarea.style.height = 'auto';
            textarea.style.height = `${textarea.scrollHeight}px`;
        };
        if (!textarea.dataset.autogrowBound) {
            textarea.addEventListener('input', update);
            textarea.dataset.autogrowBound = 'true';
        }
        window.requestAnimationFrame(update);
    };

    const bindCharCounter = (field, counter, max) => {
        if (!field || !counter) return;
        const update = () => {
            const length = field.value.length;
            counter.textContent = `${length}/${max}`;
            counter.classList.toggle('rs-char-counter--limit', length >= max);
        };
        if (!field.dataset.counterBound) {
            field.addEventListener('input', update);
            field.dataset.counterBound = 'true';
        }
        update();
    };

    const bindTextAreaTools = (field, counter, max) => {
        bindAutoGrowTextarea(field);
        bindCharCounter(field, counter, max);
    };

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
        if (item.url) return ensureFavoriteYear(item.url, item.seasonYear);
        const id = encodeURIComponent(item.externalId || '');
        const year = item.seasonYear ? `year=${encodeURIComponent(item.seasonYear)}&` : '';
        if (item.type === 'Piloto') return `/drivers.html?${year}driverId=${id}`;
        if (item.type === 'Escudería') return `/teams.html?${year}constructorId=${id}`;
        return '/calendar.html';
    };
    const ensureFavoriteYear = (url, seasonYear) => {
        if (!seasonYear || !url || !/\.html/.test(url) || url.includes('year=')) return url;
        const separator = url.includes('?') ? '&' : '?';
        return `${url}${separator}year=${encodeURIComponent(seasonYear)}`;
    };
    const favoriteSeasonLabel = (item) => item.seasonYear ? `Temporada ${item.seasonYear}` : 'Temporada no indicada';

    const isSessionFavorite = (item) => `${item.type || ''}`.toLowerCase().includes('ses');
    const isGpFavorite = (item) => `${item.type || ''}`.toLowerCase() === 'gp';
    const canNotifySession = (session) => {
        const end = new Date(session?.date_end).getTime();
        return !session?.is_cancelled && (!Number.isFinite(end) || Date.now() <= end);
    };
    const getNotificationKey = (meetingKey, session) => {
        const id = session.session_key || `${meetingKey}-${session.session_name || ''}-${session.date_start || ''}`;
        return `${meetingKey}|${id}`;
    };
    const readSessionNotifications = () => {
        try {
            return JSON.parse(localStorage.getItem('rs-session-notifications') || '{}');
        } catch {
            return {};
        }
    };
    const saveSessionNotifications = (state) => localStorage.setItem('rs-session-notifications', JSON.stringify(state));
    const clearGpSessionNotifications = (meetingKey) => {
        if (!meetingKey) return;
        const state = readSessionNotifications();
        const prefix = `${meetingKey}|`;
        let changed = false;
        Object.keys(state).forEach((key) => {
            if (!key.startsWith(prefix)) return;
            delete state[key];
            changed = true;
        });
        if (changed) saveSessionNotifications(state);
    };
    let favoriteRows = null;
    const gpSessionsCache = new Map();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Reutiliza favoritos ya cargados para no repetir API al eliminar tarjetas
     */
    const loadFavoriteRows = async () => {
        if (!favoriteRows) favoriteRows = await api('/api/favorites');
        return favoriteRows;
    };

    const renderFavorites = async (rows = null) => {
        const target = document.getElementById('favoritesList');
        if (!target) return;
        rows = rows || await loadFavoriteRows();
        target.innerHTML = rows.length ? rows.map((item) => `
            <article class="rs-favorite-card ${isSessionFavorite(item) ? 'rs-favorite-card--static' : ''}" ${isSessionFavorite(item) ? '' : `data-favorite-open="${escape(getFavoriteUrl(item))}"`}>
                <strong>${escape(item.title)}</strong>
                <span>${escape(item.type)} · ${escape(favoriteSeasonLabel(item))} · ${escape(item.description || item.externalId)}</span>
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
                const favorite = (favoriteRows || rows).find((item) => `${item.id}` === `${button.dataset.removeFavorite}`);
                await api(`/api/favorites/${button.dataset.removeFavorite}`, { method: 'DELETE' });
                if (isGpFavorite(favorite)) {
                    const params = new URLSearchParams((favorite.url || '').split('?')[1] || '');
                    clearGpSessionNotifications(params.get('meetingKey') || favorite.externalId);
                }
                favoriteRows = (favoriteRows || rows).filter((item) => `${item.id}` !== `${button.dataset.removeFavorite}`);
                renderFavorites(favoriteRows);
            });
        });
        hydrateGpSessionNotifications(target, rows);
    };

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Carga sesiones de GP favoritos y conserva la decisión manual del usuario
     * @param {HTMLElement} target Contenedor de favoritos
     * @param {Array} rows Favoritos cargados
     */
    const hydrateGpSessionNotifications = async (target, rows) => {
        const gpFavorites = rows.filter(isGpFavorite);
        [...target.querySelectorAll('[data-gp-sessions]')].forEach(async (container, index) => {
            container.addEventListener('click', (event) => event.stopPropagation());
            const favorite = gpFavorites[index];
            const params = new URLSearchParams((favorite.url || '').split('?')[1] || '');
            const meetingKey = params.get('meetingKey') || favorite.externalId;
            if (!meetingKey) return;
            container.innerHTML = '<span class="loading-state">Cargando sesiones para notificar...</span>';
            const sessions = gpSessionsCache.has(meetingKey)
                ? gpSessionsCache.get(meetingKey)
                : await fetch(`/api/f1/schedule/meetings/${encodeURIComponent(meetingKey)}/sessions`, { cache: 'no-store' })
                    .then((response) => response.ok ? response.json() : null)
                    .catch(() => null);
            gpSessionsCache.set(meetingKey, sessions);
            if (!Array.isArray(sessions)) {
                container.innerHTML = '<span class="empty-state">No se han podido cargar las sesiones para notificar.</span>';
                return;
            }
            const notifiableSessions = (Array.isArray(sessions) ? sessions : []).filter(canNotifySession);
            if (!notifiableSessions.length) {
                container.innerHTML = '<span class="empty-state">Este GP ya no tiene sesiones pendientes.</span>';
                return;
            }
            const state = readSessionNotifications();
            const sessionByKey = new Map(notifiableSessions.map((session) => [getNotificationKey(meetingKey, session), session]));
            let changed = false;
            notifiableSessions.forEach((session) => {
                const key = getNotificationKey(meetingKey, session);
                const item = {
                    enabled: true,
                    title: `${session.session_name || 'Sesión'} · ${favorite.title || 'GP favorito'}`,
                    dateStart: session.date_start || ''
                };
                if (state[key] === true) {
                    state[key] = item;
                    changed = true;
                    return;
                }
                if (state[key] !== undefined) return;
                state[key] = item;
                changed = true;
            });
            if (changed) saveSessionNotifications(state);
            container.innerHTML = notifiableSessions.map((session) => {
                const key = getNotificationKey(meetingKey, session);
                const date = session.date_start
                    ? new Date(session.date_start).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
                    : 'Horario pendiente';
                const checked = typeof state[key] === 'object' ? state[key].enabled !== false : state[key] !== false;
                return `
                    <div class="rs-notification-option">
                        <input type="checkbox" data-session-notify="${escape(key)}" ${checked ? 'checked' : ''}>
                        <span>${escape(session.session_name || 'Sesión')} · ${escape(date)}</span>
                    </div>
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

    const fieldErrorId = (input) => `${input.closest('form')?.id || 'rs-form'}-${input.name || 'field'}-error`;

    const setFieldError = (input, message) => {
        const label = input?.closest('label');
        if (!input || !label) return;
        label.classList.add('rs-field--invalid');
        input.setAttribute('aria-invalid', 'true');
        let error = label.nextElementSibling?.classList.contains('rs-field-error') ? label.nextElementSibling : null;
        if (!error) {
            error = document.createElement('span');
            error.className = 'rs-field-error';
            label.insertAdjacentElement('afterend', error);
        }
        error.id = fieldErrorId(input);
        error.textContent = message;
        input.setAttribute('aria-describedby', error.id);
    };

    const clearFieldError = (input) => {
        const label = input?.closest('label');
        if (!input || !label) return;
        label.classList.remove('rs-field--invalid');
        input.removeAttribute('aria-invalid');
        input.removeAttribute('aria-describedby');
        const error = label.nextElementSibling;
        if (error?.classList.contains('rs-field-error')) error.remove();
    };

    const clearFormFieldErrors = (form) => {
        form?.querySelectorAll('input').forEach(clearFieldError);
    };

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

    const bindProfileForms = (user) => {
        const profileForm = document.getElementById('profileForm');
        const passwordForm = document.getElementById('passwordForm');
        const deleteAccountButton = document.getElementById('deleteAccountButton');
        const deleteAccountAlert = document.getElementById('deleteAccountAlert');
        const alert = document.getElementById('profileAlert') || document.getElementById('passwordAlert');
        const isAdmin = user?.role === 'ADMIN';
        if (isAdmin) {
            [profileForm, passwordForm].forEach((form) => {
                form?.querySelectorAll('input, button, select, textarea').forEach((node) => {
                    node.disabled = true;
                });
            });
            if (deleteAccountButton) deleteAccountButton.hidden = true;
            if (alert) alert.textContent = '';
            if (deleteAccountAlert) deleteAccountAlert.textContent = '';
            updatePasswordRules(passwordForm);
            return;
        }
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
            input.addEventListener('input', () => {
                clearFieldError(input);
                updatePasswordRules(passwordForm);
            });
        });
        passwordForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = new FormData(passwordForm);
            const currentPassword = `${data.get('currentPassword') || ''}`;
            const password = `${data.get('password') || ''}`;
            const confirmPassword = `${data.get('confirmPassword') || ''}`;
            clearFormFieldErrors(passwordForm);
            if (!currentPassword) {
                alert.textContent = '';
                setFieldError(passwordForm.querySelector('[name="currentPassword"]'), 'Contraseña actual obligatoria');
                return;
            }
            if (password !== confirmPassword) {
                alert.textContent = '';
                setFieldError(passwordForm.querySelector('[name="confirmPassword"]'), PASSWORD_MISMATCH_MESSAGE);
                return;
            }
            if (!isStrongPassword(password)) {
                alert.textContent = '';
                setFieldError(passwordForm.querySelector('[name="password"]'), PASSWORD_REQUIREMENTS_MESSAGE);
                return;
            }
            try {
                await api('/api/user/password', { method: 'PUT', body: JSON.stringify({ currentPassword, password, confirmPassword }) });
                passwordForm.reset();
                updatePasswordRules(passwordForm);
                alert.textContent = 'Actualizada';
            } catch (error) {
                const message = shortAlert(error.message);
                if (error.field === 'password' || message === PASSWORD_REQUIREMENTS_MESSAGE || message === SAME_PASSWORD_MESSAGE) {
                    alert.textContent = '';
                    setFieldError(passwordForm.querySelector('[name="password"]'), message);
                } else if (error.field === 'confirmPassword' || message === PASSWORD_MISMATCH_MESSAGE) {
                    alert.textContent = '';
                    setFieldError(passwordForm.querySelector('[name="confirmPassword"]'), message);
                } else if (error.field === 'currentPassword' || message.includes('actual')) {
                    alert.textContent = '';
                    setFieldError(passwordForm.querySelector('[name="currentPassword"]'), message);
                } else {
                    alert.textContent = message;
                }
            }
        });
        deleteAccountButton?.addEventListener('click', async () => {
            if (!confirm('¿Eliminar tu cuenta y todos sus datos asociados?')) return;
            deleteAccountButton.disabled = true;
            deleteAccountAlert.textContent = 'Eliminando...';
            try {
                await api('/api/user/account', { method: 'DELETE' });
                sessionStorage.clear();
                window.location.replace(`/login.html?deleted=${Date.now()}`);
            } catch (error) {
                deleteAccountButton.disabled = false;
                deleteAccountAlert.textContent = shortAlert(error.message);
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
        const search = document.getElementById('forumSearch');
        const composer = document.getElementById('forumComposer');
        const openComposer = document.getElementById('forumCreateToggle');
        const closeComposer = document.getElementById('forumCreateClose');
        const maxTitleLength = 20;
        const maxContentLength = 1000;
        let posts = [];
        let highlightedPostId = null;

        const normalizeForumSearch = (value) => `${value || ''}`.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
        const truncate = (value, max) => `${value || ''}`.slice(0, max);
        const postMatches = (post, query) => {
            if (!query) return true;
            const source = normalizeForumSearch([post.category, post.title, post.author, post.content].join(' '));
            const replySource = normalizeForumSearch((post.replies || []).map((reply) => [reply.author, reply.content].join(' ')).join(' '));
            return source.includes(query) || replySource.includes(query);
        };
        const renderForumContent = (content) => `
            <p class="rs-forum-text" data-forum-text>${escape(content)}</p>
            <button class="rs-forum-more" type="button" data-forum-more hidden>Ver más <span aria-hidden="true">▾</span></button>
        `;
        const renderReply = (reply) => `
            <article class="rs-forum-reply-card" data-post-id="${escape(reply.id)}">
                ${reply.canDelete ? '<button class="rs-forum-card__delete" type="button" aria-label="Eliminar respuesta del foro" data-delete-post>×</button>' : ''}
                <span>${escape(reply.author)} · ${new Date(reply.createdAt).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })}</span>
                ${renderForumContent(reply.content)}
            </article>
        `;
        const renderPost = (post) => `
            <article class="rs-forum-card ${`${post.id}` === highlightedPostId ? 'rs-forum-card--highlight' : ''}" data-post-id="${escape(post.id)}">
                ${post.canDelete ? '<button class="rs-forum-card__delete" type="button" aria-label="Eliminar mensaje del foro" data-delete-post>×</button>' : ''}
                <strong class="rs-forum-card__title">${escape(post.title)}</strong>
                <span>${escape(post.category)} · <b>${escape(post.author)}</b> · ${new Date(post.createdAt).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })}</span>
                ${renderForumContent(post.content)}
                <div class="rs-forum-card__actions">
                    <button class="rs-forum-like ${post.likedByMe ? 'rs-forum-like--active' : ''}" type="button" data-like-post="${post.id}" aria-pressed="${post.likedByMe}">
                        <span class="rs-forum-like__icon" aria-hidden="true">${post.likedByMe ? '♥' : '♡'}</span>
                        <span>${post.likes}</span>
                    </button>
                    <button class="rs-link-chip" type="button" data-reply-toggle="${post.id}">Responder</button>
                </div>
                <div class="rs-forum-replies">${(post.replies || []).map(renderReply).join('')}</div>
                <form class="rs-forum-reply" data-reply-form="${post.id}" hidden>
                    <textarea name="content" placeholder="Escribe tu respuesta..." maxlength="${maxContentLength}" required></textarea>
                    <small class="rs-char-counter" data-reply-counter>0/${maxContentLength}</small>
                    <label class="rs-form__inline">
                        <input name="policyAccepted" type="checkbox" required>
                        <span>Acepto las <a href="/terms.html#forum-policy">normas del foro</a> y la <a href="/privacy-policy.html#forum-policy">política de privacidad</a>.</span>
                    </label>
                    <button class="rs-button" type="submit">Responder</button>
                </form>
            </article>
        `;
        const paint = () => {
            if (!list) return;
            const query = normalizeForumSearch(search?.value || '').trim();
            const visiblePosts = posts.filter((post) => postMatches(post, query));
            list.innerHTML = visiblePosts.length ? visiblePosts.map(renderPost).join('') : '<p class="empty-state">No hay publicaciones que coincidan.</p>';
            bindForumListEvents();
            bindForumTextToggles();
            list.querySelectorAll('[data-reply-form]').forEach((replyForm) => {
                bindTextAreaTools(
                    replyForm.querySelector('textarea[name="content"]'),
                    replyForm.querySelector('[data-reply-counter]'),
                    maxContentLength
                );
            });
            if (highlightedPostId) {
                const target = list.querySelector(`[data-post-id="${CSS.escape(highlightedPostId)}"]`);
                target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
                window.setTimeout(() => {
                    target?.classList.remove('rs-forum-card--highlight');
                    highlightedPostId = null;
                }, 3000);
            }
        };
        const load = async () => {
            if (!list) return;
            posts = await api('/api/forum');
            paint();
        };
        const bindForumListEvents = () => {
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
                    const replyForm = list.querySelector(`[data-reply-form="${CSS.escape(button.dataset.replyToggle)}"]`);
                    replyForm?.toggleAttribute('hidden');
                    bindAutoGrowTextarea(replyForm?.querySelector('textarea[name="content"]'));
                });
            });
            list.querySelectorAll('[data-reply-form]').forEach((replyForm) => {
                replyForm.addEventListener('submit', async (event) => {
                    event.preventDefault();
                    if (replyForm.dataset.pending === 'true') return;
                    const post = posts.find((item) => `${item.id}` === replyForm.dataset.replyForm);
                    const data = new FormData(replyForm);
                    const content = `${data.get('content') || ''}`.trim();
                    const policyAccepted = data.get('policyAccepted') === 'on';
                    if (!content || content.length > maxContentLength) {
                        alert.textContent = `Máximo ${maxContentLength} caracteres`;
                        return;
                    }
                    if (!policyAccepted) {
                        alert.textContent = 'Debes aceptar las normas del foro y la política de privacidad';
                        return;
                    }
                    const button = replyForm.querySelector('button[type="submit"]');
                    const previousText = button?.textContent || 'Responder';
                    replyForm.dataset.pending = 'true';
                    replyForm.setAttribute('aria-busy', 'true');
                    if (button) {
                        button.disabled = true;
                        button.textContent = 'Publicando...';
                    }
                    try {
                        await api('/api/forum', {
                            method: 'POST',
                            body: JSON.stringify({
                                category: post?.category || 'Respuesta',
                                title: truncate(`Re: ${post?.title || 'publicación'}`, maxTitleLength),
                                content,
                                parentId: post?.id,
                                policyAccepted,
                                clientRequestId: crypto.randomUUID()
                            })
                        });
                        alert.textContent = '';
                        load();
                    } catch (error) {
                        alert.textContent = shortAlert(error.message);
                    } finally {
                        replyForm.dataset.pending = 'false';
                        replyForm.removeAttribute('aria-busy');
                        if (button) {
                            button.disabled = false;
                            button.textContent = previousText;
                        }
                    }
                });
            });
        };
        /**
         * @author Yerai Pinto
         * @since 1.0
         * @version 1.0.0
         * @created 11-05-2026
         * @description Activa Ver más solo cuando el mensaje supera tres líneas reales
         */
        const bindForumTextToggles = () => {
            window.requestAnimationFrame(() => {
                list.querySelectorAll('[data-forum-text]').forEach((text) => {
                    const button = text.nextElementSibling;
                    if (!button?.matches('[data-forum-more]')) return;
                    button.hidden = text.scrollHeight <= text.clientHeight + 1;
                    button.addEventListener('click', () => {
                        const expanded = text.classList.toggle('rs-forum-text--expanded');
                        button.innerHTML = expanded ? 'Ver menos <span aria-hidden="true">▴</span>' : 'Ver más <span aria-hidden="true">▾</span>';
                    });
                });
            });
        };
        openComposer?.addEventListener('click', () => {
            alert.textContent = '';
            composer?.removeAttribute('hidden');
        });
        closeComposer?.addEventListener('click', () => {
            alert.textContent = '';
            composer?.setAttribute('hidden', '');
        });
        search?.addEventListener('input', paint);
        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (form.dataset.pending === 'true') return;
            const data = new FormData(form);
            const title = `${data.get('title') || ''}`.trim();
            const content = `${data.get('content') || ''}`.trim();
            const policyAccepted = data.get('policyAccepted') === 'on';
            if (!title || !content || title.length > maxTitleLength || content.length > maxContentLength) {
                alert.textContent = `Título máximo ${maxTitleLength} caracteres y mensaje máximo ${maxContentLength} caracteres`;
                return;
            }
            if (!policyAccepted) {
                alert.textContent = 'Debes aceptar las normas del foro y la política de privacidad';
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            const previousText = button?.textContent || 'Publicar';
            form.dataset.pending = 'true';
            form.setAttribute('aria-busy', 'true');
            if (button) {
                button.disabled = true;
                button.textContent = 'Publicando...';
            }
            try {
                const created = await api('/api/forum', {
                    method: 'POST',
                    body: JSON.stringify({
                        category: data.get('category'),
                        title,
                        content,
                        policyAccepted,
                        clientRequestId: crypto.randomUUID()
                    })
                });
                form.reset();
                bindCharCounter(form.elements.title, form.querySelector('[data-counter-for="title"]'), maxTitleLength);
                bindTextAreaTools(form.elements.content, form.querySelector('[data-counter-for="content"]'), maxContentLength);
                composer?.setAttribute('hidden', '');
                alert.textContent = '';
                highlightedPostId = `${created.id}`;
                await load();
            } catch (error) {
                alert.textContent = shortAlert(error.message);
            } finally {
                form.dataset.pending = 'false';
                form.removeAttribute('aria-busy');
                if (button) {
                    button.disabled = false;
                    button.textContent = previousText;
                }
            }
        });
        bindCharCounter(form?.elements.title, form?.querySelector('[data-counter-for="title"]'), maxTitleLength);
        bindTextAreaTools(form?.elements.content, form?.querySelector('[data-counter-for="content"]'), maxContentLength);
        await load();
    };

    const bindContact = () => {
        const form = document.getElementById('contactForm');
        const alert = document.getElementById('contactAlert');
        const maxSubjectLength = 20;
        const maxMessageLength = 1000;
        let pending = false;
        let clientRequestId = null;
        bindCharCounter(form?.elements.subject, form?.querySelector('[data-counter-for="subject"]'), maxSubjectLength);
        bindTextAreaTools(form?.elements.message, form?.querySelector('[data-counter-for="message"]'), maxMessageLength);
        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (pending) return;
            const data = new FormData(form);
            const topic = `${data.get('topic') || ''}`.trim();
            const subject = `${data.get('subject') || ''}`.trim();
            const message = `${data.get('message') || ''}`.trim();
            const policyAccepted = data.get('policyAccepted') === 'on';
            if (!topic) {
                alert.textContent = 'Selecciona un tema';
                return;
            }
            if (!subject || !message || subject.length > maxSubjectLength || message.length > maxMessageLength) {
                alert.textContent = 'Asunto máximo 20 caracteres y mensaje máximo 1000 caracteres';
                return;
            }
            if (!policyAccepted) {
                alert.textContent = 'Debes aceptar la política de privacidad y las normas de contacto';
                return;
            }
            pending = true;
            clientRequestId = clientRequestId || crypto.randomUUID();
            const button = form.querySelector('button[type="submit"]');
            const previousText = button?.textContent || 'Enviar';
            if (button) {
                button.disabled = true;
                button.textContent = 'Enviando...';
            }
            form.setAttribute('aria-busy', 'true');
            try {
                const response = await api('/api/contact', {
                    method: 'POST',
                    body: JSON.stringify({ topic, subject, message, policyAccepted, clientRequestId })
                });
                form.reset();
                clientRequestId = null;
                bindCharCounter(form.elements.subject, form.querySelector('[data-counter-for="subject"]'), maxSubjectLength);
                bindTextAreaTools(form.elements.message, form.querySelector('[data-counter-for="message"]'), maxMessageLength);
                alert.textContent = response.duplicate ? 'Solicitud ya procesada' : (response.mailSent ? 'Enviado' : 'Guardado');
            } catch (error) {
                alert.textContent = shortAlert(error.message);
            } finally {
                pending = false;
                form.removeAttribute('aria-busy');
                if (button) {
                    button.disabled = false;
                    button.textContent = previousText;
                }
            }
        });
    };

    loadMe().then((user) => {
        if (page === 'favorites') renderFavorites();
        bindProfileForms(user);
        bindForum();
        bindContact();
    }).catch(() => {
        window.location.href = '/login.html';
    });
});
