/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.7
 * @created 21-04-2026
 * @modified 27-04-2026
 * @description Lógica principal del calendario F1 RaceStream con estado integrado en cada sesion
 */
class RaceStreamCalendarPage {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 22-04-2026
     * @description Constructor principal
     */
    constructor() {
        this.meetingsApi = '/api/f1/schedule/meetings';
        this.sessionsByMeetingApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.currentOrNextMeetingApi = '/api/f1/schedule/current-or-next-meeting';

        this.monthNames = [
            'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
            'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
        ];

        this.weekDays = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

        this.allMeetings = [];
        this.filteredMeetings = [];
        this.selectedMeetingKey = null;
        this.currentMonthIndex = 0;
        this.lastValidTopMeeting = null;
        this.lastScrollY = window.scrollY;
        this.currentSeason = new Date().getFullYear();
        this.selectedYear = this.currentSeason;
        this.lastManuallySelectedFilter = 'upcoming';
        this.previousSeasonWasHistoric = false;

        this.circuitImageOverrides = {
            Catalunya: '/assets/circuits/montmelo.png',
            Madring: '/assets/circuits/madring.png'
        };

        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 24-04-2026
     * @description Cachea el DOM necesario
     */
    cacheDom() {
        this.navbar = document.getElementById('mainNavbar');
        this.yearInput = document.getElementById('yearInput');
        this.statusFilter = document.getElementById('statusFilter');
        this.filterGroup = document.getElementById('filterGroup');

        this.topSelectedCircuit = document.getElementById('topSelectedCircuit');

        this.calendarMonthLabel = document.getElementById('calendarMonthLabel');
        this.calendarGrid = document.getElementById('calendarGrid');
        this.calendarEvents = document.getElementById('calendarEvents');

        this.detailContent = document.getElementById('detailContent');
        this.selectedCircuitCard = document.getElementById('selectedCircuitCard');

        this.raceStripTitle = document.getElementById('raceStripTitle');
        this.raceStripMeta = document.getElementById('raceStripMeta');
        this.raceStripClocks = document.getElementById('raceStripClocks');
        this.raceStripAction = document.getElementById('raceStripAction');
        this.raceStripFlag = document.getElementById('raceStripFlag');

        this.profileDropdown = document.getElementById('profileDropdown');
        this.profileTrigger = this.profileDropdown?.querySelector('.rs-profile-dropdown__trigger');
        this.profileMenu = this.profileDropdown?.querySelector('.rs-profile-dropdown__menu');

        this.mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
        this.mobileMenuTrigger = this.mobileMenuDropdown?.querySelector('.rs-navbar__menu-trigger');
        this.mobileMenuPanel = this.mobileMenuDropdown?.querySelector('.rs-navbar-mobile-menu__panel');

        this.prevMonthButton = document.getElementById('prevMonthButton');
        this.nextMonthButton = document.getElementById('nextMonthButton');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 24-04-2026
     * @description Asocia eventos
     */
    bindEvents() {
        this.yearInput.addEventListener('change', () => this.loadCalendar());

        this.statusFilter.addEventListener('change', () => {
            this.lastManuallySelectedFilter = this.statusFilter.value;
            this.loadCalendar();
        });

        this.prevMonthButton.addEventListener('click', () => this.changeMonth(-1));
        this.nextMonthButton.addEventListener('click', () => this.changeMonth(1));

        if (this.profileDropdown && this.profileTrigger) {
            this.profileTrigger.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                this.profileDropdown.classList.toggle('rs-profile-dropdown--open');
                this.mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
            });

            this.profileMenu?.addEventListener('click', (event) => event.stopPropagation());
        }

        if (this.mobileMenuDropdown && this.mobileMenuTrigger) {
            this.mobileMenuTrigger.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                this.mobileMenuDropdown.classList.toggle('rs-navbar-mobile-menu--open');
                this.profileDropdown?.classList.remove('rs-profile-dropdown--open');
            });

