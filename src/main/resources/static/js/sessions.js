/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.4.0
 * @created 30-04-2026
 * @modified 05-05-2026
 * @description Lógica de Sesiones con selector de GP, carga reforzada, podios, placeholder de pilotos y tablas desplegables
 */
class RaceStreamSessionsPage {

    constructor() {
        this.initialParams = new URLSearchParams(window.location.search);
        const requestedYear = Number(this.initialParams.get('year'));
        this.year = Number.isInteger(requestedYear) && requestedYear >= 2023 ? requestedYear : new Date().getFullYear();
        this.raceStripYear = new Date().getFullYear();
        this.currentMeetingApi = `/api/f1/schedule/current-or-next-meeting?year=${this.raceStripYear}`;
        this.meetingsApi = (year) => `/api/f1/schedule/calendar-meetings?year=${year}`;
        this.sessionsApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.resultsApi = (sessionKey) => `/api/f1/session-results?sessionKey=${sessionKey}`;
        this.driversApi = (sessionKey) => `/api/f1/drivers?sessionKey=${sessionKey}`;
        this.lapsApi = (sessionKey) => `/api/f1/live/laps?sessionKey=${sessionKey}`;
        this.mediaApi = (number) => `/api/f1/media/driver?number=${number}`;
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
        this.sessionData = new Map();
        this.selectionRequestId = 0;
        this.meetingsRequestId = 0;
        this.raceStripRequestId = 0;
        this.bindEvents();
        this.loadRaceStripMeeting();
        this.loadMeetings();
        setInterval(() => {
            this.updateRaceStripClocks();
            this.renderRaceStripAction();
        }, 1000);
    }

