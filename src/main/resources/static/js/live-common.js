/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.3
 * @created 23-05-2026
 * @modified 24-05-2026
 * @description Útilidades comunes para páginas de Fórmula 1 En Vivo con
 *              refresco controlado, AbortController y formato seguro
 */
class RaceStreamLiveCommon {
    static STATUS_CACHE_KEY = 'rs-live-status-current-v1';
    static STATUS_CACHE_MAX_AGE = 15 * 60 * 1000;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Inicializa controladores de red y temporizadores live
     */
    constructor() {
        this.controllers = new Map();
        this.timers = new Map();
        this.renderCachedStatus();
    }

    async fetchBlock(name, url, fallback = null, options = {}) {
        this.controllers.get(name)?.abort();
        const controller = new AbortController();
        this.controllers.set(name, controller);
        const data = await window.RaceStreamApi.fetchJson(url, fallback, {
            attempts: options.attempts ?? 2,
            retryEmpty: false,
            allowStaleOnError: options.allowStaleOnError ?? true,
            signal: controller.signal
        });
        if (this.controllers.get(name) === controller) {
            this.controllers.delete(name);
        }
        if (name === 'status' && data && typeof data === 'object') {
            this.writeStatusCache(data);
        }
        return data;
    }

    schedule(name, callback, intervalMs, hiddenMultiplier = 4) {
        const run = async () => {
            await callback();
            const delay = document.hidden ? intervalMs * hiddenMultiplier : intervalMs;
            this.timers.set(name, window.setTimeout(run, delay));
        };
        window.clearTimeout(this.timers.get(name));
        run();
    }

    stop() {
        this.controllers.forEach((controller) => controller.abort());
        this.timers.forEach((timer) => window.clearTimeout(timer));
        this.controllers.clear();
        this.timers.clear();
    }

    renderStatusCard(data) {
        const card = document.getElementById('liveStatusCard');
        if (!card) return;
        const session = data?.session || {};
        const meeting = session.meeting_official_name || session.meeting_name || session.location || session.country_name || 'Gran Premio por confirmar';
        const sessionLabel = data?.status === 'Sesión finalizada'
            ? `${this.translateSessionName(session.session_name)} finalizada`
            : this.translateSessionName(session.session_name);
        const cached = data?.fromCache || data?.fromLastValid
            ? '<p class="rs-live-session-summary__notice">Mostrando el último dato válido mientras se confirma una actualización nueva.</p>'
            : '';
        const metric = data?.sessionMetric?.label
            ? `<div class="rs-live-metric"><span class="rs-live-metric__label">${this.escape(data.sessionMetric.label)}</span><strong>${this.escape(data.sessionMetric.value || '—')}</strong></div>`
            : '';
        card.innerHTML = `
            <div class="rs-live-session-summary">
                <div class="rs-live-session-summary__gp">
                    <span class="rs-live-metric__label">Gran Premio</span>
                    <strong>${this.escape(meeting)}</strong>
                </div>
                <div class="rs-live-session-summary__row">
                    <div class="rs-live-metric"><span class="rs-live-metric__label">Sesión</span><strong>${this.escape(sessionLabel)}</strong></div>
                    <div class="rs-live-metric"><span class="rs-live-metric__label">Inicio</span><strong>${this.formatDateTime(session.date_start)}</strong></div>
                    <div class="rs-live-metric"><span class="rs-live-metric__label">Fin teórico</span><strong>${this.formatDateTime(session.date_end)}</strong></div>
                    ${metric}
                </div>
                ${cached}
            </div>
        `;
    }

    renderCachedStatus() {
        try {
            const cached = JSON.parse(localStorage.getItem(RaceStreamLiveCommon.STATUS_CACHE_KEY) || 'null');
            if (!cached?.data || Date.now() - Number(cached.savedAt || 0) > RaceStreamLiveCommon.STATUS_CACHE_MAX_AGE) {
                return;
            }
            this.renderStatusCard({ ...cached.data, fromCache: true });
        } catch {
            localStorage.removeItem(RaceStreamLiveCommon.STATUS_CACHE_KEY);
        }
    }

    writeStatusCache(data) {
        try {
            localStorage.setItem(RaceStreamLiveCommon.STATUS_CACHE_KEY, JSON.stringify({
                savedAt: Date.now(),
                data
            }));
        } catch {
            localStorage.removeItem(RaceStreamLiveCommon.STATUS_CACHE_KEY);
        }
    }

    setGeneratedAt(value) {
        const node = document.getElementById('liveGeneratedAt');
        if (node) {
            node.textContent = `Última actualización: ${this.formatDateTime(value)}`;
        }
    }

    renderStaleFlags(data) {
        document.querySelectorAll('[data-live-stale]').forEach((node) => {
            const key = node.dataset.liveStale;
            const stale = Boolean(data?.stale?.[key]) || Boolean(data?.fromLastValid);
            node.textContent = stale ? 'Dato anterior' : '';
        });
    }

    safeArray(value) {
        return Array.isArray(value) ? value : [];
    }

    last(value) {
        const rows = this.safeArray(value);
        return rows.length ? rows[rows.length - 1] : null;
    }

    empty(message) {
        return `<p class="empty-state">${this.escape(message)}</p>`;
    }

    renderRows(items, mapper) {
        return `<div class="rs-live-list">${items.map((item, index) => `<div class="rs-live-item">${mapper(item, index)}</div>`).join('')}</div>`;
    }

    formatDateTime(value) {
        const date = new Date(value);
        return Number.isFinite(date.getTime())
            ? date.toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' })
            : '—';
    }

    formatTime(value) {
        const date = new Date(value);
        return Number.isFinite(date.getTime())
            ? date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
            : '—';
    }

    translateSessionName(name) {
        return ({
            'Practice 1': 'Libres 1',
            'Practice 2': 'Libres 2',
            'Practice 3': 'Libres 3',
            'Free Practice 1': 'Libres 1',
            'Free Practice 2': 'Libres 2',
            'Free Practice 3': 'Libres 3',
            Qualifying: 'Clasificación',
            Race: 'Carrera',
            Sprint: 'Sprint',
            'Sprint Qualifying': 'Clasif. sprint',
            'Sprint Shootout': 'Clasif. sprint'
        })[name] || name || 'Sesión por confirmar';
    }

    safeUrl(value) {
        try {
            const url = new URL(value || '', window.location.origin);
            return ['http:', 'https:'].includes(url.protocol) ? this.escape(url.href) : '';
        } catch {
            return '';
        }
    }

    escape(value) {
        return window.RaceStreamApi?.escape
            ? window.RaceStreamApi.escape(value)
            : `${value ?? ''}`.replace(/[&<>"']/g, (character) => ({
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#039;'
            }[character]));
    }
}

window.RaceStreamLiveCommon = RaceStreamLiveCommon;
