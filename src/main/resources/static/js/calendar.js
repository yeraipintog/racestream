/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.6.1
 * @created 21-04-2026
 * @modified 31-05-2026
 * @description Lógica principal del calendario F1 RaceStream con fechas compactas, histórico autenticado, imágenes seguras, carga reforzada y rango visual máximo de tres días
 */
class RaceStreamCalendarPage {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 21-04-2026
     * @modified 08-05-2026
     * @description Constructor principal
     */
    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.meetingsApi = '/api/f1/schedule/calendar-meetings';
        this.sessionsByMeetingApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;

        this.monthNames = [
            'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
            'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
        ];

        this.weekDays = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

        this.allMeetings = [];
        this.selectedMeetingKey = null;
        this.currentMonthIndex = 0;
        this.lastValidTopMeeting = null;
        this.params = new URLSearchParams(window.location.search);
        const requestedYear = Number(this.params.get('year'));
        this.currentSeason = Number.isInteger(requestedYear) && requestedYear >= 1950 ? requestedYear : new Date().getFullYear();
        const requestedMeetingKey = Number(this.params.get('meetingKey'));
        this.pendingMeetingKey = Number.isInteger(requestedMeetingKey) && requestedMeetingKey !== 0 ? requestedMeetingKey : null;
        this.selectedSessions = [];
        this.topMeetingSessions = [];
        this.ownsRaceStrip = !window.raceStreamRaceStrip;
        this.seasonFilterLocked = false;
        this.loadRequestId = 0;
        this.selectionRequestId = 0;
        this.circuitImages = [
            [['madring'], '/assets/circuits/madring.png'],
            [['montmelo', 'circuit de barcelona catalunya', 'barcelona catalunya'], '/assets/circuits/montmelo.png'],
            [['montecarlo', 'circuit de monaco', 'monte carlo'], '/assets/circuits/montecarlo.png'],
            [['hungaroring'], '/assets/circuits/hungaroring.png']
        ];

        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 21-04-2026
     * @modified 08-05-2026
     * @description Cachea el DOM necesario
     */
    cacheDom() {
        this.yearInput = document.getElementById('calendarYearInput');
        this.topSelectedCircuit = document.getElementById('topSelectedCircuit');

        this.calendarMonthLabel = document.getElementById('calendarMonthLabel');
        this.calendarGrid = document.getElementById('calendarGrid');
        this.calendarEvents = document.getElementById('calendarEvents');
        this.calendarInsight = document.getElementById('calendarInsight');

        this.detailStatus = document.getElementById('detailStatus');
        this.selectedGpTitle = document.getElementById('selectedGpTitle');
        this.selectedGpFavorite = document.getElementById('selectedGpFavorite');
        this.selectedCircuitCard = document.getElementById('selectedCircuitCard');
        this.sessionsContent = document.getElementById('sessionsContent');

        this.raceStripTitle = document.getElementById('raceStripTitle');
        this.raceStripMeta = document.getElementById('raceStripMeta');
        this.raceStripClocks = document.getElementById('raceStripClocks');
        this.raceStripAction = document.getElementById('raceStripAction');
        this.raceStripFlag = document.getElementById('raceStripFlag');

        this.prevMonthButton = document.getElementById('prevMonthButton');
        this.nextMonthButton = document.getElementById('nextMonthButton');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 21-04-2026
     * @modified 08-05-2026
     * @description Asocia eventos
     */
    bindEvents() {
        this.yearInput?.addEventListener('change', () => {
            if (this.seasonFilterLocked) {
                this.yearInput.value = String(this.currentSeason);
                return;
            }
            this.currentSeason = Number(this.yearInput.value) || new Date().getFullYear();
            this.pendingMeetingKey = null;
            this.selectedMeetingKey = null;
            this.updateUrl(null);
            this.loadCalendar();
        });
        this.prevMonthButton.addEventListener('click', () => this.changeMonth(-1));
        this.nextMonthButton.addEventListener('click', () => this.changeMonth(1));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 21-04-2026
     * @modified 27-05-2026
     * @description Inicializa acceso público, temporadas y calendario
     */
    async init() {
        await this.applySeasonAccess();
        await this.loadSeasons();
        await this.loadCalendar();
        if (this.ownsRaceStrip) {
            setInterval(() => {
                this.updateDynamicClocks();
                this.updateCountdown();
            }, 30000);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 28-04-2026
     * @modified 03-05-2026
     * @description Lee JSON de forma segura sin guardar arrays vacíos como respuesta definitiva
     * @param {string} url URL
     * @param {*} fallback Valor por defecto
     * @returns {Promise<*>} JSON o fallback
     */
    async fetchJson(url, fallback = null) {
        return window.RaceStreamApi.fetchJson(url, fallback, { attempts: 3, delayBase: 180 });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Bloquea el filtro histórico para invitados y fuerza la temporada actual
     * @returns {Promise<void>} Acceso aplicado
     */
    async applySeasonAccess() {
        const access = await window.RaceStreamApi.resolveSeasonAccess(this.currentSeason);
        this.seasonFilterLocked = access.locked;
        this.currentSeason = access.year;
        if (this.yearInput) {
            this.yearInput.value = String(this.currentSeason);
        }
        if (access.locked && this.params.get('year') && Number(this.params.get('year')) !== access.currentYear) {
            this.pendingMeetingKey = null;
            this.updateUrl(null);
        }
        window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 13-05-2026
     * @modified 27-05-2026
     * @description Carga temporadas disponibles para filtrar calendario
     */
    async loadSeasons() {
        if (!this.yearInput) return;
        const currentYear = new Date().getFullYear();
        const seasons = await this.fetchJson('/api/f1/standings/seasons', []);
        const safeSeasons = Array.isArray(seasons) && seasons.length
            ? seasons
            : Array.from({ length: currentYear - 1949 }, (_, index) => ({ season: currentYear - index }));
        this.yearInput.innerHTML = safeSeasons
            .map((item) => `<option value="${item.season}" ${Number(item.season) === this.currentSeason ? 'selected' : ''}>${item.season}</option>`)
            .join('');
        window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @modified 22-04-2026
     * @description Convierte offset GMT a minutos
     * @param {string} gmtOffset Offset
     * @returns {number} Minutos
     */
    parseGmtOffsetToMinutes(gmtOffset) {
        if (!gmtOffset) {
            return 0;
        }

        const sign = gmtOffset.startsWith('-') ? -1 : 1;
        const clean = gmtOffset.replace('+', '').replace('-', '');
        const [hours = '0', minutes = '0'] = clean.split(':');

        return sign * ((parseInt(hours, 10) * 60) + parseInt(minutes, 10));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @modified 22-04-2026
     * @description Devuelve fecha local del cliente
     * @param {string} value Fecha ISO
     * @returns {string} Fecha formateada
     */
    formatClientDateTime(value) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');

        return `${day}/${month}, ${hours}:${minutes}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve fecha local del circuito
     * @param {string} value Fecha ISO
     * @param {string} gmtOffset Offset
     * @returns {string} Fecha formateada
     */
    formatCircuitDateTime(value, gmtOffset) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        const adjustedDate = new Date(date.getTime() + this.parseGmtOffsetToMinutes(gmtOffset) * 60000);

        const day = String(adjustedDate.getUTCDate()).padStart(2, '0');
        const month = String(adjustedDate.getUTCMonth() + 1).padStart(2, '0');
        const hours = String(adjustedDate.getUTCHours()).padStart(2, '0');
        const minutes = String(adjustedDate.getUTCMinutes()).padStart(2, '0');

        return `${day}/${month}, ${hours}:${minutes}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 28-04-2026
     * @modified 28-04-2026
     * @description Devuelve rango horario compacto del cliente
     * @param {string} startValue Fecha inicio
     * @param {string} endValue Fecha fin
     * @returns {string} Rango
     */
    formatClientDateTimeRange(startValue, endValue) {
        return `${this.formatTimeOnly(startValue)} - ${this.formatTimeOnly(endValue)}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Devuelve fecha vertical de sesión en hora local del visitante
     * @param {string} value Fecha ISO
     * @param {string} gmtOffset Offset
     * @returns {{day: string, month: string, label: string}} Fecha
     */
    formatClientDateBadge(value) {
        if (!value) {
            return { day: '--', month: '---', label: 'Fecha no disponible' };
        }

        const date = new Date(value);
        const day = String(date.getDate());
        const month = date.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();

        return { day, month, label: `${day} ${month}` };
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Devuelve solo hora para construir rangos compactos
     * @param {string} value Fecha ISO
     * @param {string|null} gmtOffset Offset circuito
     * @param {boolean} useCircuitTime Indica si se usa hora del circuito
     * @returns {string} Hora
     */
    formatTimeOnly(value, gmtOffset = null, useCircuitTime = false) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        const adjustedDate = useCircuitTime
            ? new Date(date.getTime() + this.parseGmtOffsetToMinutes(gmtOffset) * 60000)
            : date;
        const hours = useCircuitTime ? adjustedDate.getUTCHours() : adjustedDate.getHours();
        const minutes = useCircuitTime ? adjustedDate.getUTCMinutes() : adjustedDate.getMinutes();

        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Renderiza fecha compacta en hora del circuito
     * @param {string} value Fecha ISO
     * @param {string} gmtOffset Offset
     * @returns {string} HTML
     */
    renderCircuitTimeValue(value, gmtOffset) {
        return this.formatCircuitDateTime(value, gmtOffset);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Normaliza el tipo de circuito recibido desde APIs
     * @param {string} circuitType Tipo original
     * @returns {string} Tipo traducido
     */
    translateCircuitType(circuitType) {
        const normalized = (circuitType || '').toLowerCase();
        const map = {
            permanent: 'Circuito Permanente',
            'temporary - street': 'Circuito Urbano',
            'temporary - road': 'Circuito Temporal',
            'circuito urbano': 'Circuito Urbano',
            'circuito permanente': 'Circuito Permanente',
            'circuito temporal': 'Circuito Temporal'
        };

        if (map[normalized]) {
            return map[normalized];
        }
        if (normalized.includes('street')) {
            return 'Circuito Urbano';
        }
        if (normalized.includes('road')) {
            return 'Circuito Temporal';
        }
        if (normalized.includes('permanent')) {
            return 'Circuito Permanente';
        }

        return circuitType || '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 31-05-2026
     * @description Devuelve fecha normalizada del cliente para pintar calendario
     * @param {string} value Fecha ISO
     * @returns {Date} Fecha
     */
    getClientCalendarDate(value) {
        const date = new Date(value);
        date.setHours(0, 0, 0, 0);
        return date;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 31-05-2026
     * @modified 31-05-2026
     * @description Devuelve el rango visual del GP en hora del cliente limitado a tres días de calendario
     * @param {Object} meeting GP
     * @returns {{start: Date, end: Date}} Rango visual
     */
    getCalendarWeekendRange(meeting) {
        const rawStart = this.getClientCalendarDate(meeting?.date_start);
        const rawEnd = this.getClientCalendarDate(meeting?.date_end);

        if (Number.isNaN(rawStart.getTime()) && Number.isNaN(rawEnd.getTime())) {
            const fallback = new Date(this.currentSeason, 0, 1);
            return { start: fallback, end: fallback };
        }
        if (Number.isNaN(rawStart.getTime())) {
            return { start: rawEnd, end: rawEnd };
        }
        if (Number.isNaN(rawEnd.getTime()) || rawEnd < rawStart) {
            return { start: rawStart, end: rawStart };
        }

        const spanDays = Math.round((rawEnd - rawStart) / 86400000) + 1;
        if (spanDays <= 3) {
            return { start: rawStart, end: rawEnd };
        }

        const end = new Date(meeting.date_end);
        const endMinutes = end.getHours() * 60 + end.getMinutes();
        if (endMinutes <= 240) {
            return { start: rawStart, end: this.addCalendarDays(rawStart, 2) };
        }

        return { start: this.addCalendarDays(rawEnd, -2), end: rawEnd };
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 31-05-2026
     * @modified 31-05-2026
     * @description Suma días a una fecha de calendario sin mutar el valor original
     * @param {Date} date Fecha base
     * @param {number} days Días a sumar
     * @returns {Date} Fecha resultante
     */
    addCalendarDays(date, days) {
        const result = new Date(date);
        result.setDate(result.getDate() + days);
        result.setHours(0, 0, 0, 0);
        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 28-04-2026
     * @description Devuelve rango oficial corto del GP en formato MAY 1-3
     * @param {Object} meeting GP
     * @returns {string} Rango
     */
    formatMeetingDateRangeForStrip(meeting) {
        if (!meeting?.date_start || !meeting?.date_end) {
            return '-';
        }

        const { start, end } = this.getCalendarWeekendRange(meeting);
        return this.formatDateRange(start, end);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Devuelve rangos sin ceros a la izquierda en formato MAY 1-3
     * @param {Date} start Inicio
     * @param {Date} end Fin
     * @returns {string} Rango
     */
    formatDateRange(start, end) {
        const startMonth = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const endMonth = end.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const startDay = start.getDate();
        const endDay = end.getDate();

        if (startMonth === endMonth && startDay === endDay) {
            return `${startMonth} ${startDay}`;
        }

        return startMonth === endMonth
            ? `${startMonth} ${startDay}-${endDay}`
            : `${startMonth} ${startDay} - ${endMonth} ${endDay}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Hora actual cliente
     * @returns {string} Hora
     */
    getClientNowTime() {
        return new Date().toLocaleTimeString('es-ES', {
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Hora actual circuito
     * @param {string} gmtOffset Offset
     * @returns {string} Hora
     */
    getCircuitNowTime(gmtOffset) {
        const offsetMinutes = this.parseGmtOffsetToMinutes(gmtOffset || '+00:00');
        const circuitDate = new Date(Date.now() + offsetMinutes * 60000);

        return `${String(circuitDate.getUTCHours()).padStart(2, '0')}:${String(circuitDate.getUTCMinutes()).padStart(2, '0')}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 22-04-2026
     * @modified 08-05-2026
     * @description Cuenta atrás compacta sin segundos para la franja común
     * @param {string} startDate Fecha
     * @returns {string} Cuenta atrás
     */
    getCountdownToMeeting(startDate) {
        const diff = new Date(startDate).getTime() - Date.now();

        if (diff <= 0) {
            return 'En curso';
        }

        const days = Math.floor(diff / 86400000);
        const hours = Math.floor((diff % 86400000) / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);

        return `${days}d ${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Estado del GP
     * @param {Object} meeting GP
     * @returns {string} Estado
     */
    getMeetingStatus(meeting) {
        if (meeting?.is_cancelled) {
            return 'cancelled';
        }

        const now = new Date();
        const start = new Date(meeting.date_start);
        const end = new Date(meeting.date_end);

        if (now >= start && now <= end) {
            return 'live';
        }

        if (now < start) {
            return 'upcoming';
        }

        return 'completed';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Texto del estado
     * @param {string} status Estado interno
     * @returns {string} Texto
     */
    getStatusLabel(status) {
        if (status === 'cancelled') {
            return 'Cancelado';
        }
        if (status === 'live') {
            return 'En vivo';
        }
        if (status === 'upcoming') {
            return 'Próximo';
        }
        return 'Completado';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Construye la etiqueta visual de estado de un GP
     * @param {string} status Estado interno
     * @returns {string} HTML
     */
    getStatusPillHtml(status) {
        return `<span class="rs-status-pill rs-status-pill--${status}">${this.getStatusLabel(status)}</span>`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Actualiza el estado situado junto al título Detalle del GP
     * @param {Object|null} meeting GP
     */
    renderDetailStatus(meeting) {
        if (!this.detailStatus) {
            return;
        }
        this.detailStatus.innerHTML = meeting
            ? this.getStatusPillHtml(this.getMeetingStatus(meeting))
            : '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Obtiene el GP actual o siguiente desde el calendario ya cargado
     * @param {Array} meetings Calendario
     * @returns {Object|null} GP
     */
    getCurrentOrNextFromMeetings(meetings) {
        return meetings.find(meeting => {
            const status = this.getMeetingStatus(meeting);
            return status === 'live' || status === 'upcoming';
        }) || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Traduce nombre de sesión
     * @param {string} sessionName Nombre
     * @returns {string} Traducción
     */
    translateSessionName(sessionName) {
        const map = {
            'Practice 1': 'Libres 1',
            'Practice 2': 'Libres 2',
            'Practice 3': 'Libres 3',
            'Free Practice 1': 'Libres 1',
            'Free Practice 2': 'Libres 2',
            'Free Practice 3': 'Libres 3',
            'Qualifying': 'Clasificación',
            'Sprint Qualifying': 'Clasif. sprint',
            'Sprint Shootout': 'Clasif. sprint',
            'Sprint': 'Sprint',
            'Race': 'Carrera',
            'Testing': 'Test',
            'Pre-Season Testing': 'Test de pretemporada'
        };

        return map[sessionName] || sessionName || '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Traduce tipo de sesión
     * @param {string} sessionType Tipo
     * @returns {string} Traducción
     */
    translateSessionType(sessionType) {
        const normalized = (sessionType || '').toLowerCase();

        if (normalized.includes('practice')) {
            return 'Practice';
        }
        if (normalized.includes('sprint') && normalized.includes('qual')) {
            return 'Sprint Qualifying';
        }
        if (normalized.includes('qualifying')) {
            return 'Qualifying';
        }
        if (normalized.includes('sprint')) {
            return 'Sprint';
        }
        if (normalized.includes('race')) {
            return 'Race';
        }
        if (normalized.includes('test')) {
            return 'Test';
        }

        return sessionType || '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Devuelve clase CSS de sesión
     * @param {string} sessionType Tipo
     * @returns {string} Clase CSS
     */
    getSessionTypeClass(sessionType) {
        const normalized = (sessionType || '').toLowerCase();

        if (normalized === 'race' || normalized.includes('sprint')) {
            return 'rs-session-row rs-session-row--race';
        }

        if (normalized.includes('qualifying')) {
            return 'rs-session-row rs-session-row--qualifying';
        }

        return 'rs-session-row rs-session-row--practice';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Indica si la sesión está en vivo
     * @param {Object} session Sesión
     * @returns {boolean} Resultado
     */
    isSessionLive(session) {
        const now = new Date();
        return now >= new Date(session.date_start) && now <= new Date(session.date_end);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Indica si la sesión ha terminado
     * @param {Object} session Sesión
     * @returns {boolean} Resultado
     */
    isSessionCompleted(session) {
        return new Date() > new Date(session.date_end);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.4
     * @created 27-04-2026
     * @modified 28-04-2026
     * @description Devuelve estado visual de una sesión del GP
     * @param {Object} session Sesión
     * @param {Object} meeting GP
     * @returns {string} Estado
     */
    getSessionStatus(session, meeting = null) {
        if (meeting?.is_cancelled || (session?.is_cancelled && new Date(session.date_end).getTime() < Date.now())) {
            return 'cancelled';
        }

        if (this.isSessionLive(session)) {
            return 'live';
        }

        if (this.isSessionCompleted(session)) {
            return 'completed';
        }

        return 'upcoming';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 27-04-2026
     * @modified 28-04-2026
     * @description Traduce estado interno de sesión a etiqueta visible
     * @param {string} status Estado interno
     * @returns {string} Etiqueta
     */
    getSessionStatusLabel(status) {
        if (status === 'cancelled') {
            return 'Cancelado';
        }

        if (status === 'live') {
            return 'En vivo';
        }

        if (status === 'completed') {
            return 'Completado';
        }

        return 'Próximo';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 27-04-2026
     * @modified 04-05-2026
     * @description Devuelve botón de acción según el estado de la sesión
     * @param {string} status Estado interno
     * @param {Object} session Sesión
     * @param {Object} meeting GP
     * @returns {string} HTML del botón
     */
    getSessionActionButton(status, session = null, meeting = null, sessionIndex = 0) {
        if (status === 'completed') {
            const params = new URLSearchParams();
            params.set('year', this.currentSeason);
            if (meeting?.meeting_key) params.set('meetingKey', meeting.meeting_key);
            if (session?.session_key) params.set('sessionKey', session.session_key);
            params.set('sessionIndex', sessionIndex);
            return `<a class="rs-link-chip" href="/sessions.html?${params.toString()}">Ver resultados</a>`;
        }

        if (status === 'live') {
            return '<a class="rs-link-chip rs-link-chip--live" href="/live.html">Ver En Vivo</a>';
        }

        return '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Actualiza relojes superiores con el próximo GP
     */
    updateDynamicClocks() {
        if (!this.ownsRaceStrip) return;
        const topMeeting = this.lastValidTopMeeting;

        if (!topMeeting) {
            return;
        }

        if (!this.raceStripClocks.querySelector('.rs-race-strip__clock-card')) {
            this.raceStripClocks.innerHTML = `
                <div class="rs-race-strip__clock-card">
                    <div class="rs-race-strip__clock-row">
                        <span class="rs-race-strip__clock-label">MI HORA</span>
                        <strong class="rs-race-strip__clock-value"></strong>
                    </div>

                    <span class="rs-race-strip__clock-divider"></span>

                    <div class="rs-race-strip__clock-row">
                        <span class="rs-race-strip__clock-subvalue">CIRCUITO</span>
                        <span class="rs-race-strip__clock-track-value"></span>
                    </div>
                </div>
            `;
        }
        this.raceStripClocks.querySelector('.rs-race-strip__clock-value').textContent = this.getClientNowTime();
        this.raceStripClocks.querySelector('.rs-race-strip__clock-track-value').textContent = this.getCircuitNowTime(topMeeting.gmt_offset);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 30-04-2026
     * @description Actualiza acción y cuenta atrás según la siguiente sesión del GP
     */
    updateCountdown() {
        if (!this.ownsRaceStrip) return;
        if (!this.lastValidTopMeeting) {
            return;
        }

        this.renderRaceStripAction();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga sesiones del GP superior para sincronizar sus contadores
     * @param {Object} meeting GP superior
     */
    async loadTopMeetingSessions(meeting) {
        if (!this.ownsRaceStrip) return;
        if (!meeting?.meeting_key) {
            return;
        }

        const meetingKey = meeting.meeting_key;
        const sessions = meeting.is_cancelled && Array.isArray(meeting.cancelled_sessions)
            ? meeting.cancelled_sessions
            : await this.fetchJson(this.sessionsByMeetingApi(meetingKey), []);

        if (this.lastValidTopMeeting?.meeting_key !== meetingKey) {
            return;
        }

        this.topMeetingSessions = (Array.isArray(sessions) ? sessions : [])
            .sort((left, right) => new Date(left.date_start).getTime() - new Date(right.date_start).getTime());
        this.renderRaceStripAction();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Devuelve la sesión activa o la siguiente del GP superior
     * @returns {Object|null} Sesión
     */
    getActiveOrNextTopSession() {
        const now = Date.now();
        return this.topMeetingSessions.find(session => new Date(session.date_end).getTime() >= now) || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Pinta botón En Vivo o cuenta atrás de la siguiente sesión
     */
    renderRaceStripAction() {
        if (!this.ownsRaceStrip) return;
        if (!this.lastValidTopMeeting) {
            return;
        }

        const session = this.getActiveOrNextTopSession();
        const now = Date.now();
        const isSessionLive = session
            && now >= new Date(session.date_start).getTime()
            && now <= new Date(session.date_end).getTime();

        const label = this.translateSessionName(session?.session_name || 'Sesión');
        if (isSessionLive) {
            if (!this.raceStripAction.querySelector('a')) this.raceStripAction.innerHTML = '<a class="rs-button rs-button--primary" href="/live.html">En Vivo</a>';
            return;
        }
        const text = `${label} en ${this.getCountdownToMeeting(session?.date_start || this.lastValidTopMeeting.date_start)}`;
        const status = this.raceStripAction.querySelector('.rs-race-strip__status');
        if (status) {
            status.textContent = text;
        } else {
            this.raceStripAction.innerHTML = `<span id="raceStripCountdown" class="rs-race-strip__status">${text}</span>`;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 30-04-2026
     * @description Actualiza franja superior del próximo GP
     * @param {Object} meeting Próximo GP
     */
    updateRaceStrip(meeting) {
        if (!this.ownsRaceStrip) return;
        const referenceMeeting = meeting?.meeting_name ? meeting : this.lastValidTopMeeting;

        if (!referenceMeeting) {
            this.raceStripTitle.textContent = 'Sin próximo GP disponible';
            this.raceStripMeta.textContent = 'No hay información disponible';
            this.raceStripClocks.textContent = '-';
            this.raceStripFlag.style.display = 'none';
            this.raceStripAction.innerHTML = '<span class="rs-race-strip__status">Sin datos</span>';
            return;
        }

        this.lastValidTopMeeting = referenceMeeting;

        this.raceStripTitle.textContent = referenceMeeting.meeting_name;
        this.raceStripMeta.textContent = this.formatMeetingDateRangeForStrip(referenceMeeting);

        const flagUrl = this.getCountryFlagUrl(referenceMeeting);
        if (flagUrl) {
            this.raceStripFlag.src = flagUrl;
            this.raceStripFlag.style.display = 'inline-block';
        } else {
            this.raceStripFlag.style.display = 'none';
        }

        this.renderRaceStripAction();
        this.loadTopMeetingSessions(referenceMeeting);
        this.updateDynamicClocks();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Actualiza línea superior con circuito seleccionado
     * @param {Object|null} meeting Meeting actual
     */
    updateTopSelectedCircuit(meeting) {
        if (!meeting) {
            this.topSelectedCircuit.textContent = '-';
            this.topSelectedCircuit.className = 'rs-calendar-topbar__selected';
            if (this.selectedGpTitle) {
                this.selectedGpTitle.textContent = 'Selecciona un Gran Premio';
            }
            if (this.selectedGpFavorite) {
                this.selectedGpFavorite.innerHTML = '';
            }
            return;
        }

        const status = this.getMeetingStatus(meeting);
        this.topSelectedCircuit.className = `rs-calendar-topbar__selected rs-calendar-topbar__selected--${status}`;
        this.topSelectedCircuit.innerHTML = `
            ${this.renderCountryFlag(meeting)}
            <span>${meeting.circuit_short_name ?? meeting.location ?? '-'} · ${this.formatMeetingDateRangeForStrip(meeting)}</span>
        `;

        if (this.selectedGpTitle) {
            this.selectedGpTitle.textContent = meeting.meeting_official_name ?? meeting.meeting_name ?? 'Gran Premio';
        }
        if (this.selectedGpFavorite) {
            this.selectedGpFavorite.innerHTML = this.renderFavoriteButton(
                'GP',
                meeting.meeting_key,
                meeting.meeting_name || 'Gran Premio',
                `/calendar.html?year=${this.currentSeason}&meetingKey=${meeting.meeting_key}`,
                `${this.formatGpDateRange(meeting)} · ${meeting.circuit_short_name || meeting.location || ''}`
            );
            window.RaceStreamFavorites?.bind(this.selectedGpFavorite);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 07-05-2026
     * @modified 08-05-2026
     * @description Conserva el GP seleccionado al recargar la página de temporada actual
     * @param {number|null} meetingKey GP seleccionado
     */
    updateUrl(meetingKey) {
        const params = new URLSearchParams();
        params.set('year', this.currentSeason);
        if (meetingKey) params.set('meetingKey', meetingKey);
        window.history.replaceState({}, '', params.toString() ? `?${params.toString()}` : window.location.pathname);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.5
     * @created 22-04-2026
     * @modified 08-05-2026
     * @description Carga el calendario completo reutilizando los datos para no duplicar llamadas externas
     */
    async loadCalendar() {
        const requestId = ++this.loadRequestId;
        this.selectionRequestId++;
        const year = this.currentSeason;
        this.selectedMeetingKey = this.pendingMeetingKey ?? this.selectedMeetingKey;

        this.calendarGrid.innerHTML = '<div class="loading-state">Cargando calendario...</div>';
        if (this.yearInput) {
            this.yearInput.disabled = true;
            this.yearInput.setAttribute('aria-busy', 'true');
        }
        this.calendarEvents.innerHTML = '';
        this.renderCalendarInsight([]);
        this.sessionsContent.innerHTML = '';
        this.selectedCircuitCard.innerHTML = '';
        this.renderDetailStatus(null);
        this.updateTopSelectedCircuit(null);

        try {
            const meetings = await this.fetchJson(`${this.meetingsApi}?year=${year}`, []);
            if (requestId !== this.loadRequestId) {
                return;
            }
            if (this.yearInput) {
                this.yearInput.disabled = this.seasonFilterLocked;
                this.yearInput.removeAttribute('aria-busy');
                window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
            }
            this.allMeetings = Array.isArray(meetings) ? meetings : [];

            this.updateRaceStrip(this.getCurrentOrNextFromMeetings(this.allMeetings));

            if (!this.allMeetings.length) {
                this.renderApiRetry(this.calendarGrid, 'Calendario');
                this.calendarEvents.innerHTML = '';
                this.sessionsContent.innerHTML = '';
                this.selectedCircuitCard.innerHTML = '';
                this.renderDetailStatus(null);
                this.updateTopSelectedCircuit(null);
                return;
            }

            const meetingToSelect = this.selectInitialMeeting();
            this.currentMonthIndex = meetingToSelect
                ? this.getCalendarWeekendRange(meetingToSelect).start.getMonth()
                : 0;

            this.renderMiniCalendar();
            this.renderMonthEvents();

            if (meetingToSelect) {
                await this.selectMeeting(meetingToSelect.meeting_key);
            } else {
                this.sessionsContent.innerHTML = '';
                this.selectedCircuitCard.innerHTML = '';
                this.renderDetailStatus(null);
                this.updateTopSelectedCircuit(null);
            }
        } catch (error) {
            if (requestId !== this.loadRequestId) {
                return;
            }
            if (this.yearInput) {
                this.yearInput.disabled = this.seasonFilterLocked;
                this.yearInput.removeAttribute('aria-busy');
                window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
            }
            console.error('Error cargando calendario:', error);
            this.renderApiRetry(this.calendarGrid, 'Calendario');
            this.calendarEvents.innerHTML = '';
            this.renderCalendarInsight([]);
            this.sessionsContent.innerHTML = '';
            this.selectedCircuitCard.innerHTML = '';
            this.renderDetailStatus(null);
            this.updateTopSelectedCircuit(null);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 22-04-2026
     * @modified 07-05-2026
     * @description Selecciona el GP pedido, actual, siguiente o último disponible
     * @returns {Object|null} Meeting
     */
    selectInitialMeeting() {
        const requestedKey = this.pendingMeetingKey ?? this.selectedMeetingKey;
        const existing = this.allMeetings.find(meeting => Number(meeting.meeting_key) === Number(requestedKey));
        if (existing) {
            return existing;
        }

        const currentOrNext = this.getCurrentOrNextFromMeetings(this.allMeetings);
        return currentOrNext || this.allMeetings[this.allMeetings.length - 1] || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Cambia mes del mini calendario
     * @param {number} direction Direccion
     */
    changeMonth(direction) {
        this.currentMonthIndex += direction;

        if (this.currentMonthIndex < 0) {
            this.currentMonthIndex = 11;
        }
        if (this.currentMonthIndex > 11) {
            this.currentMonthIndex = 0;
        }

        this.renderMiniCalendar();
        this.renderMonthEvents();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 28-04-2026
     * @description Devuelve el GP de un día usando hora de circuito
     * @param {Date} currentDate Fecha
     * @returns {Object|null} Meeting
     */
    getMeetingForCalendarDay(currentDate) {
        return this.allMeetings.find(meeting => {
            const { start, end } = this.getCalendarWeekendRange(meeting);
            return currentDate >= start && currentDate <= end;
        }) || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 22-04-2026
     * @modified 08-05-2026
     * @description Renderiza mini calendario
     */
    renderMiniCalendar() {
        const year = this.currentSeason;
        this.calendarMonthLabel.textContent = `${this.monthNames[this.currentMonthIndex]} ${year}`;

        const firstDay = new Date(year, this.currentMonthIndex, 1);
        const lastDay = new Date(year, this.currentMonthIndex + 1, 0);
        const totalDays = lastDay.getDate();
        const startOffset = (firstDay.getDay() + 6) % 7;

        let html = '';

        this.weekDays.forEach(day => {
            html += `<div class="rs-calendar-weekday">${day}</div>`;
        });

        for (let i = 0; i < startOffset; i++) {
            html += '<div class="rs-calendar-day rs-calendar-day--empty"></div>';
        }

        for (let day = 1; day <= totalDays; day++) {
            const currentDate = new Date(year, this.currentMonthIndex, day);
            const meeting = this.getMeetingForCalendarDay(currentDate);

            if (!meeting) {
                html += `
                    <div class="rs-calendar-day">
                        <span class="rs-calendar-day__number">${day}</span>
                    </div>
                `;
                continue;
            }

            const { start, end } = this.getCalendarWeekendRange(meeting);
            const monthStart = new Date(year, this.currentMonthIndex, 1);
            const monthEnd = new Date(year, this.currentMonthIndex, totalDays);
            const visibleStart = start < monthStart ? monthStart : start;
            const visibleEnd = end > monthEnd ? monthEnd : end;

            if (currentDate.getTime() !== visibleStart.getTime()) {
                continue;
            }

            const status = this.getMeetingStatus(meeting);
            const isActive = this.selectedMeetingKey === meeting.meeting_key ? 'rs-calendar-day--active' : '';
            const spanDays = Math.max(1, Math.round((visibleEnd - visibleStart) / 86400000) + 1);
            html += `
                <div class="rs-calendar-day rs-calendar-day--range rs-calendar-day--${status} ${isActive}" style="grid-column: span ${spanDays};" title="${meeting.meeting_name ?? ''} - ${this.getStatusLabel(status)}" onclick="window.raceStreamCalendarPage.selectMeeting(${meeting.meeting_key})">
                    <span class="rs-calendar-day__number">${this.formatCompactDayRange(start, end)}</span>
                    ${this.renderCountryFlag(meeting, 'rs-calendar-day__flag')}
                </div>
            `;
            day += spanDays - 1;
        }

        this.calendarGrid.innerHTML = html;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Rango corto del GP en formato MAY 1-3
     * @param {Object} meeting GP
     * @returns {string} Texto
     */
    formatGpDateRange(meeting) {
        const { start, end } = this.getCalendarWeekendRange(meeting);
        return this.formatDateRange(start, end);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Devuelve rango compacto del mini calendario evitando duplicar el mismo día
     * @param {Date} start Inicio
     * @param {Date} end Fin
     * @returns {string} Día o rango de días
     */
    formatCompactDayRange(start, end) {
        const sameDay = start.getFullYear() === end.getFullYear()
            && start.getMonth() === end.getMonth()
            && start.getDate() === end.getDate();
        return sameDay ? `${start.getDate()}` : `${start.getDate()}-${end.getDate()}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Renderiza un resumen visual del mes seleccionado
     * @param {Array} meetingsOfMonth GPs del mes
     */
    renderCalendarInsight() {
        if (!this.calendarInsight) {
            return;
        }
        this.calendarInsight.innerHTML = '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 22-04-2026
     * @modified 08-05-2026
     * @description Renderiza lista de eventos del mes y actualiza su resumen
     */
    renderMonthEvents() {
        const year = this.currentSeason;
        const monthStart = new Date(year, this.currentMonthIndex, 1);
        const monthEnd = new Date(year, this.currentMonthIndex + 1, 0);
        const meetingsOfMonth = this.allMeetings.filter(meeting => {
            const { start, end } = this.getCalendarWeekendRange(meeting);
            return start <= monthEnd && end >= monthStart;
        });
        this.renderCalendarInsight(meetingsOfMonth);

        if (!meetingsOfMonth.length) {
            this.calendarEvents.innerHTML = '<div class="empty-state">No hay GP en este mes.</div>';
            return;
        }

        this.calendarEvents.innerHTML = meetingsOfMonth.map(meeting => {
            const status = this.getMeetingStatus(meeting);
            const activeClass = this.selectedMeetingKey === meeting.meeting_key ? 'rs-calendar-events__item--active' : '';

            return `
                <div class="rs-calendar-events__item rs-calendar-events__item--${status} ${activeClass}" onclick="window.raceStreamCalendarPage.selectMeeting(${meeting.meeting_key})">
                    <div class="rs-calendar-events__row">
                        <h5 class="rs-calendar-events__title">
                            ${this.renderCountryFlag(meeting)}
                            <span>${meeting.meeting_name ?? '-'}</span>
                        </h5>
                        <div class="rs-calendar-events__side">
                            <span class="rs-calendar-events__date">${this.formatGpDateRange(meeting)}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 22-04-2026
     * @modified 14-05-2026
     * @description Devuelve imagen local solo cuando el identificador de circuito coincide de forma segura
     * @param {Object} meeting GP
     * @returns {string|null} Ruta
     */
    getCircuitImage(meeting) {
        const values = [
            meeting?.f1db_circuit_id,
            meeting?.circuit_short_name,
            meeting?.jolpica_circuit_name,
            meeting?.circuit_id,
            meeting?.circuit_slug,
            meeting?.circuitId
        ].filter(Boolean).map((value) => this.normalizeCircuitText(value));
        const localImage = this.circuitImages.find(([keys]) => values.some((value) => keys.includes(value)))?.[1];
        if (localImage) return localImage;

        const providedImage = meeting?.circuit_image || '';
        return providedImage && !providedImage.includes('/assets/circuits/') ? providedImage : null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 22-05-2026
     * @description Normaliza nombres de circuito para resolver imágenes locales aunque vengan con acentos o guiones
     * @param {string} value Texto original
     * @returns {string} Texto normalizado
     */
    normalizeCircuitText(value) {
        return (value || '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/\p{M}/gu, '')
            .replace(/[-_]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Devuelve bandera aunque OpenF1 no la incluya en temporadas históricas
     * @param {Object} meeting GP
     * @param {string} className Clase extra
     * @returns {string} HTML de bandera
     */
    renderCountryFlag(meeting, className = '') {
        const country = meeting?.country_name || meeting?.jolpica_country || '';
        const flag = meeting?.country_flag
            ? `<img class="rs-flag-inline ${className}" src="${meeting.country_flag}" alt="Bandera de ${country || 'país'}">`
            : this.assets?.countryFlag(country, className) || '';
        return flag;
    }

    getCountryFlagUrl(meeting) {
        const country = meeting?.country_name || meeting?.jolpica_country || '';
        return meeting?.country_flag || this.assets?.flagUrl(this.assets.getCountryIso2(country)) || '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Devuelve datos de circuito con fallback visible
     * @param {Object} meeting GP
     * @param {string} field Campo
     * @returns {string} Valor
     */
    getCircuitFact(meeting, field) {
        const apiValue = meeting?.[field];
        const value = apiValue == null || `${apiValue}`.trim() === '' || apiValue === '-' ? null : apiValue;
        return value == null || `${value}`.trim() === '' || value === '-' ? 'Por definir' : value;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 22-04-2026
     * @modified 30-04-2026
     * @description Renderiza tarjeta del mapa del circuito con datos técnicos
     * @param {Object} meeting GP
     */
    renderSelectedCircuitCard(meeting) {
        const imageUrl = this.getCircuitImage(meeting);

        if (!meeting) {
            this.selectedCircuitCard.innerHTML = '';
            return;
        }

        const title = `${meeting.location ?? meeting.jolpica_locality ?? '-'} - ${meeting.country_name ?? meeting.jolpica_country ?? '-'}`;
        const circuitName = meeting.jolpica_circuit_name ?? meeting.circuit_short_name ?? 'circuito';
        const facts = [
            ['Curvas', this.getCircuitFact(meeting, 'circuit_corners')],
            ['Vueltas', this.getCircuitFact(meeting, 'total_laps')],
            ['Longitud circuito', this.getCircuitFact(meeting, 'circuit_length')],
            ['Distancia carrera', this.getCircuitFact(meeting, 'race_distance')]
        ].map(([label, value]) => `
            <div class="rs-circuit-card__fact">
                <span class="rs-circuit-card__fact-label">${label}</span>
                <strong class="rs-circuit-card__fact-value">${value ?? '-'}</strong>
            </div>
        `).join('');

        this.selectedCircuitCard.innerHTML = `
            <div class="rs-circuit-card__inner">
                    <div class="rs-circuit-card__stats">
                        <div class="rs-circuit-card__lap-record">
                            <span class="rs-circuit-card__fact-label">Vuelta rápida</span>
                        <strong class="rs-circuit-card__lap-record-value">${this.getCircuitFact(meeting, 'circuit_lap_record')}</strong>
                    </div>

                    <div class="rs-circuit-card__facts">
                        ${facts}
                    </div>
                </div>

                <div class="rs-circuit-card__visual">
                    <div class="rs-circuit-card__map-header">
                        <h4 class="rs-circuit-card__map-title">${circuitName}</h4>
                        <span class="rs-circuit-card__map-subtitle">${title}</span>
                    </div>

                    <div class="rs-circuit-card__image-wrap">
                        ${imageUrl ? `<img class="rs-circuit-card__image" src="${imageUrl}" alt="Mapa del circuito ${circuitName}" onerror="this.style.display='none';this.nextElementSibling.style.display='flex';">` : ''}
                        <div class="rs-circuit-card__fallback" style="${imageUrl ? 'display:none;' : ''}">
                            ${this.renderCountryFlag(meeting)}
                            <span>Cargando imagen...</span>
                            <strong>${circuitName}</strong>
                        </div>
                    </div>
                </div>
            </div>
        `;
        if (!imageUrl) this.hydrateCircuitThumbnail(meeting);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Intenta cargar una imagen pública del circuito cuando no existe mapa local
     * @param {Object} meeting GP seleccionado
     */
    async hydrateCircuitThumbnail(meeting) {
        const wikiUrl = meeting?.jolpica_circuit_url || '';
        if (!wikiUrl.includes('/wiki/')) return;
        const page = decodeURIComponent(wikiUrl.split('/wiki/')[1] || '').trim();
        if (!page) return;
        const media = await this.fetchJson(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(page)}`, {});
        const image = media?.thumbnail?.source || media?.originalimage?.source || '';
        if (!image || this.selectedMeetingKey !== meeting.meeting_key) return;
        const wrap = this.selectedCircuitCard.querySelector('.rs-circuit-card__image-wrap');
        if (!wrap) return;
        const circuitName = meeting.jolpica_circuit_name ?? meeting.circuit_short_name ?? 'circuito';
        wrap.innerHTML = `<img class="rs-circuit-card__image" src="${image}" alt="Imagen del circuito ${circuitName}" loading="lazy">`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 22-04-2026
     * @modified 03-05-2026
     * @description Selecciona un GP y espera sus sesiones definitivas antes de renderizar
     * @param {number} meetingKey Clave del meeting
     */
    async selectMeeting(meetingKey) {
        const requestId = ++this.selectionRequestId;
        this.selectedMeetingKey = meetingKey;
        this.renderMiniCalendar();
        this.renderMonthEvents();

        const selectedMeeting = this.allMeetings.find(meeting => meeting.meeting_key === meetingKey);

        if (!selectedMeeting) {
            this.sessionsContent.innerHTML = '';
            this.selectedCircuitCard.innerHTML = '';
            this.renderDetailStatus(null);
            this.updateTopSelectedCircuit(null);
            return;
        }

        this.pendingMeetingKey = null;
        this.updateUrl(meetingKey);
        this.updateTopSelectedCircuit(selectedMeeting);
        this.renderSelectedCircuitCard(selectedMeeting);
        this.sessionsContent.innerHTML = '<div class="loading-state">Cargando sesiones del GP...</div>';

        if (selectedMeeting.is_cancelled && Array.isArray(selectedMeeting.cancelled_sessions)) {
            this.selectedSessions = selectedMeeting.cancelled_sessions;
            await this.renderMeetingDetail(selectedMeeting, selectedMeeting.cancelled_sessions);
            return;
        }

        try {
            const sessions = await this.fetchSessionsWithRetry(meetingKey);
            if (requestId !== this.selectionRequestId || this.selectedMeetingKey !== meetingKey) {
                return;
            }
            const safeSessions = Array.isArray(sessions) ? sessions : [];
            this.selectedSessions = safeSessions;
            await this.renderMeetingDetail(selectedMeeting, safeSessions);
        } catch (error) {
            if (requestId !== this.selectionRequestId || this.selectedMeetingKey !== meetingKey) {
                return;
            }
            console.error('Error cargando sesiones:', error);
            this.renderApiRetry(this.sessionsContent, 'Sesiones');
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Reintenta la carga de sesiones para evitar pintar un GP vacío por una respuesta temporal
     * @param {number} meetingKey Clave del meeting
     * @returns {Promise<Array>} Sesiones confirmadas o array vacío final
     */
    async fetchSessionsWithRetry(meetingKey) {
        let lastSessions = [];
        for (let attempt = 0; attempt < 5; attempt++) {
            const sessions = await this.fetchJson(this.sessionsByMeetingApi(meetingKey), null);
            lastSessions = Array.isArray(sessions) ? sessions : [];
            if (lastSessions.length) {
                return lastSessions;
            }
            await this.wait(320 * (attempt + 1));
        }
        return lastSessions;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera no bloqueante entre reintentos de datos
     * @param {number} milliseconds Milisegundos
     * @returns {Promise<void>} Promesa resuelta tras la espera
     */
    wait(milliseconds) {
        return window.RaceStreamApi.wait(milliseconds);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.1
     * @created 22-04-2026
     * @modified 28-04-2026
     * @description Renderiza el detalle definitivo del GP con datos de circuito y sesiones compactas
     * @param {Object} meeting GP
     * @param {Array} sessions Sesiones
     */
    renderMeetingDetail(meeting, sessions) {
        if (this.selectedMeetingKey !== meeting.meeting_key) {
            return;
        }
        this.renderDetailStatus(meeting);
        this.renderSelectedCircuitCard(meeting);
        this.renderSessions(meeting, sessions);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Renderiza las sesiones cuando ya se han cargado sus datos definitivos
     * @param {Object} meeting GP
     * @param {Array} sessions Sesiones
     */
    renderSessions(meeting, sessions) {
        const rows = sessions.map((session, index) => {
            const sessionStatus = this.getSessionStatus(session, meeting);
            const sessionAction = this.getSessionActionButton(sessionStatus, session, meeting, index);
            const sessionDate = this.formatClientDateBadge(session.date_start);

            return `
                <div class="${this.getSessionTypeClass(session.session_type)} rs-session-row--has-action">
                    <div class="rs-session-row__date" aria-label="${sessionDate.label}">
                        <strong>${sessionDate.day}</strong>
                        <span>${sessionDate.month}</span>
                    </div>

                    <div class="rs-session-row__main">
                        <div class="rs-session-row__title">
                            <h4>${this.translateSessionName(session.session_name)}</h4>
                        </div>
                        <span class="rs-session-row__status-pill rs-session-row__status-pill--${sessionStatus}">${this.getSessionStatusLabel(sessionStatus)}</span>
                        <div class="rs-session-row__meta">${this.translateSessionType(session.session_type)}</div>
                    </div>

                    <div class="rs-session-row__time rs-session-row__time--schedule">
                        <span class="rs-session-row__time-label">Horario</span>
                        <span class="rs-session-row__time-value rs-session-row__time-value--client">Mi hora ${this.formatClientDateTimeRange(session.date_start, session.date_end)}</span>
                        <span class="rs-session-row__time-value">Circuito ${this.formatTimeOnly(session.date_start, meeting.gmt_offset, true)} - ${this.formatTimeOnly(session.date_end, meeting.gmt_offset, true)}</span>
                    </div>

                    <div class="rs-session-row__actions">
                        ${sessionAction || ''}
                    </div>
                </div>
            `;
        }).join('');

        this.sessionsContent.innerHTML = `
            <div class="rs-sessions-block">
                <h3 class="rs-section-heading">Sesiones del GP</h3>
                <div class="rs-session-list">
                    ${rows || this.renderApiRetryHtml('Sesiones')}
                </div>
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Pinta recarga manual cuando faltan datos obligatorios
     * @param {HTMLElement} target Contenedor
     * @param {string} label Datos esperados
     */
    renderApiRetry(target, label) {
        if (target) target.innerHTML = this.renderApiRetryHtml(label);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Devuelve HTML de recarga reutilizable para errores de API
     * @param {string} label Datos esperados
     * @returns {string} HTML
     */
    renderApiRetryHtml(label) {
        return `
            <div class="rs-api-retry empty-state">
                <strong>${label}</strong>
                <span>Recarga forzada</span>
                <button class="rs-button" type="button" onclick="window.location.reload()">Reintentar</button>
            </div>
        `;
    }

    renderFavoriteButton(type, externalId, title, url, description) {
        return window.RaceStreamFavorites
            ? window.RaceStreamFavorites.button({ type, externalId, seasonYear: this.currentSeason, title, url, description })
            : '';
    }
}
/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 21-04-2026
 * @modified 22-04-2026
 * @description Inicializa la página
 */
document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamCalendarPage = new RaceStreamCalendarPage();
});