    bindEvents() {
        this.yearInput.value = String(this.year);
        this.yearInput.addEventListener('change', () => this.loadMeetings(Number(this.yearInput.value)));
        this.meetingSelect.addEventListener('change', () => this.selectMeeting(Number(this.meetingSelect.value)));
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

    async loadMeetings(year = this.year) {
        const requestId = ++this.meetingsRequestId;
        this.selectionRequestId++;
        this.year = year;
        this.sessionData.clear();
        this.selectedMeeting = null;
        this.meetingSelect.disabled = true;
        this.meetingSelect.value = '';
        this.meetingSelect.innerHTML = '<option value="">Cargando...</option>';
        this.sessionsList.innerHTML = '<p class="loading-state">Cargando Grandes Premios completados...</p>';
        this.renderSelectedMeetingInfo(null);
        this.updateSelectedTitle(null);
        this.meetings = await this.fetchMeetingsWithRetry(this.year, requestId);
        if (requestId !== this.meetingsRequestId) {
            return;
        }
        if (!Array.isArray(this.meetings) || !this.meetings.length) {
            this.meetingSelect.innerHTML = '<option value="">Sin datos</option>';
            this.sessionsList.innerHTML = '<p class="empty-state">No hay sesiones disponibles.</p>';
            this.renderSelectedMeetingInfo(null);
            this.updateSelectedTitle(null);
            return;
        }

        this.selectableMeetings = this.meetings.filter((meeting) => this.getMeetingStatus(meeting) === 'completed' && this.hasRealMeetingKey(meeting));
        this.meetingSelect.innerHTML = this.selectableMeetings.map((meeting) => `
            <option value="${meeting.meeting_key}">${meeting.meeting_name}</option>
        `).join('');

        const requestedMeetingKey = Number(this.initialParams.get('meetingKey'));
        const selected = this.selectableMeetings.find((meeting) => meeting.meeting_key === requestedMeetingKey)
            || [...this.selectableMeetings].reverse()[0];
        if (!selected) {
            this.meetingSelect.innerHTML = '<option value="">Sin GP completados</option>';
            this.sessionsList.innerHTML = '<p class="empty-state">Todavía no hay GP completados para consultar.</p>';
            this.renderSelectedMeetingInfo(null);
            this.updateSelectedTitle(null);
            return;
        }
        this.meetingSelect.disabled = false;
        this.meetingSelect.value = selected.meeting_key;
        this.initialParams.delete('meetingKey');
        if (requestId !== this.meetingsRequestId) {
            return;
        }
        await this.selectMeeting(selected.meeting_key);
    }

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
        this.sessionsList.innerHTML = '<p class="loading-state">Cargando sesiones y resultados...</p>';
        const sessions = await this.fetchSessionsWithRetry(meetingKey);
        if (requestId !== this.selectionRequestId) {
            return;
        }
        const safeSessions = Array.isArray(sessions) ? sessions : [];
        await this.loadCompletedSessionsData(safeSessions, requestId);
        if (requestId !== this.selectionRequestId) {
            return;
        }
        this.renderSessions(safeSessions);
        this.expandRequestedSession(safeSessions);
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
            if (lastMeetings.some((meeting) => this.hasRealMeetingKey(meeting))) {
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
        this.sessionsSelectedGp.innerHTML = meeting
            ? `${meeting.country_flag ? `<img class="rs-flag-inline" src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name || 'país'}">` : ''}<span>${meeting.location || meeting.meeting_name || 'GP'} · ${this.formatDateRange(meeting.date_start, meeting.date_end)}</span>`
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
     * @version 1.0.2
     * @created 03-05-2026
     * @modified 04-05-2026
     * @description Carga datos del GP seleccionado en lotes pequeños para reducir espera sin saturar APIs
     * @param {Array} sessions Sesiones del GP
     * @param {number} requestId Identificador de seleccion activa
     */
    async loadCompletedSessionsData(sessions, requestId) {
        const completedSessions = sessions.filter((session) => this.shouldLoadSessionDetails(session));
        const batchSize = 2;
        for (let index = 0; index < completedSessions.length; index += batchSize) {
            if (requestId !== this.selectionRequestId) {
                return;
            }
            await Promise.all(completedSessions.slice(index, index + batchSize).map((session) => this.loadSessionData(session)));
            if (requestId !== this.selectionRequestId) {
                return;
            }
            if (index + batchSize < completedSessions.length) {
                await this.wait(220);
            }
        }
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
        if (!session?.session_key || this.sessionData.has(session.session_key)) {
            return;
        }
        const results = await this.fetchArrayWithRetry(this.resultsApi(session.session_key), 5, 260);
        const safeResults = Array.isArray(results) ? results : [];
        const [drivers, laps] = await Promise.all([
            this.fetchArrayWithRetry(this.driversApi(session.session_key), 4, 220),
            safeResults.length ? Promise.resolve([]) : this.fetchArrayWithRetry(this.lapsApi(session.session_key), 5, 260)
        ]);
        const driverMap = new Map((Array.isArray(drivers) ? drivers : []).map((driver) => [Number(driver.driver_number), driver]));
        this.sessionData.set(session.session_key, {
            results: safeResults,
            laps: Array.isArray(laps) ? laps : [],
            driverMap
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
            const status = this.getSessionStatus(session);
            const isFinished = status !== 'upcoming' && status !== 'cancelled';
            const hasData = isFinished && this.hasSessionData(session);
            const pendingText = session.session_key ? 'Datos no disponibles' : 'Sin resultados oficiales';
            return `
                <article class="${this.getSessionTypeClass(session.session_type)}" data-session-index="${index}">
                    <div class="rs-session-row__date"><strong>${date.getDate()}</strong><span>${date.toLocaleDateString('en-US', { month: 'short' }).toUpperCase()}</span></div>
                    <div class="rs-session-row__main">
                        <div class="rs-session-row__title"><h4>${this.translateSessionName(session.session_name)}</h4></div>
                        <div class="rs-session-row__meta">${this.translateSessionType(session.session_type)}</div>
                    </div>
                    <div class="rs-session-row__summary">
                        ${this.renderFavoriteButton(session, index)}
                        ${hasData ? this.renderSessionPodium(session) : `<div class="rs-session-row__pending">${isFinished ? pendingText : 'Por definir'}</div>`}
                    </div>
                    ${hasData ? `<div class="rs-session-row__details">
                        ${this.renderResultTable(session)}
                    </div>` : ''}
                </article>
            `;
        }).join('') || '<p class="empty-state">No hay sesiones disponibles para este GP.</p>';

        this.sessionsList.querySelectorAll('.rs-session-row').forEach((row) => {
            row.addEventListener('click', () => this.toggleSession(Number(row.dataset.sessionIndex)));
        });
        this.hydrateDriverImages();
        window.RaceStreamFavorites?.bind(this.sessionsList);
    }

    renderFavoriteButton(session, index) {
        if (!window.RaceStreamFavorites) return '';
        const params = new URLSearchParams({
            year: String(this.year),
            meetingKey: String(this.selectedMeeting?.meeting_key || ''),
            sessionIndex: String(index)
        });
        if (session?.session_key) params.set('sessionKey', session.session_key);
        return window.RaceStreamFavorites.button({
            type: 'Sesión',
            externalId: session?.session_key || `${this.selectedMeeting?.meeting_key || 'gp'}-${index}`,
            title: `${this.selectedMeeting?.meeting_name || 'GP'} · ${this.translateSessionName(session?.session_name)}`,
            url: `/sessions.html?${params.toString()}`,
            description: `${this.formatDateTime(session?.date_start)} · ${this.translateSessionType(session?.session_type)}`
        });
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
                    const image = driver?.headshot_url || this.getDriverFallbackImage(name, driver?.team_name || row.team_name, this.getSessionYear(session), 64) || '';
                    return `<span class="rs-session-podium__item"><strong>${index + 1}</strong><span class="rs-person-avatar"><img src="${image}" alt="Foto de ${name}" loading="lazy" onerror="this.onerror=null;this.closest('.rs-person-avatar').classList.add('rs-person-avatar--fallback');this.removeAttribute('src');"></span></span>`;
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
        return Boolean(session?.session_key) && !['upcoming', 'cancelled'].includes(this.getSessionStatus(session));
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
        const data = this.sessionData.get(session?.session_key);
        return Boolean(data?.results?.length || data?.laps?.length);
    }

    renderResultTable(session) {
        const rows = this.getSortedResults(session);
        if (!rows.length) {
            return '<p class="empty-state">OpenF1 todavía no ofrece resultados para esta sesión.</p>';
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
        const year = this.getSessionYear(session);
        const fallback = this.getDriverFallbackImage(name, driver?.team_name || row.team_name, year);
        const number = row.driver_number || driver?.driver_number || '';
        const image = fallback || driver?.headshot_url || '';
        return `<span class="rs-session-driver"><span class="rs-session-driver__avatar rs-person-avatar"><img src="${image}" data-driver-number="${number}" data-media-url="${number ? this.mediaApi(number) : ''}" alt="Foto de ${name}" onerror="this.onerror=null;this.closest('.rs-person-avatar').classList.add('rs-person-avatar--fallback');this.removeAttribute('src');"></span>${name}</span>`;
    }

    renderTeamCell(driver, row, session) {
        const teamName = driver?.team_name || row.team_name || '-';
        const logo = this.getTeamLogo(teamName, this.getSessionYear(session));
        return `<span class="rs-session-team"><img src="${logo}" alt="Logo de ${teamName}" onerror="this.style.display='none';">${teamName}</span>`;
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
        this.sessionsList.querySelectorAll('img[data-media-url^="/api/f1/media/driver"]').forEach(async (image) => {
            if (image.getAttribute('src')) {
                image.closest('.rs-person-avatar')?.classList.remove('rs-person-avatar--fallback');
                return;
            }
            const media = await this.fetchJson(image.dataset.mediaUrl, {});
            if (!media?.headshotUrl || image.src === media.headshotUrl) return;
            this.loadImage(media.headshotUrl)
                .then(() => {
                    image.src = media.headshotUrl;
                    image.closest('.rs-person-avatar')?.classList.remove('rs-person-avatar--fallback');
                })
                .catch(() => {
                    image.removeAttribute('src');
                    image.closest('.rs-person-avatar')?.classList.add('rs-person-avatar--fallback');
                });
        });
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

    toggleSession(index) {
        let expandedRow = null;
        this.sessionsList.querySelectorAll('.rs-session-row').forEach((row, rowIndex) => {
            const expanded = rowIndex === index && !row.classList.contains('rs-session-row--expanded');
            row.classList.toggle('rs-session-row--expanded', expanded);
            row.setAttribute('aria-expanded', expanded);
            if (expanded) expandedRow = row;
        });
        expandedRow?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    expandRequestedSession(sessions) {
        const requestedSessionKey = this.initialParams.get('sessionKey');
        const hasRequestedIndex = this.initialParams.has('sessionIndex');
        const requestedIndex = hasRequestedIndex ? Number(this.initialParams.get('sessionIndex')) : -1;
        const index = requestedSessionKey
            ? sessions.findIndex((session) => `${session.session_key}` === requestedSessionKey)
            : requestedIndex;
        if ((requestedSessionKey || hasRequestedIndex) && Number.isInteger(index) && index >= 0) {
            this.toggleSession(index);
            this.sessionsList.querySelector(`[data-session-index="${index}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
        this.raceStripAction.innerHTML = isLive
            ? '<a class="rs-button rs-button--primary" href="/live.html">En Vivo</a>'
            : `<span class="rs-race-strip__status">${this.getCountdown(this.translateSessionName(session?.session_name), session?.date_start || this.topMeeting.date_start)}</span>`;
    }

    updateRaceStripClocks() {
        if (!this.topMeeting) return;
        this.raceStripClocks.innerHTML = `
            <div class="rs-race-strip__clock-card">
                <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-label">MI HORA</span><strong class="rs-race-strip__clock-value">${new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}</strong></div>
                <span class="rs-race-strip__clock-divider"></span>
                <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-subvalue">CIRCUITO</span><span class="rs-race-strip__clock-track-value">${this.getCircuitNowTime(this.topMeeting.gmt_offset)}</span></div>
            </div>
        `;
    }

    getSortedResults(session) {
        const data = this.sessionData.get(session?.session_key);
        const rows = (data?.results?.length ? data.results : this.buildRowsFromLaps(session));
        return [...rows].sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999));
    }

    getDriver(row, session) {
        return this.sessionData.get(session?.session_key)?.driverMap.get(Number(row.driver_number));
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
        const data = this.sessionData.get(session?.session_key);
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
        const data = this.sessionData.get(session?.session_key);
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

    getTeamLogo(value, year = this.year) {
        const team = this.resolveTeamLogoSlug(value, year);
        if (!team || team === '-') return '/assets/img/LogoRS2.png';
        const file = team.replace(/-/g, '');
        return `https://media.formula1.com/image/upload/c_lfill,w_64/q_auto/v1740000001/common/f1/${year}/${team}/${year}${file}logowhite.webp`;
    }

    resolveTeamLogoSlug(value, year = this.year) {
        const key = this.normalizeAssetKey(value);
        if (key.includes('red-bull')) return 'redbullracing';
        if (key.includes('alpha-tauri') || key.includes('alphatauri')) return 'alphatauri';
        if (key.includes('racing-bulls') || key.includes('visa-cash-app') || key === 'rb') return year <= 2023 ? 'alphatauri' : 'racingbulls';
        if (key.includes('aston-martin')) return 'astonmartin';
        if (key.includes('haas')) return 'haas';
        if (key.includes('alfa-romeo')) return 'alfaromeo';
        if (key.includes('kick') || key.includes('stake') || key.includes('sauber')) return year <= 2023 ? 'alfaromeo' : 'kicksauber';
        if (key.includes('mercedes')) return 'mercedes';
        if (key.includes('ferrari')) return 'ferrari';
        if (key.includes('mclaren')) return 'mclaren';
        if (key.includes('williams')) return 'williams';
        if (key.includes('alpine')) return 'alpine';
        return key;
    }

    getRacePoints(position, session) {
        const sprint = /sprint/i.test(`${session?.session_name || ''} ${session?.session_type || ''}`);
        const scale = sprint ? [8, 7, 6, 5, 4, 3, 2, 1] : [25, 18, 15, 12, 10, 8, 6, 4, 2, 1];
        return scale[Number(position) - 1] || 0;
    }

    getDriverFallbackImage(name, teamName = '', year = this.year, width = 64) {
        const displayName = `${name || ''}`.replace(/^Andrea\s+Kimi\s+Antonelli$/i, 'Kimi Antonelli');
        const clean = displayName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z\s-]/g, '').trim();
        const parts = clean.split(/\s+/).filter(Boolean);
        if (parts.length < 2) return '/assets/img/LogoRS2.png';
        const given = parts[0];
        const family = this.getDriverFamilyCode(parts);
        const assetId = `${given.slice(0, 3)}${family.slice(0, 3)}01`;
        const team = this.resolveTeamLogoSlug(teamName, year) || (assetId === 'arvlin01' ? 'racingbulls' : '');
        if (team && team !== '-') {
            return `https://media.formula1.com/image/upload/c_lfill,w_${width}/q_auto/d_common:f1:${year}:fallback:driver:${year}fallbackdriverright.webp/v1740000001/common/f1/${year}/${team}/${assetId}/${year}${team}${assetId}right.webp`;
        }
        const folder = assetId.charAt(0).toUpperCase();
        const displayName = `${this.capitalize(given)}_${this.capitalize(family)}`;
        return `https://www.formula1.com/content/dam/fom-website/drivers/${folder}/${assetId.toUpperCase()}_${displayName}/${assetId}.png.transform/1col/image.png`;
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
     * @version 1.0.0
     * @created 04-05-2026
     * @description Mantiene apellidos compuestos compatibles con el formato oficial de pilotos
     * @param {Array<string>} parts Nombre dividido
     * @returns {string} Apellido para asset
     */
    getDriverFamilyCode(parts) {
        const particles = new Set(['da', 'de', 'del', 'do', 'dos', 'du', 'van', 'von']);
        const previous = parts[parts.length - 2];
        return parts.length > 2 && particles.has(previous) ? `${previous}${parts[parts.length - 1]}` : parts[parts.length - 1];
    }

    capitalize(value) {
        return value ? value.charAt(0).toUpperCase() + value.slice(1) : value;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Lee un array esperando si la API devuelve vacio de forma temporal
     * @param {string} url URL
     * @param {number} attempts Intentos
     * @param {number} delayBase Espera base
     * @returns {Promise<Array>} Array confirmado
     */
    async fetchArrayWithRetry(url, attempts = 5, delayBase = 260) {
        const data = await this.fetchJson(url, [], { retryEmpty: true, attempts, delayBase });
        return Array.isArray(data) ? data : [];
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @modified 03-05-2026
     * @description Lee JSON con cache local y opcion de no aceptar arrays vacios temporales
     * @param {string} url URL
     * @param {*} fallback Valor por defecto
     * @param {Object} options Opciones de reintento
     * @returns {Promise<*>} JSON confirmado
     */
    async fetchJson(url, fallback, options = {}) {
        const cacheKey = `rs-cache:${url}`;
        const attempts = options.attempts ?? 3;
        const delayBase = options.delayBase ?? 180;
        for (let attempt = 0; attempt < attempts; attempt++) {
            try {
                const response = await fetch(url, { cache: 'no-store' });
                if (response.ok) {
                    const data = await response.json();
                    if (options.retryEmpty && Array.isArray(data) && !data.length) {
                        if (attempt < attempts - 1) {
                            await this.wait(delayBase * (attempt + 1));
                            continue;
                        }
                        break;
                    }
                    if (!(Array.isArray(data) && !data.length)) {
                        localStorage.setItem(cacheKey, JSON.stringify(data));
                    }
                    return data;
                }
            } catch {
                if (attempt < attempts - 1) {
                    await this.wait(delayBase * (attempt + 1));
                }
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

    getMeetingStatus(meeting) {
        if (meeting?.is_cancelled) return 'cancelled';
        const now = Date.now();
        const start = new Date(meeting.date_start).getTime();
        const end = new Date(meeting.date_end).getTime();
        if (now >= start && now <= end) return 'live';
        return now > end ? 'completed' : 'upcoming';
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

    getCurrentOrNextMeeting(meetings) {
        return meetings.find((meeting) => new Date(meeting.date_end).getTime() >= Date.now()) || meetings[meetings.length - 1] || null;
    }

    getSessionStatus(session) {
        if (session?.is_cancelled) return 'cancelled';
        if (Date.now() >= new Date(session.date_start).getTime() && Date.now() <= new Date(session.date_end).getTime()) return 'live';
        return Date.now() > new Date(session.date_end).getTime() ? 'completed' : 'upcoming';
    }

    getSessionStatusLabel(session) {
        return { cancelled: 'Cancelado', live: 'En vivo', completed: 'Completado', upcoming: 'Próximo' }[this.getSessionStatus(session)];
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
        return ({ 'Practice 1': 'Libres 1', 'Practice 2': 'Libres 2', 'Practice 3': 'Libres 3', 'Free Practice 1': 'Libres 1', 'Free Practice 2': 'Libres 2', 'Free Practice 3': 'Libres 3', 'Sprint Qualifying': 'Clasif. sprint', 'Sprint Shootout': 'Clasif. sprint', Qualifying: 'Clasificación', Race: 'Carrera', Sprint: 'Sprint', 'Day 1': 'Día 1', 'Day 2': 'Día 2', 'Day 3': 'Día 3' })[name] || name || 'Sesión';
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
        return `${month} ${start.getDate()}-${end.getDate()}`;
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
        return `${sessionName || 'Sesión'} en ${days}d ${hours}h ${minutes}m`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera no bloqueante para dosificar llamadas a APIs
     * @param {number} milliseconds Milisegundos
     * @returns {Promise<void>} Promesa de espera
     */
    wait(milliseconds) {
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamSessionsPage = new RaceStreamSessionsPage();
});
