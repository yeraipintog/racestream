/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.3
 * @created 03-05-2026
 * @modified 03-05-2026
 * @description Fórmula 1 En Vivo con estado visual, horarios dobles y datos agregados desde OpenF1
 */
class RaceStreamLivePage {
    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Inicializa endpoints, DOM y refresco de datos
     */
    constructor() {
        this.year = new Date().getFullYear();
        this.meetingApi = `/api/f1/schedule/current-or-next-meeting?year=${this.year}`;
        this.nextSessionApi = `/api/f1/schedule/next-session?year=${this.year}`;
        this.sessionsApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.overviewApi = (sessionKey) => `/api/f1/live/overview?sessionKey=${sessionKey}`;
        this.driversApi = (sessionKey) => `/api/f1/drivers?sessionKey=${sessionKey}`;
        this.mediaApi = (number) => `/api/f1/media/driver?number=${number}`;
        this.meeting = null;
        this.session = null;
        this.sessions = [];
        this.drivers = new Map();
        this.overview = {};
        this.cacheDom();
        this.bindMenus();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Cachea elementos principales de la página
     */
    cacheDom() {
        ['raceStripTitle', 'raceStripMeta', 'raceStripAction', 'raceStripClocks', 'raceStripFlag', 'liveTitleStatus', 'liveStatusCard', 'leaderboard', 'leaderboardCount', 'weatherPanel', 'raceControlPanel', 'stintsPanel', 'pitPanel', 'radioPanel', 'lapsPanel', 'overtakesPanel', 'gridPanel', 'championshipPanel', 'sectorsPanel', 'telemetryPanel'].forEach((id) => this[id] = document.getElementById(id));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Activa menús de perfil y móvil
     */
    bindMenus() {
        const profileDropdown = document.getElementById('profileDropdown');
        const mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
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

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Carga datos iniciales y programa refrescos
     */
    async init() {
        await this.loadData();
        setInterval(() => this.updateRaceStripClocks(), 1000);
        setInterval(() => this.refreshLiveData(), 30000);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Carga calendario, sesión y datos OpenF1
     */
    async loadData() {
        this.meeting = await this.fetchJson(this.meetingApi, null);
        this.session = await this.fetchJson(this.nextSessionApi, null);
        if (this.meeting?.meeting_key) this.sessions = await this.fetchJson(this.sessionsApi(this.meeting.meeting_key), []);
        if (!this.session && this.sessions.length) this.session = this.sessions.find((item) => new Date(item.date_end).getTime() >= Date.now()) || this.sessions[0];
        await this.refreshLiveData();
        this.renderRaceStrip();
        this.renderStatus();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Refresca datos de sesión sin recargar la página
     */
    async refreshLiveData() {
        if (!this.session?.session_key) {
            this.renderEmptyLive();
            return;
        }
        const [overview, drivers] = await Promise.all([
            this.fetchJson(this.overviewApi(this.session.session_key), {}),
            this.fetchJson(this.driversApi(this.session.session_key), [])
        ]);
        this.overview = overview || {};
        this.drivers = new Map((Array.isArray(drivers) ? drivers : []).map((driver) => [Number(driver.driver_number), driver]));
        this.renderLivePanels();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Renderiza la franja superior
     */
    renderRaceStrip() {
        if (!this.meeting) return;
        this.raceStripTitle.textContent = this.meeting.meeting_name || 'Gran Premio';
        this.raceStripMeta.textContent = this.formatDateRange(this.meeting.date_start, this.meeting.date_end);
        this.raceStripFlag.style.display = this.meeting.country_flag ? 'block' : 'none';
        if (this.meeting.country_flag) this.raceStripFlag.src = this.meeting.country_flag;
        this.renderRaceStripAction();
        this.updateRaceStripClocks();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Pinta estado de live o cuenta atrás de la sesión
     */
    renderRaceStripAction() {
        const live = this.isLive(this.session);
        this.raceStripAction.innerHTML = live
            ? '<span class="rs-race-strip__status">En vivo ahora</span>'
            : `<span class="rs-race-strip__status">${this.translateSessionName(this.session?.session_name)} en ${this.getCountdown(this.session?.date_start)}</span>`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 03-05-2026
     * @modified 03-05-2026
     * @description Renderiza el estado junto al titulo y los datos principales en linea horizontal
     */
    renderStatus() {
        const status = this.getSessionStatus(this.session);
        const flag = this.meeting?.country_flag ? `<img class="rs-flag-inline" src="${this.meeting.country_flag}" alt="Bandera de ${this.meeting.country_name || 'país'}">` : '';
        this.liveTitleStatus.innerHTML = this.getStatusPillHtml(status);
        this.liveStatusCard.innerHTML = `
            <div class="rs-live-metrics rs-live-status-grid">
                <div class="rs-live-metric"><span class="rs-live-metric__label">Sesión</span><strong>${this.translateSessionName(this.session?.session_name)}</strong></div>
                <div class="rs-live-metric"><span class="rs-live-metric__label">GP</span><strong class="rs-live-metric__flag">${flag}${this.meeting?.meeting_name || '-'}</strong></div>
                <div class="rs-live-metric"><span class="rs-live-metric__label">Hora cliente</span><strong>${this.formatDateTime(this.session?.date_start)}</strong></div>
                <div class="rs-live-metric"><span class="rs-live-metric__label">Hora circuito</span><strong>${this.formatCircuitDateTime(this.session?.date_start, this.meeting?.gmt_offset)}</strong></div>
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Renderiza todos los paneles OpenF1
     */
    renderLivePanels() {
        this.renderLeaderboard();
        this.renderWeather();
        this.raceControlPanel.innerHTML = this.renderList(this.lastItems(this.overview.raceControl, 8), (item) => `<strong>${item.category || item.flag || 'Aviso'}:</strong> ${item.message || item.reason || 'Sin detalle'}`);
        this.stintsPanel.innerHTML = this.renderList(this.lastItems(this.overview.stints, 8), (item) => `<strong>${this.driverName(item.driver_number)}</strong> ${item.compound || '-'} · vueltas ${item.lap_start || '-'}-${item.lap_end || '-'}`);
        this.pitPanel.innerHTML = this.renderList(this.lastItems(this.overview.pitStops, 8), (item) => `<strong>${this.driverName(item.driver_number)}</strong> vuelta ${item.lap_number || '-'} · ${item.pit_duration || item.duration || '-'}s`);
        this.radioPanel.innerHTML = this.renderList(this.lastItems(this.overview.teamRadio, 8), (item) => `<strong>${this.driverName(item.driver_number)}</strong> ${this.formatTime(item.date)} ${item.recording_url ? `<a href="${item.recording_url}" target="_blank" rel="noopener noreferrer">Escuchar</a>` : ''}`);
        this.lapsPanel.innerHTML = this.renderList(this.bestLaps(), (item) => `<strong>${this.driverName(item.driver_number)}</strong> vuelta ${item.lap_number || '-'} · ${item.lap_duration || '-'}s · S1 ${item.duration_sector_1 || '-'}`);
        this.overtakesPanel.innerHTML = this.renderList(this.lastItems(this.overview.overtakes, 8), (item) => `<strong>${this.driverName(item.overtaking_driver_number)}</strong> a ${this.driverName(item.overtaken_driver_number)} · vuelta ${item.lap_number || '-'}`);
        this.gridPanel.innerHTML = this.renderList(this.lastItems(this.overview.startingGrid, 10), (item) => `<strong>P${item.position || '-'}</strong> ${this.driverName(item.driver_number)}`);
        this.championshipPanel.innerHTML = this.renderChampionship();
        this.sectorsPanel.innerHTML = this.renderSectors();
        this.telemetryPanel.innerHTML = this.renderTelemetry();
        this.hydrateDriverImages();
        this.renderStatus();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Construye el leaderboard con posición, intervalos y gaps
     */
    renderLeaderboard() {
        const intervals = new Map(this.lastByDriver(this.overview.intervals).map((item) => [Number(item.driver_number), item]));
        const rows = this.lastByDriver(this.overview.position).sort((a, b) => (Number(a.position) || 999) - (Number(b.position) || 999));
        this.leaderboardCount.textContent = `${rows.length} pilotos`;
        this.leaderboard.innerHTML = rows.length ? `
            <table class="rs-live-table">
                <thead><tr><th>Pos</th><th>Piloto</th><th>Gap</th><th>Intervalo</th><th>Última actualización</th></tr></thead>
                <tbody>${rows.map((row) => {
                    const interval = intervals.get(Number(row.driver_number)) || {};
                    return `<tr><td>${row.position || '-'}</td><td>${this.renderDriver(row.driver_number)}</td><td>${interval.gap_to_leader || '-'}</td><td>${interval.interval || '-'}</td><td>${this.formatTime(row.date)}</td></tr>`;
                }).join('')}</tbody>
            </table>
        ` : '<p class="empty-state">OpenF1 todavía no ofrece posiciones para esta sesión.</p>';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Renderiza meteorología de la última medición
     */
    renderWeather() {
        const weather = this.lastItems(this.overview.weather, 1)[0];
        this.weatherPanel.innerHTML = weather ? `
            <div class="rs-live-metrics">
                <div class="rs-live-metric"><span>Aire</span><strong>${weather.air_temperature ?? '-'} ºC</strong></div>
                <div class="rs-live-metric"><span>Pista</span><strong>${weather.track_temperature ?? '-'} ºC</strong></div>
                <div class="rs-live-metric"><span>Viento</span><strong>${weather.wind_speed ?? '-'} km/h</strong></div>
                <div class="rs-live-metric"><span>Lluvia</span><strong>${weather.rainfall ? 'Sí' : 'No'}</strong></div>
            </div>
        ` : '<p class="empty-state">Sin meteorología disponible.</p>';
    }

    renderChampionship() {
        const drivers = this.lastItems(this.overview.championshipDrivers, 5);
        const teams = this.lastItems(this.overview.championshipTeams, 5);
        return `${this.renderList(drivers, (item) => `<strong>${item.position || '-'}</strong> ${this.driverName(item.driver_number)} · ${item.points || 0} pts`)}${this.renderList(teams, (item) => `<strong>${item.position || '-'}</strong> ${item.team_name || 'Equipo'} · ${item.points || 0} pts`)}`;
    }

    renderTelemetry() {
        const car = this.lastByDriver(this.overview.carData).slice(0, 8);
        const location = this.lastByDriver(this.overview.location).slice(0, 8);
        const carHtml = this.renderList(car, (item) => `<strong>${this.driverName(item.driver_number)}</strong> ${item.speed || '-'} km/h · marcha ${item.n_gear || '-'} · RPM ${item.rpm || '-'}`);
        const locationHtml = this.renderList(location, (item) => `<strong>${this.driverName(item.driver_number)}</strong> X ${item.x || '-'} · Y ${item.y || '-'} · Z ${item.z || '-'}`);
        return `${carHtml}${locationHtml}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Muestra sectores recientes para completar la lectura de rendimiento por vuelta
     * @returns {string} HTML de sectores
     */
    renderSectors() {
        return this.renderList(this.bestLaps(), (item) => `
            <strong>${this.driverName(item.driver_number)}</strong>
            <span class="rs-live-metric__time">
                <small>S1 ${item.duration_sector_1 || '-'}</small>
                <small>S2 ${item.duration_sector_2 || '-'}</small>
                <small>S3 ${item.duration_sector_3 || '-'}</small>
            </span>
        `);
    }

    renderEmptyLive() {
        document.querySelectorAll('.rs-live-card > div:last-child').forEach((panel) => panel.innerHTML = '<p class="empty-state">No hay sesión disponible para cargar datos live.</p>');
    }

    renderDriver(number) {
        const driver = this.drivers.get(Number(number));
        const name = driver?.full_name || `Piloto ${number || '-'}`;
        const image = driver?.headshot_url || '/assets/img/LogoRS2.png';
        return `<span class="rs-live-driver"><img src="${image}" data-driver-number="${number || ''}" alt="Foto de ${name}" onerror="this.src='/assets/img/LogoRS2.png';">${name}</span>`;
    }

    renderList(items, mapper) {
        return items?.length ? `<div class="rs-live-list">${items.map((item) => `<div class="rs-live-item">${mapper(item)}</div>`).join('')}</div>` : '<p class="empty-state">Sin datos disponibles.</p>';
    }

    bestLaps() {
        return this.lastByDriver(this.overview.laps).sort((a, b) => (Number(a.lap_duration) || 9999) - (Number(b.lap_duration) || 9999)).slice(0, 8);
    }

    lastByDriver(items) {
        const map = new Map();
        (Array.isArray(items) ? items : []).forEach((item) => map.set(Number(item.driver_number), item));
        return [...map.values()];
    }

    lastItems(items, limit) {
        return (Array.isArray(items) ? items : []).slice(-limit).reverse();
    }

    async hydrateDriverImages() {
        document.querySelectorAll('.rs-live-driver img[data-driver-number]').forEach(async (image) => {
            if (!image.dataset.driverNumber || image.src.includes('http')) return;
            const media = await this.fetchJson(this.mediaApi(image.dataset.driverNumber), {});
            if (media?.headshotUrl) image.src = media.headshotUrl;
        });
    }

    async fetchJson(url, fallback) {
        const cacheKey = `rs-cache:${url}`;
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                const response = await fetch(url, { cache: 'no-store' });
                if (response.ok) {
                    const data = await response.json();
                    if (!(Array.isArray(data) && !data.length)) {
                        localStorage.setItem(cacheKey, JSON.stringify(data));
                    }
                    return data;
                }
            } catch {
                await new Promise((resolve) => setTimeout(resolve, 180 * (attempt + 1)));
            }
        }
        const cached = localStorage.getItem(cacheKey);
        if (cached) {
            const parsed = JSON.parse(cached);
            if (!(Array.isArray(parsed) && !parsed.length)) {
                return parsed;
            }
        }
        return fallback;
    }

    driverName(number) { return this.drivers.get(Number(number))?.name_acronym || this.drivers.get(Number(number))?.last_name || `#${number || '-'}`; }
    isLive(session) { return session && Date.now() >= new Date(session.date_start).getTime() && Date.now() <= new Date(session.date_end).getTime(); }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Calcula el estado interno de la sesión para reutilizar estilos del calendario
     * @param {Object} session Sesión actual o próxima
     * @returns {string} Estado normalizado
     */
    getSessionStatus(session) {
        if (session?.is_cancelled) return 'cancelled';
        if (this.isLive(session)) return 'live';
        return session && Date.now() > new Date(session.date_end).getTime() ? 'completed' : 'upcoming';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Construye la píldora visual de estado compartida con calendario
     * @param {string} status Estado interno
     * @returns {string} HTML de estado
     */
    getStatusPillHtml(status) { return `<span class="rs-status-pill rs-status-pill--${status}">${this.getSessionStatusLabel(status)}</span>`; }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Traduce el estado interno a texto visible
     * @param {string} status Estado interno
     * @returns {string} Texto de estado
     */
    getSessionStatusLabel(status) { return ({ cancelled: 'Cancelado', live: 'En vivo', completed: 'Completado', upcoming: 'Próximo' })[status] || 'Próximo'; }
    translateSessionName(name) { return ({ 'Practice 1': 'Libres 1', 'Practice 2': 'Libres 2', 'Practice 3': 'Libres 3', 'Sprint Qualifying': 'Clasif. sprint', Qualifying: 'Clasificación', Race: 'Carrera', Sprint: 'Sprint' })[name] || name || 'Sesión'; }
    formatDateTime(value) { return value ? new Date(value).toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' }) : '-'; }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea la hora de inicio en la zona horaria del circuito
     * @param {string} value Fecha ISO
     * @param {string} offset Offset GMT del circuito
     * @returns {string} Fecha y hora del circuito
     */
    formatCircuitDateTime(value, offset) {
        if (!value) return '-';
        const date = new Date(new Date(value).getTime() + this.parseOffset(offset || '+00:00') * 60000);
        return `${date.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', timeZone: 'UTC' })}, ${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }
    formatTime(value) { return value ? new Date(value).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }) : '-'; }
    formatDateRange(startValue, endValue) { return startValue && endValue ? `${new Date(startValue).toLocaleDateString('en-US', { month: 'short' }).toUpperCase()} ${new Date(startValue).getDate()}-${new Date(endValue).getDate()}` : '-'; }
    getCountdown(value) {
        const diff = new Date(value).getTime() - Date.now();
        if (!value || diff <= 0) return 'En curso';
        return `${Math.floor(diff / 86400000)}d ${Math.floor((diff % 86400000) / 3600000)}h ${Math.floor((diff % 3600000) / 60000)}m`;
    }
    updateRaceStripClocks() {
        if (!this.meeting) return;
        this.raceStripClocks.innerHTML = `<div class="rs-race-strip__clock-card"><div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-label">MI HORA</span><strong class="rs-race-strip__clock-value">${this.formatTime(new Date())}</strong></div><span class="rs-race-strip__clock-divider"></span><div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-subvalue">HORA CIRCUITO</span><span class="rs-race-strip__clock-track-value">${this.getCircuitNowTime(this.meeting.gmt_offset)}</span></div></div>`;
        this.renderRaceStripAction();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Devuelve la hora actual del circuito usando su offset GMT
     * @param {string} offset Offset GMT
     * @returns {string} Hora HH:mm
     */
    getCircuitNowTime(offset) {
        const date = new Date(Date.now() + this.parseOffset(offset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Convierte un offset GMT a minutos
     * @param {string} offset Offset GMT
     * @returns {number} Minutos de diferencia
     */
    parseOffset(offset) {
        const sign = `${offset}`.startsWith('-') ? -1 : 1;
        const [hours = '0', minutes = '0'] = `${offset}`.replace('+', '').replace('-', '').split(':');
        return sign * (Number(hours) * 60 + Number(minutes));
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamLivePage = new RaceStreamLivePage();
});
