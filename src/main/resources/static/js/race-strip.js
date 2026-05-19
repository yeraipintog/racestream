/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.4.0
 * @created 04-05-2026
 * @modified 18-05-2026
 * @description Carga la franja comun de proximo GP mostrando pais, cache inmediata y refresco en segundo plano
 */
class RaceStreamRaceStrip {

    static CACHE_KEY = 'rs-race-strip-current-v1';
    static CACHE_MAX_AGE = 24 * 60 * 60 * 1000;
    static COUNTRY_LABELS_ES = {
        Australia: 'Australia',
        Austria: 'Austria',
        Azerbaijan: 'Azerbaiyán',
        Bahrain: 'Baréin',
        Belgium: 'Bélgica',
        Brazil: 'Brasil',
        Canada: 'Canadá',
        China: 'China',
        Hungary: 'Hungría',
        Italy: 'Italia',
        Japan: 'Japón',
        Mexico: 'México',
        Monaco: 'Mónaco',
        Netherlands: 'Países Bajos',
        Qatar: 'Catar',
        Singapore: 'Singapur',
        Spain: 'España',
        'Saudi Arabia': 'Arabia Saudí',
        'United Arab Emirates': 'Emiratos Árabes Unidos',
        'United Kingdom': 'Reino Unido',
        'United States': 'Estados Unidos'
    };

