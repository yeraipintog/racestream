/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.0
 * @created 12-05-2026
 * @modified 27-05-2026
 * @description Cliente común RaceStream para fetch, reintentos, estados de carga, sesión pública, bloqueo de filtros y caché local segura
 */
class RaceStreamApiClient {

    static CACHE_VERSION = '2026-05-13-v3';
    static CACHE_VERSION_KEY = 'rs-cache-version';

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 12-05-2026
     * @modified 13-05-2026
     * @description Inicializa estado de la última petición y descarta caché antigua de datos API
     */
    constructor() {
        this.lastStatus = { state: 'idle', cached: false, partialData: false, url: '' };
        this.currentUserPromise = null;
        this.purgeOldApiCache();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.1
     * @created 12-05-2026
     * @modified 12-05-2026
     * @description Lee JSON con reintentos, cancelación y fallback stale sin cachear vacíos temporales
     * @param {string} url URL
     * @param {*} fallback Valor alternativo
     * @param {Object} options Opciones de carga
     * @returns {Promise<*>} JSON confirmado
     */
    async fetchJson(url, fallback = null, options = {}) {
        const attempts = options.attempts ?? 3;
        const delayBase = options.delayBase ?? 220;
        const cacheKey = `rs-cache:${RaceStreamApiClient.CACHE_VERSION}:${url}`;
        this.lastStatus = { state: 'loading', cached: false, partialData: false, url };
        let staleAllowed = options.allowStaleOnError ?? true;

        for (let attempt = 0; attempt < attempts; attempt++) {
            try {
                const response = await fetch(url, {
                    cache: 'no-store',
                    signal: options.signal,
                    headers: { 'Cache-Control': 'no-cache', ...(options.headers || {}) }
                });
                if (response.ok) {
                    const data = await response.json();
                    if (options.retryEmpty && this.isEmptyArray(data) && attempt < attempts - 1) {
                        this.lastStatus = { state: 'loading', cached: false, partialData: true, url };
                        await this.wait(delayBase * (attempt + 1));
                        continue;
                    }
                    if (this.isEmptyArray(data)) {
                        this.removeCache(cacheKey);
                    } else {
                        this.writeCache(cacheKey, data);
                    }
                    this.lastStatus = { state: this.isEmptyArray(data) ? 'empty-real' : 'loaded', cached: false, partialData: false, url };
                    return data;
                }
                this.lastStatus = { state: 'error', cached: false, partialData: false, url };
            } catch {
                if (options.signal?.aborted) {
                    this.lastStatus = { state: 'cancelled', cached: false, partialData: false, url };
                    return fallback;
                }
                staleAllowed = true;
                this.lastStatus = { state: 'error', cached: false, partialData: false, url };
            }

            if (attempt < attempts - 1) {
                await this.wait(delayBase * (attempt + 1));
            }
        }

        const cached = staleAllowed ? this.readCache(cacheKey) : null;
        if (cached !== null) {
            this.lastStatus = { state: 'stale-fallback', cached: true, partialData: true, url };
            return cached;
        }
        this.lastStatus = { state: 'error', cached: false, partialData: false, url };
        return fallback;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Elimina solo caché API antigua manteniendo favoritos, preferencias y notificaciones
     */
    purgeOldApiCache() {
        try {
            if (localStorage.getItem(RaceStreamApiClient.CACHE_VERSION_KEY) === RaceStreamApiClient.CACHE_VERSION) {
                return;
            }
            Object.keys(localStorage)
                .filter((key) => key.startsWith('rs-cache:') || key.startsWith('rs-cache-version:'))
                .forEach((key) => localStorage.removeItem(key));
            localStorage.setItem(RaceStreamApiClient.CACHE_VERSION_KEY, RaceStreamApiClient.CACHE_VERSION);
        } catch {
            /* La caché del navegador nunca debe bloquear la carga de datos. */
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Lee un array JSON confirmado
     * @param {string} url URL
     * @param {Object} options Opciones de carga
     * @returns {Promise<Array>} Array seguro
     */
    async fetchArray(url, options = {}) {
        const data = await this.fetchJson(url, [], { retryEmpty: true, ...options });
        return Array.isArray(data) ? data : [];
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Devuelve la sesión actual reutilizando una única llamada por carga de página
     * @returns {Promise<Object>} Usuario autenticado o estado anónimo
     */
    async getCurrentUser() {
        if (!this.currentUserPromise) {
            this.currentUserPromise = fetch('/api/auth/me', {
                cache: 'no-store',
                headers: { 'Cache-Control': 'no-cache' }
            })
                .then((response) => response.ok ? response.json() : { authenticated: false })
                .catch(() => ({ authenticated: false }));
        }
        return this.currentUserPromise;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Devuelve la temporada actual del calendario del cliente
     * @returns {number} Temporada actual
     */
    currentSeason() {
        return new Date().getFullYear();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Resuelve el acceso de temporada para bloquear históricos en
     *              sesiones públicas sin esperar a un error del backend
     * @param {number|null} requestedYear Temporada solicitada en URL o selector
     * @returns {Promise<Object>} Estado de acceso y temporada permitida
     */
    async resolveSeasonAccess(requestedYear = null) {
        const user = await this.getCurrentUser();
        const currentYear = this.currentSeason();
        const requested = Number(requestedYear);
        const validRequested = Number.isInteger(requested) && requested >= 1950 ? requested : currentYear;
        const authenticated = Boolean(user?.authenticated);
        return {
            authenticated,
            locked: !authenticated,
            currentYear,
            year: authenticated ? validRequested : currentYear
        };
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Aplica estado visual y accesible a un selector de temporada
     *              bloqueado para invitados
     * @param {HTMLSelectElement|null} select Selector de temporada
     * @param {boolean} locked Indica si el usuario es público
     */
    setSeasonFilterLocked(select, locked) {
        if (!select) return;
        select.disabled = Boolean(locked);
        if (locked) {
            select.setAttribute('aria-disabled', 'true');
        } else {
            select.removeAttribute('aria-disabled');
        }
        select.title = locked ? 'Inicia sesión para consultar temporadas históricas.' : '';
        select.closest('.rs-select-wrap')?.classList.toggle('rs-select-wrap--locked', Boolean(locked));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Genera un bloque reutilizable de reintento
     * @param {string} title Título
     * @param {string} message Mensaje
     * @param {string} buttonText Texto del botón
     * @returns {string} HTML seguro
     */
    retryButton(title, message, buttonText = 'Reintentar / Reiniciar página') {
        return `
            <div class="rs-api-retry empty-state" data-rs-state="retry">
                <strong>${this.escape(title)}</strong>
                <span>${this.escape(message)}</span>
                <button class="rs-button" type="button" onclick="window.location.reload()">${this.escape(buttonText)}</button>
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Lee caché local descartando arrays vacíos
     * @param {string} cacheKey Clave localStorage
     * @returns {*|null} Dato cacheado
     */
    readCache(cacheKey) {
        try {
            const cached = localStorage.getItem(cacheKey);
            if (!cached) return null;
            const parsed = JSON.parse(cached);
            if (this.isEmptyArray(parsed)) {
                localStorage.removeItem(cacheKey);
                return null;
            }
            if (parsed?.version !== RaceStreamApiClient.CACHE_VERSION) {
                localStorage.removeItem(cacheKey);
                return null;
            }
            return parsed.data;
        } catch {
            localStorage.removeItem(cacheKey);
            return null;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Guarda caché versionada solo cuando hay dato útil
     * @param {string} cacheKey Clave localStorage
     * @param {*} data Dato confirmado
     */
    writeCache(cacheKey, data) {
        try {
            localStorage.setItem(cacheKey, JSON.stringify({
                version: RaceStreamApiClient.CACHE_VERSION,
                savedAt: Date.now(),
                data
            }));
        } catch {
            this.removeCache(cacheKey);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @description Elimina una entrada de caché API concreta
     * @param {string} cacheKey Clave localStorage
     */
    removeCache(cacheKey) {
        try {
            localStorage.removeItem(cacheKey);
        } catch {
            /* Sin acción: localStorage puede estar bloqueado. */
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Comprueba si el dato es un array vacío
     * @param {*} value Valor
     * @returns {boolean} Resultado
     */
    isEmptyArray(value) {
        return Array.isArray(value) && !value.length;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Escapa texto antes de insertarlo como HTML
     * @param {*} value Valor
     * @returns {string} Texto seguro
     */
    escape(value) {
        return window.RaceStreamF1Assets?.escape
            ? window.RaceStreamF1Assets.escape(value)
            : `${value ?? ''}`.replace(/[&<>"']/g, (character) => ({
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#039;'
            }[character]));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Espera no bloqueante entre reintentos
     * @param {number} milliseconds Milisegundos
     * @returns {Promise<void>} Promesa de espera
     */
    wait(milliseconds) {
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }
}

window.RaceStreamApi = window.RaceStreamApi || new RaceStreamApiClient();
