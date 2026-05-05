/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description Gestiona login, registro, pestañas y validacion de contrasenas
 */
document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const loginAlert = document.getElementById('loginAlert');
    const registerAlert = document.getElementById('registerAlert');
    const redirect = new URLSearchParams(window.location.search).get('redirect') || '/account.html';

    const postJson = async (url, body) => {
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'No se pudo completar la operación');
        return data;
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
    activatePanel(window.location.hash === '#registro' ? 'registerPanel' : 'loginPanel');

    registerForm?.querySelectorAll('[type="password"]').forEach((input) => {
        input.addEventListener('input', () => updateRules(registerForm));
    });
    updateRules(registerForm);

    loginForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        loginAlert.textContent = 'Comprobando credenciales...';
        const form = new FormData(loginForm);
        try {
            await postJson('/api/auth/login', { email: form.get('email'), password: form.get('password') });
            window.location.href = redirect;
        } catch (error) {
            loginAlert.textContent = error.message;
        }
    });

    registerForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        registerAlert.textContent = 'Creando cuenta...';
        const form = new FormData(registerForm);
        const password = `${form.get('password') || ''}`;
        const confirmPassword = `${form.get('confirmPassword') || ''}`;
        if (password !== confirmPassword) {
            registerAlert.textContent = 'Las contraseñas no coinciden.';
            return;
        }
        if (!isStrongPassword(password)) {
            registerAlert.textContent = 'Completa todos los requisitos de seguridad.';
            return;
        }
        try {
            await postJson('/api/auth/register', {
                name: form.get('name'),
                email: form.get('email'),
                password,
                acceptPolicies: form.get('acceptPolicies') === 'on',
                cookieConsent: form.get('cookieConsent') === 'on'
            });
            window.location.href = redirect;
        } catch (error) {
            registerAlert.textContent = error.message;
        }
    });
});