    constructor() {
        this.year = new Date().getFullYear();
        this.meetingApi = `/api/f1/schedule/current-or-next-meeting?year=${this.year}`;
        this.sessionsApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.title = document.getElementById('raceStripTitle');
        this.meta = document.getElementById('raceStripMeta');
        this.action = document.getElementById('raceStripAction');
        this.clocks = document.getElementById('raceStripClocks');
        this.flag = document.getElementById('raceStripFlag');
        this.meeting = null;
        this.sessions = [];
        this.clockTimer = null;
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Pinta cache valida al instante y actualiza datos confirmados sin bloquear la pagina
     */
    async init() {
        if (!this.title) return;
        if (!this.renderCachedMeeting()) {
            this.renderNeutral();
        }
        this.startClockTimer();
        await this.refresh();
    }

    renderCachedMeeting() {
        const cached = this.readCache();
        if (!cached || !this.isTrustedMeeting(cached.meeting, true)) {
            return false;
        }
        this.meeting = cached.meeting;
        this.sessions = Array.isArray(cached.sessions) ? cached.sessions : [];
        this.render();
        return true;
    }

    async refresh() {
        const meeting = await this.fetchJson(this.meetingApi, null, { attempts: 2, allowStaleOnError: false });
        if (!this.isTrustedMeeting(meeting, false)) {
            if (!this.meeting) this.renderNeutral('Calendario no disponible', 'Sin datos confirmados');
            return;
        }
        this.meeting = meeting;
        this.sessions = meeting.meeting_key
            ? await this.fetchJson(this.sessionsApi(meeting.meeting_key), [], { attempts: 2, retryEmpty: false })
            : [];
        if (!Array.isArray(this.sessions)) {
            this.sessions = [];
        }
        this.writeCache();
        this.render();
    }

    async fetchJson(url, fallback, options = {}) {
        if (window.RaceStreamApi) {
            return window.RaceStreamApi.fetchJson(url, fallback, options);
        }
        const attempts = options.attempts ?? 2;
        for (let attempt = 0; attempt < attempts; attempt++) {
            try {
                const response = await fetch(url, {
                    cache: 'no-store',
                    headers: { 'Cache-Control': 'no-cache' }
                });
                if (response.ok) return await response.json();
            } catch {
                await this.wait(180 * (attempt + 1));
            }
        }
        return fallback;
    }

    renderNeutral(title = 'Pr\u00f3ximo GP', meta = 'Actualizando calendario') {
        if (this.title) this.title.textContent = title;
        if (this.meta) this.meta.textContent = meta;
        if (this.action) this.action.innerHTML = '<span class="rs-race-strip__status">Sin datos</span>';
        if (this.clocks) this.clocks.textContent = '-';
        if (this.flag) this.flag.style.display = 'none';
    }

    render() {
        if (!this.isTrustedMeeting(this.meeting, false)) {
            this.renderNeutral('Calendario no disponible', 'Sin datos confirmados');
            return;
        }
        this.title.textContent = this.getMeetingCountryLabel(this.meeting);
        this.meta.textContent = this.formatDateRange(this.meeting.date_start, this.meeting.date_end);
        if (this.flag) {
            this.flag.style.display = this.meeting.country_flag ? 'inline-block' : 'none';
            if (this.meeting.country_flag) this.flag.src = this.meeting.country_flag;
        }
        this.renderAction();
        this.renderClocks();
    }

    renderClocks() {
        if (!this.meeting || !this.clocks) return;
        if (!this.clocks.querySelector('.rs-race-strip__clock-card')) {
            this.clocks.innerHTML = `
                <div class="rs-race-strip__clock-card">
                    <div class="rs-race-strip__clock-row">
                        <span class="rs-race-strip__clock-label">MI HORA</span>
                        <strong class="rs-race-strip__clock-value"></strong>
                    </div>
                    <div class="rs-race-strip__clock-divider"></div>
                    <div class="rs-race-strip__clock-row">
                        <span class="rs-race-strip__clock-subvalue">CIRCUITO</span>
                        <strong class="rs-race-strip__clock-track-value"></strong>
                    </div>
                </div>
            `;
        }
        this.clocks.querySelector('.rs-race-strip__clock-value').textContent = this.getNowTime();
        this.clocks.querySelector('.rs-race-strip__clock-track-value').textContent = this.getCircuitNowTime(this.meeting.gmt_offset);
    }

    renderAction() {
        if (!this.meeting || !this.action) return;
        const session = [...this.sessions]
            .filter((item) => !item?.is_cancelled)
            .sort((left, right) => new Date(left.date_start).getTime() - new Date(right.date_start).getTime())
            .find((item) => new Date(item.date_end).getTime() >= Date.now());
        const isLive = session && Date.now() >= new Date(session.date_start).getTime();
        if (isLive) {
            if (!this.action.querySelector('a')) this.action.innerHTML = '<a class="rs-button rs-button--primary" href="/live.html">En Vivo</a>';
            return;
        }
        const label = session
            ? `${this.translateSessionName(session.session_name)} en ${this.getCountdown(session.date_start)}`
            : this.getMeetingFallbackStatus();
        const status = this.action.querySelector('.rs-race-strip__status');
        if (status) {
            status.textContent = label;
        } else {
            this.action.innerHTML = `<span class="rs-race-strip__status">${label}</span>`;
        }
    }

    getMeetingFallbackStatus() {
        const start = new Date(this.meeting?.date_start).getTime();
        if (Number.isFinite(start) && start > Date.now()) {
            return `GP en ${this.getCountdown(this.meeting.date_start)}`;
        }
        return 'Sesiones por confirmar';
    }

    formatDateRange(startValue, endValue) {
        if (!startValue && !endValue) return '-';
        const start = new Date(startValue || endValue);
        const end = new Date(endValue || startValue);
        if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) return '-';
        const startMonth = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const endMonth = end.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const sameDay = start.getFullYear() === end.getFullYear()
            && start.getMonth() === end.getMonth()
            && start.getDate() === end.getDate();
        if (sameDay) return `${startMonth} ${start.getDate()}`;
        return startMonth === endMonth
            ? `${startMonth} ${start.getDate()}-${end.getDate()}`
            : `${startMonth} ${start.getDate()} - ${endMonth} ${end.getDate()}`;
    }

    isTrustedMeeting(meeting, fromCache) {
        if (!meeting || meeting.isMissingNode || (!meeting.meeting_name && !meeting.date_start)) {
            return false;
        }
        if (!fromCache) return true;
        const end = new Date(meeting.date_end || meeting.date_start).getTime();
        return !Number.isFinite(end) || end >= Date.now() - 60 * 60 * 1000;
    }

