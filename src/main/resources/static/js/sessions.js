/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.5.1
 * @created 30-04-2026
 * @modified 14-05-2026
 * @description Lógica de Sesiones completadas con selector rápido por GP, carga reforzada, podios y tablas
 */
class RaceStreamSessionsPage {

    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.initialParams = new URLSearchParams(window.location.search);
        const requestedYear = Number(this.initialParams.get('year'));
        this.year = Number.isInteger(requestedYear) && requestedYear >= 1950 ? requestedYear : new Date().getFullYear();
        this.raceStripYear = new Date().getFullYear();
        this.currentMeetingApi = `/api/f1/schedule/current-or-next-meeting?year=${this.raceStripYear}`;
        this.meetingsApi = (year) => `/api/f1/schedule/calendar-meetings?year=${year}`;
        this.sessionsApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.resultsApi = (sessionKey) => `/api/f1/session-results?sessionKey=${sessionKey}`;
        this.driversApi = (sessionKey) => `/api/f1/drivers?sessionKey=${sessionKey}`;
        this.lapsApi = (sessionKey) => `/api/f1/live/laps?sessionKey=${sessionKey}`;
        this.raceResultsApi = (year) => `/api/f1/standings/race-results?year=${year}`;
        this.yearInput = document.getElementById('sessionsYearInput');
        this.meetingSelect = document.getElementById('meetingSelect');
        this.sessionsList = document.getElementById('sessionsList');
        this.raceStripTitle = document.getElementById('raceStripTitle');
        this.raceStripMeta = document.getElementById('raceStripMeta');
        this.raceStripAction = document.getElementById('raceStripAction');
        this.raceStripClocks = document.getElementById('raceStripClocks');
        this.raceStripFlag = document.getElementById('raceStripFlag');
        this.sessionsSelectedGp = document.getElementById('sessionsSelectedGp');
        this.sessionsSelectedTitle = document.getElementById('sessionsSelectedTitle');
        this.meetings = [];
        this.selectableMeetings = [];
        this.selectedMeeting = null;
        this.topMeeting = null;
        this.topSessions = [];
        this.ownsRaceStrip = !window.raceStreamRaceStrip;
        this.sessionData = new Map();
        this.completedSessionsByMeetingKey = new Map();
        this.currentSessions = [];
        this.loadingSessionKeys = new Set();
        this.unavailableSessionKeys = new Set();
        this.expandedSessionIndex = null;
        this.selectionRequestId = 0;
        this.meetingsRequestId = 0;
        this.raceStripRequestId = 0;
        this.bindEvents();
        this.init();
        if (this.ownsRaceStrip) {
            setInterval(() => {
                this.updateRaceStripClocks();
                this.renderRaceStripAction();
            }, 30000);
        }
    }

    bindEvents() {
        this.yearInput.value = String(this.year);
        this.yearInput.addEventListener('change', () => {
            this.initialParams.delete('meetingKey');
            this.initialParams.delete('sessionKey');
            this.initialParams.delete('sessionIndex');
            this.updateUrl(Number(this.yearInput.value), null);
            this.loadMeetings(Number(this.yearInput.value));
        });
        this.meetingSelect.addEventListener('change', () => this.selectMeeting(Number(this.meetingSelect.value)));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Inicializa temporadas, strip superior y meetings seleccionables
     * @returns {Promise<void>} Carga completada
     */
    async init() {
        await this.loadSeasons();
        if (this.ownsRaceStrip) this.loadRaceStripMeeting();
        await this.loadMeetings();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Carga temporadas historicas en el selector de sesiones
     * @returns {Promise<void>} Carga completada
     */
    async loadSeasons() {
        const currentYear = new Date().getFullYear();
        const seasons = await this.fetchJson('/api/f1/standings/seasons', []);
        const safeSeasons = Array.isArray(seasons) && seasons.length
            ? seasons
            : Array.from({ length: currentYear - 1949 }, (_, index) => ({ season: currentYear - index }));
        this.yearInput.innerHTML = safeSeasons
            .map((item) => `<option value="${item.season}" ${Number(item.season) === this.year ? 'selected' : ''}>${item.season}</option>`)
            .join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 30-04-2026
     * @modified 14-05-2026
     * @description Carga GP de la temporada sin pedir todas sus sesiones antes de pintar el selector
     * @param {number} year Temporada
     * @returns {Promise<void>} Carga completada
     */
    async loadMeetings(year = this.year) {
        const requestId = ++this.meetingsRequestId;
        this.selectionRequestId++;
        this.year = year;
        this.sessionData.clear();
        this.completedSessionsByMeetingKey.clear();
        this.unavailableSessionKeys.clear();
        this.selectedMeeting = null;
        this.meetingSelect.disabled = true;
        this.yearInput.disabled = true;
        this.yearInput.setAttribute('aria-busy', 'true');
        this.meetingSelect.value = '';
        this.meetingSelect.innerHTML = '<option value="">Cargando...</option>';
        this.sessionsList.innerHTML = '<p class="loading-state">Cargando Grandes Premios...</p>';
        this.renderSelectedMeetingInfo(null);
        this.updateSelectedTitle(null);
        this.meetings = await this.fetchMeetingsWithRetry(this.year, requestId);
        if (requestId !== this.meetingsRequestId) {
            return;
        }
        if (!Array.isArray(this.meetings) || !this.meetings.length) {
            this.meetingSelect.innerHTML = '<option value="">Sin datos</option>';
            this.yearInput.disabled = false;
            this.yearInput.removeAttribute('aria-busy');
            this.renderSessionsRetry('No hay sesiones disponibles para esta temporada.');
            this.renderSelectedMeetingInfo(null);
            this.updateSelectedTitle(null);
            return;
        }

        this.sessionsList.innerHTML = '<p class="loading-state">Preparando Grandes Premios con sesiones completadas...</p>';
        this.selectableMeetings = await this.loadSelectableMeetings(
            this.meetings.filter((meeting) => this.hasUsableMeetingKey(meeting)),
            requestId
        );
        if (requestId !== this.meetingsRequestId) {
            return;
        }
        this.meetingSelect.innerHTML = this.selectableMeetings.map((meeting) => `
            <option value="${meeting.meeting_key}">${meeting.meeting_name}</option>
        `).join('');

        const requestedMeetingKey = Number(this.initialParams.get('meetingKey'));
        const selected = this.selectableMeetings.find((meeting) => meeting.meeting_key === requestedMeetingKey)
            || [...this.selectableMeetings].reverse().find((meeting) => this.getMeetingStatus(meeting) === 'completed')
            || this.getCurrentOrNextMeeting(this.selectableMeetings);
        if (!selected) {
            this.meetingSelect.innerHTML = '<option value="">Sin Grandes Premios</option>';
            this.yearInput.disabled = false;
            this.yearInput.removeAttribute('aria-busy');
            this.renderSessionsRetry('Todavía no hay Grandes Premios con sesiones completadas en esta temporada.');
            this.renderSelectedMeetingInfo(null);
            this.updateSelectedTitle(null);
            return;
        }
        this.meetingSelect.disabled = false;
        this.yearInput.disabled = false;
        this.yearInput.removeAttribute('aria-busy');
        this.meetingSelect.value = selected.meeting_key;
        this.initialParams.delete('meetingKey');
        if (requestId !== this.meetingsRequestId) {
            return;
        }
        await this.selectMeeting(selected.meeting_key);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 30-04-2026
     * @modified 14-05-2026
     * @description Carga solo las sesiones completadas del GP seleccionado y conserva sus detalles
     * @param {number|string} meetingKey Clave del GP
     * @returns {Promise<void>} Carga completada
     */
    async selectMeeting(meetingKey) {
        const requestId = ++this.selectionRequestId;
        this.selectedMeeting = this.selectableMeetings.find((meeting) => meeting.meeting_key === meetingKey);
        if (!this.selectedMeeting) {
            this.sessionsList.innerHTML = '<p class="empty-state">Selecciona un Gran Premio con sesiones oficiales.</p>';
            this.renderSelectedMeetingInfo(null);
            this.updateSelectedTitle(null);
            return;
        }
        this.renderSelectedMeetingInfo(this.selectedMeeting);
        this.updateSelectedTitle(this.selectedMeeting);
        this.updateUrl(this.year, meetingKey);
        this.sessionsList.innerHTML = '<p class="loading-state">Cargando sesiones oficiales...</p>';
        const cachedCompletedSessions = this.completedSessionsByMeetingKey.get(`${meetingKey}`);
        const sessions = cachedCompletedSessions || await this.fetchSessionsWithRetry(meetingKey);
        if (requestId !== this.selectionRequestId) {
            return;
        }
        const safeSessions = Array.isArray(sessions) ? sessions : [];
        this.unavailableSessionKeys.clear();
        const completedSessions = cachedCompletedSessions || this.getCompletedSessions(safeSessions, this.selectedMeeting);
        if (!cachedCompletedSessions) {
            this.completedSessionsByMeetingKey.set(`${meetingKey}`, completedSessions);
        }
        this.currentSessions = completedSessions;
        this.expandedSessionIndex = null;

        if (!completedSessions.length) {
            this.removeSelectableMeeting(meetingKey);
            const fallbackMeeting = this.selectableMeetings[this.selectableMeetings.length - 1];
            if (fallbackMeeting) {
                this.meetingSelect.value = fallbackMeeting.meeting_key;
                await this.selectMeeting(fallbackMeeting.meeting_key);
                return;
            }
            this.sessionsList.innerHTML = '<p class="empty-state">Todavía no hay sesiones completadas para este Gran Premio.</p>';
            return;
        }

        this.renderSessions(completedSessions);
        this.expandRequestedSession(completedSessions);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Muestra estado vacío con recarga manual cuando no se pueden cargar sesiones
     * @param {string} message Mensaje visible
     */
    renderSessionsRetry(message) {
        this.sessionsList.innerHTML = window.RaceStreamApi.retryButton('Sesiones no disponibles', message);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Filtra el selector sin bloquear la página con todas las sesiones de la temporada
     * @param {Array} meetings Grandes Premios con clave util
     * @param {number} requestId Carga activa
     * @returns {Promise<Array>} Grandes Premios consultables
     */
    async loadSelectableMeetings(meetings, requestId) {
        const completedMeetings = meetings.filter((meeting) => this.getMeetingStatus(meeting) === 'completed');
        const liveMeetings = meetings.filter((meeting) => this.getMeetingStatus(meeting) === 'live');
        if (!liveMeetings.length) return completedMeetings;

        const liveWithCompletedSessions = await Promise.all(liveMeetings.map(async (meeting) => {
            if (requestId !== this.meetingsRequestId) return null;
            const sessions = await this.fetchArrayWithRetry(this.sessionsApi(meeting.meeting_key), 2, 180);
            const completedSessions = this.getCompletedSessions(sessions, meeting);
            if (!completedSessions.length) return null;
            this.completedSessionsByMeetingKey.set(`${meeting.meeting_key}`, completedSessions);
            return meeting;
        }));
        if (requestId !== this.meetingsRequestId) return [];
        return [...completedMeetings, ...liveWithCompletedSessions.filter(Boolean)]
            .sort((left, right) => new Date(left.date_start).getTime() - new Date(right.date_start).getTime());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Devuelve solo sesiones completadas sin perder sesiones históricas con fechas válidas
     * @param {Array} sessions Sesiones del GP
     * @param {Object|null} meeting Gran Premio asociado
     * @returns {Array} Sesiones completadas
     */
    getCompletedSessions(sessions, meeting = this.selectedMeeting) {
        return (Array.isArray(sessions) ? sessions : [])
            .filter((session) => this.getSessionStatus(session, meeting) === 'completed');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Retira del selector un GP que finalmente no tiene sesiones completadas
     * @param {number|string} meetingKey Clave del GP
     */
    removeSelectableMeeting(meetingKey) {
        this.selectableMeetings = this.selectableMeetings.filter((meeting) => `${meeting.meeting_key}` !== `${meetingKey}`);
        this.meetingSelect.innerHTML = this.selectableMeetings.length
            ? this.selectableMeetings.map((meeting) => `<option value="${meeting.meeting_key}">${meeting.meeting_name}</option>`).join('')
            : '<option value="">Sin Grandes Premios</option>';
        this.meetingSelect.disabled = !this.selectableMeetings.length;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera a que la temporada tenga meetings oficiales antes de pintar opciones
     * @param {number} year Temporada
     * @param {number} requestId Identificador de carga activa
     * @returns {Promise<Array>} Meetings confirmados
     */
    async fetchMeetingsWithRetry(year, requestId) {
        let lastMeetings = [];
        for (let attempt = 0; attempt < 6; attempt++) {
            const meetings = await this.fetchArrayWithRetry(this.meetingsApi(year), 1);
            if (requestId !== this.meetingsRequestId) return [];
            lastMeetings = Array.isArray(meetings) ? meetings : [];
            if (lastMeetings.some((meeting) => this.hasUsableMeetingKey(meeting))) {
                return lastMeetings;
            }
            await this.wait(420 * (attempt + 1));
        }
        return lastMeetings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Actualiza la cápsula derecha del header con el GP completado seleccionado
     * @param {Object|null} meeting Gran Premio seleccionado
     */
    renderSelectedMeetingInfo(meeting) {
        if (!this.sessionsSelectedGp) return;
        const country = meeting?.country_name || meeting?.jolpica_country || '';
        const flag = meeting?.country_flag
            ? `<img class="rs-flag-inline" src="${meeting.country_flag}" alt="Bandera de ${country || 'país'}">`
            : this.assets?.countryFlag(country) || '';
        this.sessionsSelectedGp.innerHTML = meeting
            ? `
                ${flag}
                <span>${meeting.location || meeting.meeting_name || 'GP'} · ${this.formatDateRange(meeting.date_start, meeting.date_end)}</span>
            `
            : '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 03-05-2026
     * @description Muestra el nombre oficial completo del GP
     * @param {Object|null} meeting Gran Premio seleccionado
     */
    updateSelectedTitle(meeting) {
        if (!this.sessionsSelectedTitle) return;
        const name = meeting?.meeting_official_name || meeting?.meeting_name || 'Cargando gran premio...';
        this.sessionsSelectedTitle.textContent = name;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Reintenta sesiones para no renderizar resultados incompletos por respuestas temporales vacías
     * @param {number} meetingKey Clave del GP
     * @returns {Promise<Array>} Sesiones disponibles
     */
    async fetchSessionsWithRetry(meetingKey) {
        return this.fetchArrayWithRetry(this.sessionsApi(meetingKey), 6, 320);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Carga resultados, pilotos y vueltas de una sesion real
     * @param {Object} session Sesion
     * @returns {Promise<void>} Carga completada
     */
    async loadSessionData(session) {
        const cacheKey = this.getSessionCacheKey(session);
        if (!cacheKey || this.sessionData.has(cacheKey)) {
            return;
        }
        if (this.canUseJolpicaRaceResults(session)) {
            const results = await this.loadJolpicaRaceRows();
            this.sessionData.set(cacheKey, {
                results,
                laps: [],
                driverMap: new Map()
            });
            if (!results.length) {
                this.unavailableSessionKeys.add(cacheKey);
            }
            return;
        }
        if (!session?.session_key) {
            this.unavailableSessionKeys.add(cacheKey);
            return;
        }
        const results = await this.fetchArrayWithRetry(this.resultsApi(session.session_key), 5, 260);
        const safeResults = Array.isArray(results) ? results : [];
        const [drivers, laps] = await Promise.all([
            this.fetchArrayWithRetry(this.driversApi(session.session_key), 4, 220),
            safeResults.length ? Promise.resolve([]) : this.fetchArrayWithRetry(this.lapsApi(session.session_key), 5, 260)
        ]);
        const driverMap = new Map((Array.isArray(drivers) ? drivers : []).map((driver) => [Number(driver.driver_number), driver]));
        this.sessionData.set(cacheKey, {
            results: safeResults,
            laps: Array.isArray(laps) ? laps : [],
            driverMap
        });
        if (!safeResults.length && !(Array.isArray(laps) && laps.length)) {
            this.unavailableSessionKeys.add(cacheKey);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Devuelve una clave estable para sesiones reales o sinteticas
     * @param {Object} session Sesion
     * @returns {string} Clave
     */
    getSessionCacheKey(session) {
        return session?.session_key
            ? `${session.session_key}`
            : `${this.selectedMeeting?.meeting_key || 'gp'}:${session?.session_name || session?.session_type || 'session'}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Indica si una carrera historica puede usar resultados Jolpica
     * @param {Object} session Sesion
     * @returns {boolean} Resultado
     */
    canUseJolpicaRaceResults(session) {
        return !session?.session_key
            && /race/i.test(`${session?.session_name || ''} ${session?.session_type || ''}`)
            && Boolean(this.selectedMeeting?.jolpica_round);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Adapta resultados Jolpica al formato de tabla de sesiones
     * @returns {Promise<Array>} Resultados de carrera adaptados
     */
    async loadJolpicaRaceRows() {
        const races = await this.fetchArrayWithRetry(this.raceResultsApi(this.year), 4, 280);
        const race = races.find((item) => `${item.round || ''}` === `${this.selectedMeeting?.jolpica_round || ''}`)
            || races.find((item) => this.normalizeAssetKey(item.raceName) === this.normalizeAssetKey(this.selectedMeeting?.meeting_name));
        return (race?.Results || []).map((result) => {
            const driver = result.Driver || {};
            const constructor = result.Constructor || {};
            const time = result.Time?.time || '';
            const isLeader = Number(result.positionOrder || result.position) === 1;
            return {
                Driver: driver,
                Constructor: constructor,
                driver_number: result.number || driver.permanentNumber || '',
                driver_code: driver.code || '',
                full_name: [driver.givenName, driver.familyName].filter(Boolean).join(' ') || driver.code || 'Piloto',
                team_name: constructor.name || '-',
                position: result.positionOrder || result.position || result.positionText,
                number_of_laps: result.laps || '-',
                duration: isLeader ? time : null,
                gap_to_leader: !isLeader && time.startsWith('+') ? time : null,
                points: result.points || 0,
                status: result.status || '-'
            };
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 04-05-2026
     * @description Renderiza sesiones y actualiza las fotos reales de pilotos al terminar
     * @param {Array} sessions Sesiones del GP
     */
    renderSessions(sessions) {
        this.sessionsList.innerHTML = sessions.map((session, index) => {
            const date = new Date(session.date_start);
            const key = this.getSessionCacheKey(session);
            const unavailable = this.unavailableSessionKeys.has(key);
            const hasData = this.hasSessionData(session);
            const canOpen = this.shouldLoadSessionDetails(session) || hasData;
            const expanded = canOpen && this.expandedSessionIndex === index;
            const loading = this.loadingSessionKeys.has(key);
            const pendingText = unavailable ? 'Sin resultados oficiales' : canOpen ? 'Pulsa para cargar resultados' : 'Sin resultados oficiales';
            return `
                <article class="${this.getSessionTypeClass(session.session_type)}${expanded ? ' rs-session-row--expanded' : ''}${canOpen ? '' : ' rs-session-row--disabled'}" data-session-index="${index}" data-session-open="${canOpen}" aria-expanded="${expanded}">
                    <div class="rs-session-row__date"><strong>${date.getDate()}</strong><span>${date.toLocaleDateString('en-US', { month: 'short' }).toUpperCase()}</span></div>
                    <div class="rs-session-row__main">
                        <div class="rs-session-row__title"><h4>${this.translateSessionName(session.session_name)}</h4></div>
                        <div class="rs-session-row__meta">${this.translateSessionType(session.session_type)}</div>
                    </div>
                    <div class="rs-session-row__summary">
                        ${hasData ? this.renderSessionPodium(session) : `<div class="rs-session-row__pending">${loading ? 'Cargando resultados...' : pendingText}</div>`}
                    </div>
                    <div class="rs-session-row__details">
                        ${expanded && hasData ? this.renderResultTable(session) : expanded && loading ? '<p class="loading-state">Cargando datos de la sesión...</p>' : ''}
                    </div>
                </article>
            `;
        }).join('') || '<p class="empty-state">Todavía no hay sesiones completadas para este Gran Premio.</p>';

        this.sessionsList.querySelectorAll('[data-session-open="true"]').forEach((row) => {
            row.addEventListener('click', () => this.toggleSession(Number(row.dataset.sessionIndex)));
        });
        this.hydrateDriverImages();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Renderiza el podio compacto de una sesion completada con datos disponibles
     * @param {Object} session Sesion
     * @returns {string} HTML del podio
     */
    renderSessionPodium(session) {
        const podium = this.getSortedResults(session).slice(0, 3);
        if (podium.length < 3) return '<div class="rs-session-row__pending">Podio no disponible</div>';
        return `
            <div class="rs-session-podium" aria-label="Podio de la sesión">
                ${podium.map((row, index) => {
                    const driver = this.getDriver(row, session);
                    const name = driver?.full_name || row.full_name || `Piloto ${row.driver_number || ''}`;
                    return `
                        <span class="rs-session-podium__item">
                            <strong>${index + 1}</strong>
                            ${this.renderDriverAvatar(name, driver, row, session, '')}
                        </span>
                    `;
                }).join('')}
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Indica si una sesion puede cargar resultados reales desde OpenF1
     * @param {Object} session Sesion
     * @returns {boolean} Resultado
     */
    shouldLoadSessionDetails(session) {
        if (this.unavailableSessionKeys.has(this.getSessionCacheKey(session))) {
            return false;
        }
        return (Boolean(session?.session_key) || this.canUseJolpicaRaceResults(session))
            && this.getSessionStatus(session) === 'completed';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Comprueba si una sesion ya tiene resultados o vueltas cargadas
     * @param {Object} session Sesion
     * @returns {boolean} Resultado
     */
    hasSessionData(session) {
        const data = this.getSessionData(session);
        return Boolean(data?.results?.length || data?.laps?.length);
    }

    renderResultTable(session) {
        const rows = this.getSortedResults(session);
        if (!rows.length) {
            return '';
        }

        const isQualifying = this.isQualifyingSession(session);
        const isRace = /race|sprint/i.test(`${session.session_type || ''} ${session.session_name || ''}`) && !isQualifying;
        const isPractice = !isRace && !isQualifying;
        const headers = isRace
            ? ['Posición', 'Piloto', 'Código', 'Número', 'Escudería', 'Vueltas realizadas', 'Tiempo / gap', 'Puntos', 'Estado']
            : isQualifying
                ? ['Posición', 'Piloto', 'Código', 'Número', 'Escudería', 'Vueltas realizadas', 'Tiempo Q1', 'Tiempo Q2', 'Tiempo Q3', 'Mejor vuelta / gap']
                : ['Posición', 'Piloto', 'Código', 'Número', 'Escudería', 'Vueltas realizadas', 'Mejor vuelta / gap'];

        return `
            <div class="rs-session-table-wrap">
                <table class="rs-session-table">
                    <thead><tr>${headers.map((header) => `<th>${header}</th>`).join('')}</tr></thead>
                    <tbody>${rows.map((row, index) => this.renderResultRow(row, session, index, isRace, isQualifying, isPractice)).join('')}</tbody>
                </table>
            </div>
        `;
    }

    renderResultRow(row, session, index, isRace, isQualifying, isPractice) {
        const driver = this.getDriver(row, session);
        const position = this.getResultPosition(row, index, isRace);
        const stats = this.getLapStats(row, session);
        const base = [
            position,
            this.renderDriverCell(driver, row, session),
            driver?.name_acronym || row.driver_code || '-',
            row.driver_number || driver?.driver_number || '-',
            this.renderTeamCell(driver, row, session)
        ];
        const raceCells = [stats.laps || row.number_of_laps || row.laps || '-', this.formatRaceTimeOrGap(row, session, index), this.formatPoints(row.points ?? this.getRacePoints(position, session)), this.getRaceStatus(row, session)];
        const qualifyingData = this.formatQualifyingData(row, session, index);
        const qualifyingCells = [stats.laps || '-', qualifyingData.q1, qualifyingData.q2, qualifyingData.q3, qualifyingData.gap];
        const practiceCells = [stats.laps || '-', this.formatBestLapGap(row, session, index)];
        return `<tr>${[...base, ...(isRace ? raceCells : isQualifying ? qualifyingCells : practiceCells)].map((cell) => `<td>${cell}</td>`).join('')}</tr>`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Devuelve posicion visual sin inventar clasificacion para DNFs
     * @param {Object} row Resultado
     * @param {number} index Indice
     * @param {boolean} isRace Indica si es carrera
     * @returns {string|number} Posicion
     */
    getResultPosition(row, index, isRace) {
        if (row.position) return row.position;
        return isRace ? 'NC' : index + 1;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Muestra el estado real de carrera devuelto por OpenF1
     * @param {Object} row Resultado
     * @returns {string} Estado
     */
    getRaceStatus(row) {
        if (row.dsq || row.disqualified) return 'DSQ';
        if (row.dns) return 'DNS';
        if (row.dnf || row.retired) return 'DNF';
        if (row.status) return row.status;
        if (row.position || row.duration != null || row.gap_to_leader != null) return 'Clasificado';
        return '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea puntos reales sin decimales innecesarios
     * @param {number|string} value Puntos
     * @returns {string|number} Puntos visibles
     */
    formatPoints(value) {
        const points = Number(value);
        return Number.isFinite(points) ? points.toLocaleString('es-ES', { maximumFractionDigits: 2 }) : value || 0;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 03-05-2026
     * @modified 04-05-2026
     * @description Pinta la celda del piloto con fallback visual y endpoint interno de imagen oficial
     * @param {Object} driver Piloto OpenF1
     * @param {Object} row Resultado
     * @param {Object} session Sesión
     * @returns {string} HTML de piloto
     */
    renderDriverCell(driver, row, session) {
        const name = driver?.full_name || row.full_name || `Piloto ${row.driver_number || ''}`;
        return `
            <span class="rs-session-driver">
                ${this.renderDriverAvatar(name, driver, row, session, 'rs-session-driver__avatar')}
                ${name}
            </span>
        `;
    }

    renderTeamCell(driver, row, session) {
        const teamName = driver?.team_name || row.team_name || '-';
        const year = this.getSessionYear(session);
        const constructor = { name: teamName, constructorId: row.Constructor?.constructorId || teamName };
        return `<span class="rs-session-team">${this.assets.teamMark(constructor, year, 'rs-session-team__mark')}<span>${this.assets.escape(teamName)}</span></span>`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 06-05-2026
     * @modified 11-05-2026
     * @description Renderiza avatar con los mismos assets por temporada que pilotos y escuderias
     * @param {string} name Nombre del piloto
     * @param {Object} driver Piloto OpenF1
     * @param {Object} row Resultado
     * @param {Object} session Sesion
     * @param {string} className Clase extra
     * @returns {string} HTML del avatar
     */
    renderDriverAvatar(name, driver, row, session, className) {
        const year = this.getSessionYear(session);
        const [givenName, ...familyParts] = `${name || ''}`.trim().split(/\s+/);
        const driverAsset = {
            ...row.Driver,
            ...driver,
            givenName: row.Driver?.givenName || driver?.givenName || driver?.first_name || givenName,
            familyName: row.Driver?.familyName || driver?.familyName || driver?.last_name || familyParts.join(' '),
            full_name: name,
            code: driver?.name_acronym || row.driver_code || row.Driver?.code,
            permanentNumber: row.driver_number || driver?.driver_number || row.Driver?.permanentNumber
        };
        const constructor = row.Constructor || { name: driver?.team_name || row.team_name || '', constructorId: driver?.team_name || row.team_name || '' };
        return this.assets.personAvatar(driverAsset, className, { constructor, year, size: 64 });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 04-05-2026
     * @description Sustituye fotos calculadas por la imagen real del endpoint interno cuando está disponible
     * @returns {void}
     */
    hydrateDriverImages() {
        this.assets.hydrateDriverImages(this.sessionsList, this.fetchJson.bind(this), this.loadImage.bind(this));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Precarga una imagen antes de reemplazar la foto visible del piloto
     * @param {string} url URL de la imagen
     * @returns {Promise<void>} Resultado de carga
     */
    loadImage(url) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve();
            image.onerror = () => reject();
            image.src = url;
        });
    }

    async toggleSession(index) {
        const session = this.currentSessions[index];
        if (!session || (!this.shouldLoadSessionDetails(session) && !this.hasSessionData(session))) {
            return;
        }
        const row = this.sessionsList.querySelector(`[data-session-index="${index}"]`);
        const shouldExpand = row && !row.classList.contains('rs-session-row--expanded');
        this.expandedSessionIndex = shouldExpand ? index : null;
        this.renderSessions(this.currentSessions);
        if (!shouldExpand) return;

        const key = this.getSessionCacheKey(session);
        if (!this.shouldLoadSessionDetails(session) || this.hasSessionData(session) || this.loadingSessionKeys.has(key)) return;
        this.loadingSessionKeys.add(key);
        this.renderSessions(this.currentSessions);
        await this.loadSessionData(session);
        this.loadingSessionKeys.delete(key);
        if (!this.hasSessionData(session)) {
            this.unavailableSessionKeys.add(key);
            this.expandedSessionIndex = null;
        }
        this.renderSessions(this.currentSessions);
    }

    expandRequestedSession(sessions) {
        const requestedSessionKey = this.initialParams.get('sessionKey');
        const hasRequestedIndex = this.initialParams.has('sessionIndex');
        const requestedIndex = hasRequestedIndex ? Number(this.initialParams.get('sessionIndex')) : -1;
        const index = requestedSessionKey
            ? sessions.findIndex((session) => `${session.session_key}` === requestedSessionKey)
            : requestedIndex;
        if ((requestedSessionKey || hasRequestedIndex) && Number.isInteger(index) && index >= 0
            && this.shouldLoadSessionDetails(sessions[index])) {
            this.toggleSession(index);
            this.initialParams.delete('sessionKey');
            this.initialParams.delete('sessionIndex');
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Carga el GP actual o próximo real sin depender de la temporada seleccionada en Sesiones
     * @returns {Promise<void>} Carga completada
     */
    async loadRaceStripMeeting() {
        const requestId = ++this.raceStripRequestId;
        let meeting = await this.fetchJson(this.currentMeetingApi, null);
        if (!meeting?.meeting_key) {
            const meetings = await this.fetchArrayWithRetry(this.meetingsApi(this.raceStripYear), 4, 320);
            meeting = this.getCurrentOrNextMeeting(meetings);
        }
        if (requestId !== this.raceStripRequestId || !meeting) return;
        this.topMeeting = meeting;
        this.topSessions = [];
        this.updateRaceStrip();
        this.loadTopSessions(requestId);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 04-05-2026
     * @description Carga las sesiones del GP del strip superior sin usar la temporada seleccionada
     * @param {number} requestId Identificador de carga del strip
     * @returns {Promise<void>} Carga completada
     */
    async loadTopSessions(requestId = this.raceStripRequestId) {
        if (!this.topMeeting?.meeting_key) return;
        const meetingKey = this.topMeeting.meeting_key;
        const sessions = await this.fetchSessionsWithRetry(meetingKey);
        if (requestId !== this.raceStripRequestId || this.topMeeting?.meeting_key !== meetingKey) return;
        this.topSessions = Array.isArray(sessions) ? sessions : [];
        this.renderRaceStripAction();
    }

    updateRaceStrip() {
        const meeting = this.topMeeting;
        if (!meeting) return;
        this.raceStripTitle.textContent = meeting.meeting_name || 'Gran Premio';
        this.raceStripMeta.textContent = this.formatDateRange(meeting.date_start, meeting.date_end);
        this.raceStripFlag.style.display = meeting.country_flag ? 'block' : 'none';
        if (meeting.country_flag) this.raceStripFlag.src = meeting.country_flag;
        this.renderRaceStripAction();
        this.updateRaceStripClocks();
    }

    renderRaceStripAction() {
        if (!this.topMeeting) return;
        const session = this.topSessions.find((item) => new Date(item.date_end).getTime() >= Date.now());
        const isLive = session && Date.now() >= new Date(session.date_start).getTime();
        if (isLive) {
            if (!this.raceStripAction.querySelector('a')) this.raceStripAction.innerHTML = '<a class="rs-button rs-button--primary" href="/live.html">En Vivo</a>';
            return;
        }
        const text = this.getCountdown(this.translateSessionName(session?.session_name), session?.date_start || this.topMeeting.date_start);
        const status = this.raceStripAction.querySelector('.rs-race-strip__status');
        if (status) {
            status.textContent = text;
        } else {
            this.raceStripAction.innerHTML = `<span class="rs-race-strip__status">${text}</span>`;
        }
    }

    updateRaceStripClocks() {
        if (!this.topMeeting) return;
        if (!this.raceStripClocks.querySelector('.rs-race-strip__clock-card')) {
            this.raceStripClocks.innerHTML = `
                <div class="rs-race-strip__clock-card">
                    <div class="rs-race-strip__clock-row">
                        <span class="rs-race-strip__clock-label">MI HORA</span>
                        <strong class="rs-race-strip__clock-value"></strong>
                    </div>
                    <span class="rs-race-strip__clock-divider"></span>
                    <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-subvalue">CIRCUITO</span><span class="rs-race-strip__clock-track-value"></span></div>
                </div>
            `;
        }
        this.raceStripClocks.querySelector('.rs-race-strip__clock-value').textContent = new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
        this.raceStripClocks.querySelector('.rs-race-strip__clock-track-value').textContent = this.getCircuitNowTime(this.topMeeting.gmt_offset);
    }

    getSortedResults(session) {
        const data = this.getSessionData(session);
        const rows = (data?.results?.length ? data.results : this.buildRowsFromLaps(session));
        return [...rows].sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999));
    }

    getDriver(row, session) {
        return this.getSessionData(session)?.driverMap.get(Number(row.driver_number)) || row.Driver;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Lee datos cargados de una sesion real o sintetica
     * @param {Object} session Sesion
     * @returns {Object|undefined} Datos cacheados
     */
    getSessionData(session) {
        return this.sessionData.get(this.getSessionCacheKey(session));
    }

    formatRaceTimeOrGap(row, session, index) {
        const leaderGap = this.lastUsableValue(row.gap_to_leader);
        const duration = this.lastUsableValue(row.duration || row.total_time);
        if (index === 0) {
            return this.formatRawRaceTime(duration || this.getLapStats(row, session).bestLap) || 'Líder';
        }
        if (leaderGap !== null) {
            return this.formatGapValue(leaderGap);
        }
        const lapGap = this.getLapGapToLeader(row, session);
        if (lapGap > 0) return this.formatLapGap(lapGap);
        if (row.interval) {
            return this.formatGapValue(row.interval);
        }
        if (duration !== null) {
            const leaderDuration = this.lastUsableValue(this.getSortedResults(session)[0]?.duration);
            const seconds = this.toSeconds(duration);
            const leaderSeconds = this.toSeconds(leaderDuration);
            if (seconds !== null && leaderSeconds !== null && seconds > leaderSeconds) {
                return this.formatGapValue(seconds - leaderSeconds);
            }
        }
        return this.formatBestLapGap(row, session, index);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Calcula diferencia de vueltas cuando OpenF1 no envia texto de gap
     * @param {Object} row Resultado
     * @param {Object} session Sesión
     * @returns {number} Vueltas perdidas
     */
    getLapGapToLeader(row, session) {
        const leader = this.getSortedResults(session)[0];
        const leaderLaps = Number(leader?.number_of_laps || leader?.laps);
        const currentLaps = Number(row.number_of_laps || row.laps);
        return leaderLaps && currentLaps && leaderLaps > currentLaps ? leaderLaps - currentLaps : 0;
    }

    buildRowsFromLaps(session) {
        const data = this.getSessionData(session);
        const bestByDriver = new Map();
        (data?.laps || []).forEach((lap) => {
            if (!lap.driver_number) return;
            const key = Number(lap.driver_number);
            const current = bestByDriver.get(key);
            const lapTime = Number(lap.lap_duration) || null;
            if (!current || (lapTime && (!Number(current.best_lap_time) || lapTime < Number(current.best_lap_time)))) {
                bestByDriver.set(key, {
                    driver_number: key,
                    best_lap_time: lapTime,
                    laps: this.countDriverLaps(data.laps, key)
                });
            }
        });
        return [...bestByDriver.values()]
            .sort((left, right) => (Number(left.best_lap_time) || 9999) - (Number(right.best_lap_time) || 9999) || Number(left.driver_number) - Number(right.driver_number))
            .map((row, index) => ({ ...row, position: index + 1 }));
    }

    countDriverLaps(laps, driverNumber) {
        return laps.filter((lap) => Number(lap.driver_number) === Number(driverNumber) && Number(lap.lap_number)).length;
    }

    getLapStats(row, session) {
        const data = this.getSessionData(session);
        const laps = (data?.laps || []).filter((lap) => Number(lap.driver_number) === Number(row.driver_number));
        const validLaps = laps.filter((lap) => Number(lap.lap_duration));
        const best = validLaps.sort((left, right) => Number(left.lap_duration) - Number(right.lap_duration))[0];
        return {
            laps: row.number_of_laps || row.laps || laps.length || '-',
            bestLap: row.best_lap_time || row.duration || best?.lap_duration || null
        };
    }

    formatBestLapGap(row, session, index) {
        const rows = this.getSortedResults(session);
        const best = this.toSeconds(this.getLapStats(rows[0] || {}, session).bestLap);
        const current = this.toSeconds(this.getLapStats(row, session).bestLap);
        if (!current) return '-';
        if (index === 0 || !best) return this.formatLapTime(current);
        return `+${(current - best).toFixed(3)}`;
    }

    formatQualifyingData(row, session, index) {
        const times = this.extractArrayValues(row.duration);
        const gaps = this.extractArrayValues(row.gap_to_leader);
        const stats = this.getLapStats(row, session);
        const q1 = this.formatRawLapTime(times[0] ?? row.q1 ?? stats.bestLap);
        const q2 = this.formatRawLapTime(times[1] ?? row.q2);
        const q3 = this.formatRawLapTime(times[2] ?? row.q3);
        const lastGap = this.lastUsableValue(gaps);
        return {
            q1,
            q2,
            q3,
            gap: index === 0 ? (q3 !== '-' ? q3 : q2 !== '-' ? q2 : q1) : (lastGap !== null ? this.formatSignedGap(lastGap) : this.formatBestLapGap(row, session, index))
        };
    }

    extractArrayValues(value) {
        if (Array.isArray(value)) return value;
        if (typeof value === 'string' && value.includes(',')) return value.split(',').map((part) => part.trim());
        return value === null || value === undefined || value === '' ? [] : [value];
    }

    lastUsableValue(value) {
        const values = this.extractArrayValues(value).filter((item) => item !== null && item !== undefined && item !== '' && item !== 0 && item !== '0');
        return values.length ? values[values.length - 1] : null;
    }

    formatSignedGap(value) {
        return this.formatGapValue(value);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea gaps por tiempo o por vueltas conservando la informacion de OpenF1
     * @param {number|string} value Gap original
     * @returns {string} Gap visible
     */
    formatGapValue(value) {
        const raw = `${value ?? ''}`.trim();
        if (!raw) return '-';
        if (/lap|vuelta/i.test(raw)) {
            const laps = Number(raw.match(/\d+/)?.[0] || 1);
            return this.formatLapGap(laps);
        }
        const seconds = this.toSeconds(value);
        if (seconds === null) return '-';
        return seconds > 0 ? `+${seconds.toFixed(3)}` : 'Líder';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea gaps de una o varias vueltas
     * @param {number} laps Vueltas perdidas
     * @returns {string} Gap por vueltas
     */
    formatLapGap(laps) {
        return `+${laps} ${laps === 1 ? 'vuelta' : 'vueltas'}`;
    }

    formatRawLapTime(value) {
        const seconds = this.toSeconds(value);
        return seconds ? this.formatLapTime(seconds) : '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea duraciones largas de carrera como horas, minutos y segundos
     * @param {number|string} value Duracion
     * @returns {string} Tiempo visible
     */
    formatRawRaceTime(value) {
        const seconds = this.toSeconds(value);
        return seconds ? this.formatRaceTime(seconds) : '-';
    }

    toSeconds(value) {
        if (value == null || value === '-') return null;
        if (typeof value === 'number') return value;
        const parts = `${value}`.split(':').map(Number);
        if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
        if (parts.length === 2) return parts[0] * 60 + parts[1];
        return Number(value) || null;
    }

    formatLapTime(seconds) {
        const minutes = Math.floor(seconds / 60);
        const rest = (seconds - minutes * 60).toFixed(3).padStart(6, '0');
        return minutes ? `${minutes}:${rest}` : seconds.toFixed(3);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Formatea tiempos totales de carrera
     * @param {number} seconds Segundos
     * @returns {string} Tiempo en H:MM:SS.mmm o M:SS.mmm
     */
    formatRaceTime(seconds) {
        if (seconds < 3600) return this.formatLapTime(seconds);
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds - hours * 3600) / 60);
        const rest = (seconds - hours * 3600 - minutes * 60).toFixed(3).padStart(6, '0');
        return `${hours}:${String(minutes).padStart(2, '0')}:${rest}`;
    }

    getRacePoints(position, session) {
        const sprint = /sprint/i.test(`${session?.session_name || ''} ${session?.session_type || ''}`);
        const scale = sprint ? [8, 7, 6, 5, 4, 3, 2, 1] : [25, 18, 15, 12, 10, 8, 6, 4, 2, 1];
        return scale[Number(position) - 1] || 0;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Devuelve la temporada real de una sesion para usar imagenes historicas correctas
     * @param {Object} session Sesion
     * @returns {number} Temporada
     */
    getSessionYear(session) {
        const meetingYear = Number(this.selectedMeeting?.year);
        if (Number.isInteger(meetingYear)) return meetingYear;
        const value = session?.date_start || session?.date_end || this.selectedMeeting?.date_start;
        const parsedYear = value ? new Date(value).getFullYear() : this.year;
        return Number.isInteger(parsedYear) ? parsedYear : this.year;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Normaliza nombres de equipos para rutas de assets de Formula 1
     * @param {string} value Texto original
     * @returns {string} Clave normalizada
     */
    normalizeAssetKey(value) {
        return `${value || ''}`.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 12-05-2026
     * @description Lee un array usando el cliente comun con reintento de vacios
     * @param {string} url URL
     * @param {number} attempts Intentos
     * @param {number} delayBase Espera base
     * @returns {Promise<Array>} Array confirmado
     */
    async fetchArrayWithRetry(url, attempts = 5, delayBase = 260) {
        return window.RaceStreamApi.fetchArray(url, { attempts, delayBase });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 03-05-2026
     * @modified 12-05-2026
     * @description Lee JSON con cache local segura desde el cliente comun
     * @param {string} url URL
     * @param {*} fallback Valor por defecto
     * @param {Object} options Opciones de reintento
     * @returns {Promise<*>} JSON confirmado
     */
    async fetchJson(url, fallback, options = {}) {
        return window.RaceStreamApi.fetchJson(url, fallback, options);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 14-05-2026
     * @description Calcula el estado del GP con fechas válidas y evita falsos completados
     * @param {Object} meeting Gran Premio
     * @returns {string} Estado interno
     */
    getMeetingStatus(meeting) {
        if (meeting?.is_cancelled) return 'cancelled';
        const now = Date.now();
        const start = new Date(meeting.date_start).getTime();
        const end = new Date(meeting.date_end).getTime();
        if (Number.isFinite(start) && Number.isFinite(end) && now >= start && now <= end) return 'live';
        if (Number.isFinite(end)) return now > end ? 'completed' : 'upcoming';
        return 'upcoming';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Confirma que el GP tiene clave real de OpenF1 para poder pedir resultados
     * @param {Object} meeting Gran Premio
     * @returns {boolean} Resultado
     */
    hasRealMeetingKey(meeting) {
        return Number(meeting?.meeting_key) > 0 && !meeting?.is_jolpica_fallback;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Permite meetings reales OpenF1 y meetings historicos sinteticos de Jolpica
     * @param {Object} meeting Gran Premio
     * @returns {boolean} Resultado
     */
    hasUsableMeetingKey(meeting) {
        const key = Number(meeting?.meeting_key);
        return Number.isFinite(key) && key !== 0;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Conserva temporada y GP de sesiones al recargar
     * @param {number} year Temporada
     * @param {number|null} meetingKey GP seleccionado
     */
    updateUrl(year, meetingKey) {
        const params = new URLSearchParams();
        params.set('year', year || this.year);
        if (meetingKey) params.set('meetingKey', meetingKey);
        window.history.replaceState({}, '', `?${params.toString()}`);
    }

    getCurrentOrNextMeeting(meetings) {
        return meetings.find((meeting) => new Date(meeting.date_end).getTime() >= Date.now()) || meetings[meetings.length - 1] || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 14-05-2026
     * @description Calcula si una sesión está completada sin tratar fechas ausentes como próximas por error
     * @param {Object} session Sesión
     * @param {Object|null} meeting GP asociado
     * @returns {string} Estado interno
     */
    getSessionStatus(session, meeting = this.selectedMeeting) {
        if (session?.is_cancelled) return 'cancelled';
        const now = Date.now();
        const start = new Date(session?.date_start).getTime();
        const end = new Date(session?.date_end).getTime();
        if (Number.isFinite(start) && Number.isFinite(end) && now >= start && now <= end) return 'live';
        if (Number.isFinite(end)) return now > end ? 'completed' : 'upcoming';
        return this.getMeetingStatus(meeting) === 'completed' ? 'completed' : 'upcoming';
    }

    getSessionTypeClass(type) {
        const normalized = `${type || ''}`.toLowerCase();
        const kind = normalized.includes('qualifying') ? 'qualifying' : normalized.includes('race') || normalized.includes('sprint') ? 'race' : 'practice';
        return `rs-session-row rs-session-row--${kind}`;
    }

    isQualifyingSession(session) {
        return /qualifying|clasif/i.test(`${session?.session_type || ''} ${session?.session_name || ''}`);
    }

    translateSessionName(name) {
        return {
            'Practice 1': 'Libres 1',
            'Practice 2': 'Libres 2',
            'Practice 3': 'Libres 3',
            'Free Practice 1': 'Libres 1',
            'Free Practice 2': 'Libres 2',
            'Free Practice 3': 'Libres 3',
            'Sprint Qualifying': 'Clasif. sprint',
            'Sprint Shootout': 'Clasif. sprint',
            Qualifying: 'Clasificación',
            Race: 'Carrera',
            Sprint: 'Sprint',
            'Day 1': 'Día 1',
            'Day 2': 'Día 2',
            'Day 3': 'Día 3'
        }[name] || name || 'Sesión';
    }

    translateSessionType(type) {
        return ({ Practice: 'Practice', Qualifying: 'Qualifying', Race: 'Race', Sprint: 'Race' })[type] || type || 'Sesión';
    }

    formatDateTime(value) {
        return value ? new Date(value).toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' }) : '-';
    }

    formatDateRange(startValue, endValue) {
        const start = new Date(startValue);
        const end = new Date(endValue);
        const month = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const sameDay = start.getFullYear() === end.getFullYear()
            && start.getMonth() === end.getMonth()
            && start.getDate() === end.getDate();
        return sameDay ? `${month} ${start.getDate()}` : `${month} ${start.getDate()}-${end.getDate()}`;
    }

    getCircuitNowTime(offset) {
        const date = new Date(Date.now() + this.parseOffset(offset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    parseOffset(offset) {
        const sign = `${offset}`.startsWith('-') ? -1 : 1;
        const [hours = '0', minutes = '0'] = `${offset}`.replace('+', '').replace('-', '').split(':');
        return sign * (Number(hours) * 60 + Number(minutes));
    }

    getCountdown(sessionName, value) {
        const diff = new Date(value).getTime() - Date.now();
        if (diff <= 0) return 'En curso';
        const days = Math.floor(diff / 86400000);
        const hours = Math.floor((diff % 86400000) / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);
        return `${sessionName || 'Sesión'} en ${days}d ${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 12-05-2026
     * @description Espera no bloqueante para dosificar llamadas a APIs
     * @param {number} milliseconds Milisegundos
     * @returns {Promise<void>} Promesa de espera
     */
    wait(milliseconds) {
        return window.RaceStreamApi.wait(milliseconds);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamSessionsPage = new RaceStreamSessionsPage();
});
