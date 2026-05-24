/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.8
 * @created 05-05-2026
 * @modified 22-05-2026
 * @description Gestiona login, registro, recuperacion, hashes de panel sin scroll intermedio y validacion
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
    const PASSWORD_REQUIREMENTS_MESSAGE = 'La contraseña no cumple los requisitos';
    const PASSWORD_MISMATCH_MESSAGE = 'Las contraseñas no coinciden';
    const SAME_PASSWORD_MESSAGE = 'La contraseña introducida es la registrada actualmente';

    const postJson = async (url, body) => {
        const response = await fetch(url, {
            method: 'POST',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' },
            body: JSON.stringify(body)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            const error = new Error(data.error || 'Error');
            error.field = data.field || '';
            error.status = response.status;
            error.error = data.error || 'Error';
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
        if (text.includes('requisito') || text.includes('caracter')) return PASSWORD_REQUIREMENTS_MESSAGE;
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

    const bindFieldErrorReset = (form) => {
        form?.querySelectorAll('input').forEach((input) => {
            input.addEventListener('input', () => clearFieldError(input));
        });
    };

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

    const panelHash = {
        loginPanel: '#login',
        registerPanel: '#registro',
        resetPanel: '#reset'
    };
    const panelFromHash = () => ({
        '#registro': 'registerPanel',
        '#register': 'registerPanel',
        '#login': 'loginPanel',
        '#reset': 'resetPanel'
    }[window.location.hash.toLowerCase()] || (resetToken ? 'resetPanel' : 'loginPanel'));

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Mantiene login y registro visibles desde el inicio de la página tras cambiar de panel
     */
    const scrollToPageTop = () => {
        window.setTimeout(() => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }, 40);
    };

    const activatePanel = (id, options = {}) => {
        document.querySelectorAll('.rs-auth-tab').forEach((tab) => {
            tab.classList.toggle('rs-auth-tab--active', tab.dataset.authTarget === id);
        });
        document.querySelectorAll('.rs-auth-panel').forEach((panel) => {
            panel.classList.toggle('rs-auth-panel--active', panel.id === id);
        });
        if (options.updateHash && panelHash[id] && window.location.hash !== panelHash[id]) {
            window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}${panelHash[id]}`);
        }
        if (options.scroll) {
            scrollToPageTop();
        }
    };

    document.querySelectorAll('[data-auth-target]').forEach((tab) => {
        tab.addEventListener('click', () => activatePanel(tab.dataset.authTarget, { updateHash: true, scroll: true }));
    });
    window.addEventListener('hashchange', () => activatePanel(panelFromHash(), { scroll: true }));
    activatePanel(panelFromHash());
    bindFieldErrorReset(loginForm);
    bindFieldErrorReset(registerForm);
    bindFieldErrorReset(resetRequestForm);
    bindFieldErrorReset(resetConfirmForm);

    if (resetToken && resetConfirmForm) {
        if (resetRequestForm) resetRequestForm.hidden = true;
        resetConfirmForm.hidden = true;
        resetConfirmForm.querySelector('[name="token"]').value = resetToken;
        const tokenAlert = document.getElementById('resetTokenAlert');
        if (tokenAlert) tokenAlert.textContent = 'Comprobando enlace';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Comprueba que el enlace de recuperacion no este caducado antes de mostrar el formulario
     */
    const validateResetToken = async () => {
        if (!resetToken || !resetConfirmForm) return;
        const tokenAlert = document.getElementById('resetTokenAlert');
        const alert = document.getElementById('resetConfirmAlert');
        try {
            const response = await fetch(`/api/auth/password-reset/validate?token=${encodeURIComponent(resetToken)}`, {
                cache: 'no-store',
                headers: { 'Cache-Control': 'no-cache' }
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.error || 'Enlace caducado');
            if (tokenAlert) tokenAlert.textContent = '';
            alert.textContent = '';
            resetConfirmForm.hidden = false;
        } catch (error) {
            if (tokenAlert) tokenAlert.textContent = '';
            resetConfirmForm.hidden = true;
            resetConfirmForm.reset();
            updateRules(resetConfirmForm);
            if (resetRequestForm) resetRequestForm.hidden = false;
            const requestAlert = document.getElementById('resetRequestAlert');
            requestAlert.textContent = 'Enlace caducado';
            window.history.replaceState({}, '', '/login.html#reset');
        }
    };
    validateResetToken();

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
        input.addEventListener('input', () => {
            clearFieldError(input);
            updateRules(registerForm);
        });
    });
    resetConfirmForm?.querySelectorAll('[type="password"]').forEach((input) => {
        input.addEventListener('input', () => {
            clearFieldError(input);
            updateRules(resetConfirmForm);
        });
    });
    updateRules(registerForm);
    updateRules(resetConfirmForm);

    loginForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        clearFormFieldErrors(loginForm);
        loginAlert.textContent = 'Comprobando';
        const form = new FormData(loginForm);
        try {
            await postJson('/api/auth/login', { email: form.get('email'), password: form.get('password') });
            sessionStorage.removeItem('rs-auth-me');
            window.location.href = redirect;
        } catch (error) {
            loginAlert.textContent = '';
            if (error.field === 'email') {
                setFieldError(loginForm.querySelector('[name="email"]'), error.error || 'Usuario o email no registrado');
            } else if (error.field === 'password') {
                setFieldError(loginForm.querySelector('[name="password"]'), error.error || 'Contraseña incorrecta');
            } else {
                loginAlert.textContent = shortAlert(error.message);
            }
        }
    });

    resetRequestForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const alert = document.getElementById('resetRequestAlert');
        const form = new FormData(resetRequestForm);
        clearFormFieldErrors(resetRequestForm);
        alert.textContent = 'Preparando';
        try {
            const response = await postJson('/api/auth/password-reset/request', { email: form.get('email') });
            alert.textContent = response.blocked ? 'Usuario bloqueado' : response.mailSent ? 'Correo enviado' : 'SMTP desactivado';
        } catch (error) {
            if (error.field === 'email') {
                alert.textContent = '';
                setFieldError(resetRequestForm.querySelector('[name="email"]'), error.error || 'Email no registrado');
            } else {
                alert.textContent = shortAlert(error.message);
            }
        }
    });

    resetConfirmForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const alert = document.getElementById('resetConfirmAlert');
        const form = new FormData(resetConfirmForm);
        const password = `${form.get('password') || ''}`;
        const confirmPassword = `${form.get('confirmPassword') || ''}`;
        clearFormFieldErrors(resetConfirmForm);
        if (password !== confirmPassword) {
            alert.textContent = '';
            setFieldError(resetConfirmForm.querySelector('[name="confirmPassword"]'), PASSWORD_MISMATCH_MESSAGE);
            return;
        }
        if (!isStrongPassword(password)) {
            alert.textContent = '';
            setFieldError(resetConfirmForm.querySelector('[name="password"]'), PASSWORD_REQUIREMENTS_MESSAGE);
            return;
        }
        alert.textContent = 'Guardando';
        try {
            await postJson('/api/auth/password-reset/confirm', { token: form.get('token'), password, confirmPassword });
            sessionStorage.removeItem('rs-auth-me');
            window.location.replace('/account.html');
        } catch (error) {
            const message = shortAlert(error.message);
            if (error.field === 'password' || message === PASSWORD_REQUIREMENTS_MESSAGE || message === SAME_PASSWORD_MESSAGE) {
                alert.textContent = '';
                setFieldError(resetConfirmForm.querySelector('[name="password"]'), message);
            } else if (message === PASSWORD_MISMATCH_MESSAGE) {
                alert.textContent = '';
                setFieldError(resetConfirmForm.querySelector('[name="confirmPassword"]'), message);
            } else {
                alert.textContent = message;
            }
        }
    });

    registerForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        registerAlert.textContent = 'Creando';
        const form = new FormData(registerForm);
        const password = `${form.get('password') || ''}`;
        const confirmPassword = `${form.get('confirmPassword') || ''}`;
        clearFormFieldErrors(registerForm);
        if (password !== confirmPassword) {
            registerAlert.textContent = '';
            setFieldError(registerForm.querySelector('[name="confirmPassword"]'), PASSWORD_MISMATCH_MESSAGE);
            return;
        }
        if (!isStrongPassword(password)) {
            registerAlert.textContent = '';
            setFieldError(registerForm.querySelector('[name="password"]'), PASSWORD_REQUIREMENTS_MESSAGE);
            return;
        }
        try {
            await postJson('/api/auth/register', {
                name: form.get('name'),
                email: form.get('email'),
                password,
                confirmPassword,
                acceptPolicies: form.get('acceptPolicies') === 'on' || form.get('policyAccepted') === 'on'
            });
            sessionStorage.removeItem('rs-auth-me');
            window.location.href = redirect;
        } catch (error) {
            const message = shortAlert(error.message);
            if (error.field === 'name') {
                registerAlert.textContent = '';
                setFieldError(registerForm.querySelector('[name="name"]'), error.error || 'Nombre de usuario ya registrado');
            } else if (error.field === 'email') {
                registerAlert.textContent = '';
                setFieldError(registerForm.querySelector('[name="email"]'), error.error || 'Correo electrónico ya registrado');
            } else if (error.field === 'password' || message === PASSWORD_REQUIREMENTS_MESSAGE) {
                registerAlert.textContent = '';
                setFieldError(registerForm.querySelector('[name="password"]'), message);
            } else if (error.field === 'confirmPassword' || message === PASSWORD_MISMATCH_MESSAGE) {
                registerAlert.textContent = '';
                setFieldError(registerForm.querySelector('[name="confirmPassword"]'), message);
            } else {
                registerAlert.textContent = message;
            }
        }
    });
});