            this.mobileMenuPanel?.addEventListener('click', (event) => event.stopPropagation());
        }

        document.addEventListener('click', () => {
            this.profileDropdown?.classList.remove('rs-profile-dropdown--open');
            this.mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
        });

        window.addEventListener('scroll', () => this.handleNavbarVisibility());
        window.addEventListener('resize', () => this.updateNavbarOffset());
        window.addEventListener('load', () => this.updateNavbarOffset());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @modified 22-04-2026
     * @description Inicializa la página
     */
    init() {
        this.updateNavbarOffset();
        this.loadCalendar();
        setInterval(() => {
            this.updateDynamicClocks();
            this.updateCountdown();
        }, 1000);
    }

    updateNavbarOffset() {
        if (!this.navbar) {
            return;
        }

        document.documentElement.style.setProperty('--rs-navbar-height', `${this.navbar.offsetHeight}px`);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @modified 22-04-2026
     * @description Oculta navbar al bajar
     */
    handleNavbarVisibility() {
        const currentY = window.scrollY;
        const shouldHideNavbar = currentY > this.lastScrollY && currentY > 120;
        document.body.classList.toggle('rs-nav-hidden', shouldHideNavbar);
        this.lastScrollY = currentY;
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
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Devuelve fecha normalizada del circuito para pintar calendario
     * @param {string} value Fecha ISO
     * @param {string} gmtOffset Offset
     * @returns {Date} Fecha
     */
    getCircuitCalendarDate(value, gmtOffset) {
        const date = new Date(value);
        const adjustedDate = new Date(date.getTime() + this.parseGmtOffsetToMinutes(gmtOffset) * 60000);
        adjustedDate.setUTCHours(0, 0, 0, 0);
        return adjustedDate;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Devuelve rango oficial corto del GP
     * @param {Object} meeting GP
     * @returns {string} Rango
     */
    formatMeetingDateRangeForStrip(meeting) {
        if (!meeting?.date_start || !meeting?.date_end) {
            return '-';
        }

        const start = this.getCircuitCalendarDate(meeting.date_start, meeting.gmt_offset);
        const end = this.getCircuitCalendarDate(meeting.date_end, meeting.gmt_offset);
        const month = start.toLocaleDateString('en-US', {
            month: 'short',
            timeZone: 'UTC'
        }).toUpperCase();

        return `${month} ${start.getUTCDate()}-${end.getUTCDate()}`;
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
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Cuenta atrás
     * @param {string} startDate Fecha
     * @returns {string} Cuenta atrás
     */
    getCountdownToMeeting(startDate) {
        const diff = new Date(startDate).getTime() - Date.now();

        if (diff <= 0) {
            return 'En curso';
        }

        const totalSeconds = Math.floor(diff / 1000);
        const days = Math.floor(totalSeconds / 86400);
        const hours = Math.floor((totalSeconds % 86400) / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;

        return `Empieza en ${days}d ${hours}h ${minutes}m ${seconds}s`;
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
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Filtra por estado
     * @param {Array} meetings Meetings
     * @param {string} statusFilter Filtro
     * @returns {Array} Meetings filtrados
     */
    applyStatusFilter(meetings, statusFilter) {
        return meetings.filter(meeting => this.getMeetingStatus(meeting) === statusFilter);
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
     * @version 1.0.2
     * @created 27-04-2026
     * @modified 27-04-2026
     * @description Devuelve estado visual de una sesion del GP
     * @param {Object} session Sesion
     * @returns {string} Estado
     */
    getSessionStatus(session) {
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
     * @version 1.0.1
     * @created 27-04-2026
     * @modified 27-04-2026
     * @description Traduce estado interno de sesion a etiqueta visible
     * @param {string} status Estado interno
     * @returns {string} Etiqueta
     */
    getSessionStatusLabel(status) {
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
     * @modified 27-04-2026
     * @description Devuelve boton de accion segun el estado de la sesion
     * @param {string} status Estado interno
     * @returns {string} HTML del boton
     */
    getSessionActionButton(status) {
        if (status === 'completed') {
            return '<a class="rs-link-chip" href="/sessions.html">Ver resultados</a>';
        }

        if (status === 'live') {
            return '<a class="rs-link-chip rs-link-chip--live" href="/live.html">Ver en vivo</a>';
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
        const topMeeting = this.lastValidTopMeeting;

        if (!topMeeting) {
            return;
        }

        this.raceStripClocks.innerHTML = `
            <div class="rs-race-strip__clock-card">
                <div class="rs-race-strip__clock-row">
                    <span class="rs-race-strip__clock-label">MI HORA</span>
                    <strong class="rs-race-strip__clock-value">${this.getClientNowTime()}</strong>
                </div>

                <span class="rs-race-strip__clock-divider"></span>

                <div class="rs-race-strip__clock-row">
                    <span class="rs-race-strip__clock-subvalue">HORA CIRCUITO</span>
                    <span class="rs-race-strip__clock-track-value">${this.getCircuitNowTime(topMeeting.gmt_offset)}</span>
                </div>
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Actualiza cuenta atrás del próximo GP
     */
    updateCountdown() {
        if (!this.lastValidTopMeeting) {
            return;
        }

        const meetingStatus = this.getMeetingStatus(this.lastValidTopMeeting);
        if (meetingStatus === 'live') {
            return;
        }

        const countdownNode = document.getElementById('raceStripCountdown');
        if (countdownNode) {
            countdownNode.textContent = this.getCountdownToMeeting(this.lastValidTopMeeting.date_start);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Actualiza franja superior del próximo GP
     * @param {Object} meeting Próximo GP
     */
    updateRaceStrip(meeting) {
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

        const meetingStatus = this.getMeetingStatus(referenceMeeting);

        this.raceStripTitle.textContent = referenceMeeting.meeting_name;
        this.raceStripMeta.textContent = this.formatMeetingDateRangeForStrip(referenceMeeting);

        if (referenceMeeting.country_flag) {
            this.raceStripFlag.src = referenceMeeting.country_flag;
            this.raceStripFlag.style.display = 'inline-block';
        } else {
            this.raceStripFlag.style.display = 'none';
        }

        this.raceStripAction.innerHTML = meetingStatus === 'live'
            ? '<a class="rs-button rs-button--primary" href="/live.html">En vivo</a>'
            : `<span id="raceStripCountdown" class="rs-race-strip__status">${this.getCountdownToMeeting(referenceMeeting.date_start)}</span>`;

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
            return;
        }

        this.topSelectedCircuit.innerHTML = `
            ${meeting.country_flag ? `<img class="rs-flag-inline" src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name ?? 'país'}">` : ''}
            <span>${meeting.circuit_short_name ?? meeting.location ?? '-'} · ${this.formatMeetingDateRangeForStrip(meeting)}</span>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 22-04-2026
     * @modified 27-04-2026
     * @description Carga el calendario completo
     */
    async loadCalendar() {
        const year = parseInt(this.yearInput.value, 10);
        this.selectedYear = year;

        const isHistoricSeason = year < this.currentSeason;
        document.body.classList.toggle('rs-historic-season', isHistoricSeason);
        this.filterGroup.classList.toggle('rs-field-group--hidden', isHistoricSeason);

        if (isHistoricSeason) {
            this.statusFilter.value = 'completed';
        } else if (this.previousSeasonWasHistoric) {
            this.statusFilter.value = this.lastManuallySelectedFilter || 'upcoming';
        }

        this.previousSeasonWasHistoric = isHistoricSeason;
        const statusFilter = this.statusFilter.value;

        this.calendarGrid.innerHTML = '<div class="loading-state">Cargando calendario...</div>';
        this.calendarEvents.innerHTML = '';
        this.detailContent.innerHTML = '<div class="loading-state">Cargando información del GP...</div>';
        this.updateTopSelectedCircuit(null);

        try {
            const [meetingsResponse, nextMeetingResponse] = await Promise.all([
                fetch(`${this.meetingsApi}?year=${year}`),
                fetch(`${this.currentOrNextMeetingApi}?year=${this.currentSeason}`)
            ]);

            const meetings = await meetingsResponse.json();
            const nextMeeting = await nextMeetingResponse.json();

            this.allMeetings = Array.isArray(meetings) ? meetings : [];
            this.filteredMeetings = this.applyStatusFilter(this.allMeetings, statusFilter);

            this.updateRaceStrip(nextMeeting);

            const meetingToSelect = this.selectInitialMeeting();
            this.currentMonthIndex = meetingToSelect
                ? this.getCircuitCalendarDate(meetingToSelect.date_start, meetingToSelect.gmt_offset).getUTCMonth()
                : 0;

            this.renderMiniCalendar();
            this.renderMonthEvents();

            if (meetingToSelect) {
                await this.selectMeeting(meetingToSelect.meeting_key);
            } else {
                this.detailContent.innerHTML = '<div class="empty-state">No hay sesiones disponibles para ese filtro.</div>';
                this.selectedCircuitCard.innerHTML = '';
                this.updateTopSelectedCircuit(null);
            }
        } catch (error) {
            console.error('Error cargando calendario:', error);
            this.calendarGrid.innerHTML = '<div class="empty-state">No se pudo cargar el calendario.</div>';
            this.calendarEvents.innerHTML = '';
            this.detailContent.innerHTML = '<div class="empty-state">Se produjo un error al cargar el detalle del GP.</div>';
            this.selectedCircuitCard.innerHTML = '';
            this.updateTopSelectedCircuit(null);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Selecciona el primer GP visible
     * @returns {Object|null} Meeting
     */
    selectInitialMeeting() {
        const existing = this.filteredMeetings.find(meeting => meeting.meeting_key === this.selectedMeetingKey);
        return existing || this.filteredMeetings[0] || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Cambia mes del mini calendario
     * @param {number} direction Dirección
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
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Devuelve el GP de un día usando hora de circuito
     * @param {Date} currentDate Fecha
     * @returns {Object|null} Meeting
     */
    getMeetingForCalendarDay(currentDate) {
        return this.filteredMeetings.find(meeting => {
            const start = this.getCircuitCalendarDate(meeting.date_start, meeting.gmt_offset);
            const end = this.getCircuitCalendarDate(meeting.date_end, meeting.gmt_offset);
            return currentDate >= start && currentDate <= end;
        }) || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Renderiza mini calendario
     */
    renderMiniCalendar() {
        const year = parseInt(this.yearInput.value, 10);
        this.calendarMonthLabel.textContent = `${this.monthNames[this.currentMonthIndex]} ${year}`;

        const firstDay = new Date(Date.UTC(year, this.currentMonthIndex, 1));
        const lastDay = new Date(Date.UTC(year, this.currentMonthIndex + 1, 0));
        const totalDays = lastDay.getUTCDate();
        const startOffset = (firstDay.getUTCDay() + 6) % 7;

        let html = '';

        this.weekDays.forEach(day => {
            html += `<div class="rs-calendar-weekday">${day}</div>`;
        });

        for (let i = 0; i < startOffset; i++) {
            html += '<div class="rs-calendar-day rs-calendar-day--empty"></div>';
        }

        for (let day = 1; day <= totalDays; day++) {
            const currentDate = new Date(Date.UTC(year, this.currentMonthIndex, day));
            const meeting = this.getMeetingForCalendarDay(currentDate);

            if (!meeting) {
                html += `
                    <div class="rs-calendar-day">
                        <span class="rs-calendar-day__number">${day}</span>
                    </div>
                `;
                continue;
            }

            const start = this.getCircuitCalendarDate(meeting.date_start, meeting.gmt_offset);
            const end = this.getCircuitCalendarDate(meeting.date_end, meeting.gmt_offset);

            let rangeClass = 'rs-calendar-day--range-middle';
            if (currentDate.getTime() === start.getTime()) {
                rangeClass = 'rs-calendar-day--range-start';
            } else if (currentDate.getTime() === end.getTime()) {
                rangeClass = 'rs-calendar-day--range-end';
            }

            const isActive = this.selectedMeetingKey === meeting.meeting_key ? 'rs-calendar-day--active' : '';

            html += `
                <div class="rs-calendar-day ${rangeClass} ${isActive}" onclick="window.raceStreamCalendarPage.selectMeeting(${meeting.meeting_key})">
                    <span class="rs-calendar-day__number">${day}</span>
                    ${meeting.country_flag ? `<img class="rs-calendar-day__flag" src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name ?? 'país'}">` : ''}
                </div>
            `;
        }

        this.calendarGrid.innerHTML = html;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Rango corto del GP
     * @param {Object} meeting GP
     * @returns {string} Texto
     */
    formatGpDateRange(meeting) {
        const start = this.getCircuitCalendarDate(meeting.date_start, meeting.gmt_offset);
        const end = this.getCircuitCalendarDate(meeting.date_end, meeting.gmt_offset);
        const month = start.toLocaleDateString('en-US', {
            month: 'short',
            timeZone: 'UTC'
        }).toUpperCase();

        return `${start.getUTCDate()}-${end.getUTCDate()} ${month}`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Renderiza lista de eventos del mes
     */
    renderMonthEvents() {
        const meetingsOfMonth = this.filteredMeetings.filter(meeting =>
            this.getCircuitCalendarDate(meeting.date_start, meeting.gmt_offset).getUTCMonth() === this.currentMonthIndex
        );

        if (!meetingsOfMonth.length) {
            this.calendarEvents.innerHTML = '<div class="empty-state">No hay GP en este mes con el filtro actual.</div>';
            return;
        }

        this.calendarEvents.innerHTML = meetingsOfMonth.map(meeting => {
            const activeClass = this.selectedMeetingKey === meeting.meeting_key ? 'rs-calendar-events__item--active' : '';

            return `
                <div class="rs-calendar-events__item ${activeClass}" onclick="window.raceStreamCalendarPage.selectMeeting(${meeting.meeting_key})">
                    <div class="rs-calendar-events__row">
                        <h5 class="rs-calendar-events__title">
                            ${meeting.country_flag ? `<img class="rs-flag-inline" src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name ?? 'país'}">` : ''}
                            <span>${meeting.meeting_name ?? '-'}</span>
                        </h5>
                        <span class="rs-calendar-events__date">${this.formatGpDateRange(meeting)}</span>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Devuelve imagen del circuito
     * @param {Object} meeting GP
     * @returns {string|null} Ruta
     */
    getCircuitImage(meeting) {
        if (!meeting) {
            return null;
        }

        const shortName = meeting.circuit_short_name || '';
        const location = meeting.location || '';

        if (this.circuitImageOverrides[shortName]) {
            return this.circuitImageOverrides[shortName];
        }

        if (this.circuitImageOverrides[location]) {
            return this.circuitImageOverrides[location];
        }

        return meeting.circuit_image || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Renderiza tarjeta del mapa del circuito
     * @param {Object} meeting GP
     */
    renderSelectedCircuitCard(meeting) {
        const imageUrl = this.getCircuitImage(meeting);

        if (!meeting || !imageUrl) {
            this.selectedCircuitCard.innerHTML = '';
            return;
        }

        this.selectedCircuitCard.innerHTML = `
            <div class="rs-circuit-card__map-header">
                <h4 class="rs-circuit-card__map-title">Mapa del circuito de ${meeting.circuit_short_name ?? '-'}</h4>
                <p class="rs-circuit-card__map-subtitle">${meeting.location ?? '-'} · ${meeting.country_name ?? '-'}</p>
            </div>

            <img class="rs-circuit-card__image" src="${imageUrl}" alt="Mapa del circuito ${meeting.circuit_short_name ?? ''}" onerror="this.style.display='none';">
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 22-04-2026
     * @modified 22-04-2026
     * @description Selecciona un GP y carga sus sesiones
     * @param {number} meetingKey Clave del meeting
     */
    async selectMeeting(meetingKey) {
        this.selectedMeetingKey = meetingKey;
        this.renderMiniCalendar();
        this.renderMonthEvents();

        const selectedMeeting = this.filteredMeetings.find(meeting => meeting.meeting_key === meetingKey)
            || this.allMeetings.find(meeting => meeting.meeting_key === meetingKey);

        if (!selectedMeeting) {
            this.detailContent.innerHTML = '<div class="empty-state">No se encontró el Gran Premio seleccionado.</div>';
            this.updateTopSelectedCircuit(null);
            return;
        }

        this.updateTopSelectedCircuit(selectedMeeting);
        this.renderSelectedCircuitCard(selectedMeeting);
        this.detailContent.innerHTML = '<div class="loading-state">Cargando sesiones del GP...</div>';

        try {
            const response = await fetch(this.sessionsByMeetingApi(meetingKey));
            const sessions = await response.json();
            this.renderMeetingDetail(selectedMeeting, Array.isArray(sessions) ? sessions : []);
        } catch (error) {
            console.error('Error cargando sesiones:', error);
            this.detailContent.innerHTML = '<div class="empty-state">No se pudieron cargar las sesiones del GP.</div>';
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-04-2026
     * @modified 27-04-2026
     * @description Renderiza detalle del GP con estado integrado en el titulo de cada sesion
     * @param {Object} meeting GP
     * @param {Array} sessions Sesiones
     */
    renderMeetingDetail(meeting, sessions) {
        const status = this.getMeetingStatus(meeting);

        this.detailContent.innerHTML = `
            <div class="rs-detail-grid">
                <div class="rs-info-card">
                    <h3 class="rs-info-card__title">Información General</h3>

                    <div class="rs-info-list">
                        <div class="rs-info-item">
                            <span class="rs-info-item__label">GP</span>
                            <span class="rs-info-item__value rs-info-item__value--with-flag">
                                ${meeting.country_flag ? `<img class="rs-flag-inline" src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name ?? 'país'}">` : ''}
                                <span>${meeting.meeting_name ?? '-'}</span>
                            </span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Estado</span>
                            <span class="rs-info-item__value">${this.getStatusLabel(status)}</span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Circuito</span>
                            <span class="rs-info-item__value">${meeting.circuit_short_name ?? '-'}</span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">País</span>
                            <span class="rs-info-item__value rs-info-item__value--with-flag">
                                <span>${meeting.country_name ?? '-'}</span>
                            </span>
                        </div>
                    </div>
                </div>

                <div class="rs-info-card">
                    <h3 class="rs-info-card__title">Fechas y Localización</h3>

                    <div class="rs-info-list">
                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Inicio</span>
                            <span class="rs-info-item__value rs-info-item__value--compact">${this.formatCircuitDateTime(meeting.date_start, meeting.gmt_offset)}</span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Fin</span>
                            <span class="rs-info-item__value rs-info-item__value--compact">${this.formatCircuitDateTime(meeting.date_end, meeting.gmt_offset)}</span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Ubicación</span>
                            <span class="rs-info-item__value">${meeting.location ?? '-'}</span>
                        </div>

                        <div class="rs-info-item">
                            <span class="rs-info-item__label">Circuito</span>
                            <span class="rs-info-item__value">${meeting.circuit_type ?? '-'}</span>
                        </div>
                    </div>
                </div>
            </div>

            <div>
                <h3 class="rs-section-heading">Sesiones del GP</h3>

                <div class="rs-session-list">
                    ${sessions.length ? sessions.map(session => {
                        const sessionStatus = this.getSessionStatus(session);
                        const sessionAction = this.getSessionActionButton(sessionStatus);

                        return `
                        <div class="${this.getSessionTypeClass(session.session_type)} ${sessionAction ? 'rs-session-row--has-action' : ''}">
                            <div class="rs-session-row__main">
                                <div class="rs-session-row__title">
                                    <h4>${this.translateSessionName(session.session_name)}</h4>
                                </div>
                                <span class="rs-session-row__status-pill rs-session-row__status-pill--${sessionStatus}">${this.getSessionStatusLabel(sessionStatus)}</span>
                                <div class="rs-session-row__meta">${this.translateSessionType(session.session_type)}</div>
                            </div>

                            <div class="rs-session-row__time rs-session-row__time--start">
                                <span class="rs-session-row__time-label">Inicio</span>
                                <span class="rs-session-row__time-value rs-session-row__time-value--client">Cliente ${this.formatClientDateTime(session.date_start)}</span>
                                <span class="rs-session-row__time-value rs-session-row__time-value--circuit">Circuito ${this.formatCircuitDateTime(session.date_start, meeting.gmt_offset)}</span>
                            </div>

                            <div class="rs-session-row__time rs-session-row__time--end">
                                <span class="rs-session-row__time-label">Fin</span>
                                <span class="rs-session-row__time-value rs-session-row__time-value--client">Cliente ${this.formatClientDateTime(session.date_end)}</span>
                                <span class="rs-session-row__time-value rs-session-row__time-value--circuit">Circuito ${this.formatCircuitDateTime(session.date_end, meeting.gmt_offset)}</span>
                            </div>

                            ${sessionAction ? `<div class="rs-session-row__actions">${sessionAction}</div>` : ''}
                        </div>
                    `;
                    }).join('') : '<div class="empty-state">No hay sesiones disponibles para este GP.</div>'}
                </div>
            </div>
        `;
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
