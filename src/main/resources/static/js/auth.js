/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.3
 * @created 05-05-2026
 * @modified 07-05-2026
 * @description Gestiona login, registro, recuperacion, pestañas y validacion de contrasenas
 */
document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const resetRequestForm = document.getElementById('resetRequestForm');
    const resetConfirmForm = document.getElementById('resetConfirmForm');
    const loginAlert = document.getElementById('loginAlert');
    const registerAlert = document.getElementById('registerAlert');
    const params = new URLSearchParams(window.location.search);
    const redirect = params.get('redirect') || '/account.html';
    const resetToken = params.get('resetToken');

    const postJson = async (url, body) => {
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
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
        if (text.includes('pol')) return 'Políticas';
        if (text.includes('nombre')) return 'Nombre usado';
        if (text.includes('email')) return 'Email usado';
        if (text.includes('credencial') || text.includes('bad credentials')) return 'Datos erróneos';
        return value && `${value}`.length <= 18 ? value : 'Error';
    };

    const isStrongPassword = (password) =>
        password.length >= 8
        && /[a-z]/.test(password)
        && /[A-Z]/.test(password)
        && /\d/.test(password)
        && /[^A-Za-z0-9]/.test(password);

    const updateRules = (form) => {
        const password = form?.querySelector('[name="password"]')?.value || '';
        form?.querySelectorAll('[data-password-rule]').forEach((rule) => {
            const ok = {
                length: password.length >= 8,
                lower: /[a-z]/.test(password),
                upper: /[A-Z]/.test(password),
                number: /\d/.test(password),
                symbol: /[^A-Za-z0-9]/.test(password)
            }[rule.dataset.passwordRule];
            rule.classList.toggle('rs-password-rule--ok', ok);
        });
    };

    const activatePanel = (id) => {
        document.querySelectorAll('.rs-auth-tab').forEach((tab) => {
            tab.classList.toggle('rs-auth-tab--active', tab.dataset.authTarget === id);
        });
        document.querySelectorAll('.rs-auth-panel').forEach((panel) => {
            panel.classList.toggle('rs-auth-panel--active', panel.id === id);
        });
    };

    document.querySelectorAll('[data-auth-target]').forEach((tab) => {
        tab.addEventListener('click', () => activatePanel(tab.dataset.authTarget));
    });
    activatePanel(resetToken ? 'resetPanel' : window.location.hash === '#registro' ? 'registerPanel' : 'loginPanel');

    if (resetToken && resetConfirmForm) {
        if (resetRequestForm) resetRequestForm.hidden = true;
        resetConfirmForm.hidden = false;
        resetConfirmForm.querySelector('[name="token"]').value = resetToken;
    }

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

    registerForm?.querySelectorAll('[type="password"]').forEach((input) => {
        input.addEventListener('input', () => updateRules(registerForm));
    });
    updateRules(registerForm);

    loginForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        loginAlert.textContent = 'Comprobando';
        const form = new FormData(loginForm);
        try {
            await postJson('/api/auth/login', { email: form.get('email'), password: form.get('password') });
            sessionStorage.removeItem('rs-auth-me');
            window.location.href = redirect;
        } catch (error) {
            loginAlert.textContent = shortAlert(error.message);
        }
    });

    resetRequestForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const alert = document.getElementById('resetRequestAlert');
        const form = new FormData(resetRequestForm);
        alert.textContent = 'Preparando';
        try {
            const response = await postJson('/api/auth/password-reset/request', { email: form.get('email') });
            alert.textContent = response.blocked ? 'Usuario bloqueado' : response.mailSent ? 'Correo enviado' : 'SMTP desactivado';
        } catch (error) {
            alert.textContent = shortAlert(error.message);
        }
    });

    resetConfirmForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const alert = document.getElementById('resetConfirmAlert');
        const form = new FormData(resetConfirmForm);
        const password = `${form.get('password') || ''}`;
        const confirmPassword = `${form.get('confirmPassword') || ''}`;
        if (password !== confirmPassword) {
            alert.textContent = 'No coinciden';
            return;
        }
        if (!isStrongPassword(password)) {
            alert.textContent = 'Requisitos';
            return;
        }
        alert.textContent = 'Guardando';
        try {
            await postJson('/api/auth/password-reset/confirm', { token: form.get('token'), password });
            alert.textContent = 'Actualizada';
            window.history.replaceState({}, '', '/login.html');
        } catch (error) {
            alert.textContent = shortAlert(error.message);
        }
    });

    registerForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        registerAlert.textContent = 'Creando';
        const form = new FormData(registerForm);
        const password = `${form.get('password') || ''}`;
        const confirmPassword = `${form.get('confirmPassword') || ''}`;
        if (password !== confirmPassword) {
            registerAlert.textContent = 'No coinciden';
            return;
        }
        if (!isStrongPassword(password)) {
            registerAlert.textContent = 'Requisitos';
            return;
        }
        try {
            await postJson('/api/auth/register', {
                name: form.get('name'),
                email: form.get('email'),
                password,
                acceptPolicies: form.get('acceptPolicies') === 'on'
            });
            sessionStorage.removeItem('rs-auth-me');
            window.location.href = redirect;
        } catch (error) {
            registerAlert.textContent = shortAlert(error.message);
        }
    });
});
