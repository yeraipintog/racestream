/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.2
 * @created 06-05-2026
 * @modified 06-05-2026
 * @description Gestiona usuarios, bloqueos, contacto y foro desde el panel privado de administracion
 */
document.addEventListener('DOMContentLoaded', () => {
    const summary = document.getElementById('adminSummary');
    const users = document.getElementById('adminUsers');
    const blockedEmails = document.getElementById('adminBlockedEmails');
    const userSearchForm = document.getElementById('adminUserSearchForm');
    const userSearchInput = userSearchForm?.querySelector('[name="email"]');
    const messages = document.getElementById('adminMessages');
    const forumPosts = document.getElementById('adminForumPosts');

    const escape = (value) => `${value ?? ''}`
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');

    const api = async (url, options = {}) => {
        const response = await fetch(url, {
            cache: 'no-store',
            headers: options.body ? { 'Content-Type': 'application/json' } : undefined,
            ...options
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'No tienes permisos de administrador');
        return data;
    };

    const formatDate = (value) => value
        ? new Date(value).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
        : '-';

    const usersUrl = () => {
        const query = `${userSearchInput?.value || ''}`.trim();
        return `/api/admin/users${query ? `?query=${encodeURIComponent(query)}` : ''}`;
    };

    const renderSummary = (data) => {
        const labels = {
            users: 'Usuarios',
            contactMessages: 'Mensajes',
            forumPosts: 'Foro',
            favorites: 'Favoritos'
        };
        summary.innerHTML = Object.entries(labels).map(([key, label]) => `
            <article>
                <span>${label}</span>
                <strong>${Number(data[key] || 0).toLocaleString('es-ES')}</strong>
            </article>
        `).join('');
    };

    const renderUsers = (rows) => {
        users.innerHTML = rows.length ? rows.map((user) => `
            <article class="rs-admin-user-card ${user.blocked ? 'rs-admin-user-card--blocked' : ''}" data-user-id="${escape(user.id)}">
                <div class="rs-admin-card__body">
                    <strong>${escape(user.name)}</strong>
                    <span>${escape(user.email)} · ${escape(user.provider)} · ${formatDate(user.createdAt)}</span>
                </div>
                <div class="rs-admin-card__actions">
                    ${user.blocked ? '' : '<button class="rs-button" type="button" data-user-block>Bloquear</button>'}
                    <button class="rs-button rs-button--danger" type="button" data-user-delete>Eliminar</button>
                </div>
            </article>
        `).join('') : '<p class="empty-state">No hay usuarios registrados.</p>';
    };

    const renderBlockedEmails = (rows) => {
        blockedEmails.innerHTML = rows.length ? rows.map((row) => `
            <article class="rs-admin-blocked-card">
                <div class="rs-admin-card__body">
                    <strong>${escape(row.email)}</strong>
                    <span>${escape(row.reason || 'Bloqueado')} · ${formatDate(row.createdAt)}</span>
                </div>
                <button type="button" aria-label="Desbloquear ${escape(row.email)}" data-unblock-email="${escape(row.email)}">Desbloquear</button>
            </article>
        `).join('') : '<p class="empty-state">No hay usuarios bloqueados.</p>';
    };

    const renderMessages = (rows) => {
        messages.innerHTML = rows.length ? rows.map((message) => `
            <article class="rs-admin-message-card" data-message-id="${escape(message.id)}">
                <div class="rs-admin-card__body">
                    <strong>${escape(message.subject)}</strong>
                    <span>${escape(message.userName)} · ${escape(message.userEmail)} · ${formatDate(message.createdAt)}</span>
                    <p>${escape(message.message)}</p>
                </div>
                <div class="rs-admin-card__actions">
                    ${message.completed
                        ? `<span class="rs-admin-status">Completado ${formatDate(message.completedAt)}</span>`
                        : '<button class="rs-button" type="button" data-contact-complete>Marcar completado</button>'}
                    <button class="rs-admin-delete-button" type="button" aria-label="Eliminar mensaje de contacto" data-contact-delete>×</button>
                </div>
            </article>
        `).join('') : '<p class="empty-state">No hay mensajes de contacto.</p>';
    };

    const renderForumPosts = (rows) => {
        forumPosts.innerHTML = rows.length ? rows.map((post) => `
            <article class="rs-admin-forum-card" data-post-id="${escape(post.id)}">
                <div class="rs-admin-card__body">
                    <strong>${escape(post.title)}</strong>
                    <span>${escape(post.category)} · ${escape(post.author)} · ${escape(post.authorEmail)} · ${formatDate(post.createdAt)} · ${Number(post.likes || 0)} likes</span>
                    <p>${escape(post.content)}</p>
                </div>
                <div class="rs-admin-card__actions">
                    <button class="rs-admin-delete-button" type="button" aria-label="Eliminar mensaje del foro" data-forum-delete>×</button>
                </div>
            </article>
        `).join('') : '<p class="empty-state">No hay mensajes del foro.</p>';
    };

    const init = async () => {
        summary.innerHTML = '<p class="loading-state">Cargando panel...</p>';
        const [summaryData, userRows, blockedRows, messageRows, forumRows] = await Promise.all([
            api('/api/admin/summary'),
            api(usersUrl()),
            api('/api/admin/blocked-emails'),
            api('/api/admin/contact-messages'),
            api('/api/admin/forum-posts')
        ]);
        renderSummary(summaryData);
        renderUsers(userRows);
        renderBlockedEmails(blockedRows);
        renderMessages(messageRows);
        renderForumPosts(forumRows);
    };

    const showError = (error) => {
        summary.innerHTML = `<p class="empty-state">${escape(error.message)}</p>`;
    };

    users.addEventListener('click', async (event) => {
        const card = event.target.closest('[data-user-id]');
        if (!card) return;
        const id = card.dataset.userId;
        try {
            if (event.target.closest('[data-user-block]') && confirm('¿Bloquear este correo?')) {
                await api(`/api/admin/users/${id}/block`, { method: 'POST' });
                await init();
            }
            if (event.target.closest('[data-user-delete]') && confirm('¿Eliminar este usuario y sus datos asociados?')) {
                await api(`/api/admin/users/${id}`, { method: 'DELETE' });
                await init();
            }
        } catch (error) {
            showError(error);
        }
    });

    blockedEmails.addEventListener('click', async (event) => {
        const email = event.target.dataset.unblockEmail;
        if (!email) return;
        try {
            await api(`/api/admin/blocked-emails/${encodeURIComponent(email)}`, { method: 'DELETE' });
            await init();
        } catch (error) {
            showError(error);
        }
    });

    messages.addEventListener('click', async (event) => {
        const card = event.target.closest('[data-message-id]');
        if (!card) return;
        try {
            if (event.target.closest('[data-contact-delete]') && confirm('¿Eliminar este mensaje de contacto?')) {
                await api(`/api/admin/contact-messages/${card.dataset.messageId}`, { method: 'DELETE' });
                await init();
            }
            if (event.target.closest('[data-contact-complete]')) {
                await api(`/api/admin/contact-messages/${card.dataset.messageId}/complete`, { method: 'PATCH' });
                await init();
            }
        } catch (error) {
            showError(error);
        }
    });

    forumPosts.addEventListener('click', async (event) => {
        const card = event.target.closest('[data-post-id]');
        if (!card || !event.target.closest('[data-forum-delete]') || !confirm('¿Eliminar este mensaje del foro?')) return;
        try {
            await api(`/api/admin/forum-posts/${card.dataset.postId}`, { method: 'DELETE' });
            await init();
        } catch (error) {
            showError(error);
        }
    });

    userSearchForm?.addEventListener('submit', (event) => event.preventDefault());

    let searchTimer = 0;
    userSearchInput?.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
            api(usersUrl()).then(renderUsers).catch(showError);
        }, 220);
    });

    init().catch((error) => {
        showError(error);
        users.innerHTML = '';
        blockedEmails.innerHTML = '';
        messages.innerHTML = '';
        forumPosts.innerHTML = '';
    });
});
