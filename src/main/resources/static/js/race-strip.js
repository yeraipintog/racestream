/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.5
 * @created 04-05-2026
 * @modified 07-05-2026
 * @description Carga la franja común de próximo GP con cache para no repetir llamadas al cambiar de pagina
 */
class RaceStreamRaceStrip {

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
        this.bindNavbarDropdowns();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @description Inicializa carga y refresco de relojes
     */
    async init() {
        if (!this.title) return;
        this.meeting = await this.fetchJson(this.meetingApi, null);
        if (this.meeting?.meeting_key) {
            const sessions = await this.fetchJson(this.sessionsApi(this.meeting.meeting_key), []);
            this.sessions = Array.isArray(sessions) ? sessions : [];
        }
        this.render();
        this.renderClocks();
        setInterval(() => {
            this.renderClocks();
            this.renderAction();
        }, 1000);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Activa menús de perfil y hamburguesa si el layout común todavía no los ha enlazado
     */
    bindNavbarDropdowns() {
        const profileDropdown = document.getElementById('profileDropdown');
        const mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
        if (profileDropdown?.dataset.rsDropdownBound && mobileMenuDropdown?.dataset.rsDropdownBound) return;
        profileDropdown?.querySelector('.rs-profile-dropdown__trigger')?.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            profileDropdown.classList.toggle('rs-profile-dropdown--open');
            mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
        });
        mobileMenuDropdown?.querySelector('.rs-navbar__menu-trigger')?.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            mobileMenuDropdown.classList.toggle('rs-navbar-mobile-menu--open');
            profileDropdown?.classList.remove('rs-profile-dropdown--open');
        });
        document.addEventListener('click', () => {
            profileDropdown?.classList.remove('rs-profile-dropdown--open');
            mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
        });
    }

    async fetchJson(url, fallback) {
        const cacheKey = `rs-cache:${url}`;
        const timeKey = `${cacheKey}:time`;
        const cached = localStorage.getItem(cacheKey);
        const cachedAt = Number(localStorage.getItem(timeKey) || 0);
        if (cached && Date.now() - cachedAt < 300000) {
            return JSON.parse(cached);
        }
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                const response = await fetch(url, { cache: 'no-store' });
                if (response.ok) {
                    const data = await response.json();
                    if (data && !(Array.isArray(data) && !data.length)) {
                        localStorage.setItem(cacheKey, JSON.stringify(data));
                        localStorage.setItem(timeKey, String(Date.now()));
                    }
                    return data;
                }
            } catch {
                await this.wait(180 * (attempt + 1));
            }
        }
        return cached ? JSON.parse(cached) : fallback;
    }

    render() {
        if (!this.meeting || this.meeting.isMissingNode) {
            this.title.textContent = 'Recarga forzada';
            this.meta.textContent = 'Reintentar carga';
            if (this.action) {
                this.action.innerHTML = '<button class="rs-button" type="button" onclick="window.location.reload()">Reintentar</button>';
            }
            this.clocks.textContent = '-';
            return;
        }
        this.title.textContent = this.meeting.meeting_name || 'Gran Premio';
        this.meta.textContent = this.formatDateRange(this.meeting.date_start, this.meeting.date_end);
        this.renderAction();
        this.flag.style.display = this.meeting.country_flag ? 'block' : 'none';
        if (this.meeting.country_flag) {
            this.flag.src = this.meeting.country_flag;
        }
    }

    renderClocks() {
        if (!this.meeting) return;
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
        const session = this.sessions.find((item) => new Date(item.date_end).getTime() >= Date.now());
        const isLive = session && Date.now() >= new Date(session.date_start).getTime();
        if (isLive) {
            if (!this.action.querySelector('a')) this.action.innerHTML = '<a class="rs-button rs-button--primary" href="/live.html">En Vivo</a>';
            return;
        }
        const label = `${this.translateSessionName(session?.session_name || 'GP')} en ${this.getCountdown(session?.date_start || this.meeting.date_start)}`;
        const status = this.action.querySelector('.rs-race-strip__status');
        if (status) {
            status.textContent = label;
        } else {
            this.action.innerHTML = `<span class="rs-race-strip__status">${label}</span>`;
        }
    }

    formatDateRange(startValue, endValue) {
        if (!startValue || !endValue) return '-';
        const start = new Date(startValue);
        const end = new Date(endValue);
        const startMonth = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const endMonth = end.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        return startMonth === endMonth
            ? `${startMonth} ${start.getDate()}-${end.getDate()}`
            : `${startMonth} ${start.getDate()} - ${endMonth} ${end.getDate()}`;
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
        if (diff <= 0) return 'En curso';
        const days = Math.floor(diff / 86400000);
        const hours = Math.floor((diff % 86400000) / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);
        const seconds = Math.floor((diff % 60000) / 1000);
        return `${days}d ${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m ${String(seconds).padStart(2, '0')}s`;
    }

    translateSessionName(name) {
        return {
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
        }[name] || name || 'Sesión';
    }

    wait(milliseconds) {
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 07-05-2026
 * @description Evita doble refresco en paginas con franja superior propia
 * @returns {boolean} Resultado
 */
const pageOwnsRaceStrip = () => Boolean(document.querySelector('.rs-live-page, .rs-sessions-page, .rs-news-page') || document.getElementById('calendarGrid'));

const bootRaceStreamRaceStrip = () => {
    if (!pageOwnsRaceStrip() && !window.raceStreamRaceStrip && document.getElementById('raceStripTitle')) {
        window.raceStreamRaceStrip = new RaceStreamRaceStrip();
    }
};

if (document.getElementById('raceStripTitle')) {
    bootRaceStreamRaceStrip();
} else {
    document.addEventListener('DOMContentLoaded', bootRaceStreamRaceStrip, { once: true });
}