    readCache() {
        try {
            const cached = JSON.parse(localStorage.getItem(RaceStreamRaceStrip.CACHE_KEY) || 'null');
            if (!cached?.meeting || Date.now() - Number(cached.savedAt || 0) > RaceStreamRaceStrip.CACHE_MAX_AGE) {
                return null;
            }
            return cached;
        } catch {
            localStorage.removeItem(RaceStreamRaceStrip.CACHE_KEY);
            return null;
        }
    }

    writeCache() {
        try {
            localStorage.setItem(RaceStreamRaceStrip.CACHE_KEY, JSON.stringify({
                savedAt: Date.now(),
                meeting: this.meeting,
                sessions: this.sessions
            }));
        } catch {
            localStorage.removeItem(RaceStreamRaceStrip.CACHE_KEY);
        }
    }

    startClockTimer() {
        if (this.clockTimer) return;
        this.clockTimer = window.setInterval(() => {
            this.renderClocks();
            this.renderAction();
        }, 30000);
    }

    getNowTime() {
        return new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    }

    getCircuitNowTime(offset) {
        const date = new Date(Date.now() + this.parseOffset(offset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    parseOffset(offset) {
        const sign = `${offset}`.startsWith('-') ? -1 : 1;
        const [hours = '0', minutes = '0'] = `${offset}`.replace('+', '').replace('-', '').split(':');
        return sign * ((Number(hours) * 60) + Number(minutes));
    }

    getCountdown(value) {
        const diff = new Date(value).getTime() - Date.now();
        if (!Number.isFinite(diff) || diff <= 0) return 'En curso';
        const days = Math.floor(diff / 86400000);
        const hours = Math.floor((diff % 86400000) / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);
        return `${days}d ${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m`;
    }

    translateSessionName(name) {
        return {
            'Practice 1': 'Libres 1',
            'Practice 2': 'Libres 2',
            'Practice 3': 'Libres 3',
            'Free Practice 1': 'Libres 1',
            'Free Practice 2': 'Libres 2',
            'Free Practice 3': 'Libres 3',
            Qualifying: 'Clasificaci\u00f3n',
            Race: 'Carrera',
            Sprint: 'Sprint',
            'Sprint Qualifying': 'Clasif. sprint',
            'Sprint Shootout': 'Clasif. sprint'
        }[name] || name || 'Sesi\u00f3n';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Devuelve el pais visible del GP evitando nombres largos de Gran Premio
     * @param {Object} meeting GP confirmado
     * @returns {string} Pais del GP
     */
    getMeetingCountryLabel(meeting) {
        const country = `${meeting?.country_name || meeting?.jolpica_country || ''}`.trim();
        if (country) {
            return RaceStreamRaceStrip.COUNTRY_LABELS_ES[country] || country;
        }
        return this.simplifyMeetingName(meeting?.meeting_name) || 'Gran Premio';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Limpia prefijos habituales cuando solo existe el nombre largo del GP
     * @param {string} name Nombre del GP
     * @returns {string} Nombre compacto
     */
    simplifyMeetingName(name) {
        const value = `${name || ''}`.trim();
        if (!value) return '';
        return value
            .replace(/^formula 1\s+/i, '')
            .replace(/^gran premio de\s+/i, '')
            .replace(/^grand prix of\s+/i, '')
            .replace(/\s+grand prix$/i, '')
            .replace(/\s+gp$/i, '')
            .trim();
    }

    wait(milliseconds) {
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }
}

const bootRaceStreamRaceStrip = () => {
    if (!window.raceStreamRaceStrip && document.getElementById('raceStripTitle')) {
        window.raceStreamRaceStrip = new RaceStreamRaceStrip();
    }
};

if (document.getElementById('raceStripTitle')) {
    bootRaceStreamRaceStrip();
} else {
    document.addEventListener('DOMContentLoaded', bootRaceStreamRaceStrip, { once: true });
}
