/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 30-04-2026
 * @modified 05-05-2026
 * @description Lógica de Inicio con contadores estables, última sesión, carga segura, placeholders de piloto y ayuda contextual
 */
class RaceStreamHomePage {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @description Constructor principal de Inicio
     */
    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.year = new Date().getFullYear();
        this.meetingsApi = `/api/f1/schedule/calendar-meetings?year=${this.year}`;
        this.sessionsByMeetingApi = (meetingKey) => `/api/f1/schedule/meetings/${meetingKey}/sessions`;
        this.resultsApi = (sessionKey) => `/api/f1/session-results?sessionKey=${sessionKey}`;
        this.driversApi = (sessionKey) => `/api/f1/drivers?sessionKey=${sessionKey}`;
        this.driverStandingsApi = `/api/f1/standings/drivers?year=${this.year}`;
        this.constructorStandingsApi = `/api/f1/standings/constructors?year=${this.year}`;
        this.newsApi = '/api/news/f1?limit=10';
        this.lastScrollY = window.scrollY;
        this.nextMeeting = null;
        this.nextSession = null;
        this.refreshingSession = false;
        this.glossary = {
            SC: ['Safety Car', 'Coche de seguridad que reduce el ritmo y agrupa a los pilotos cuando hay peligro en pista.'],
            VSC: ['Virtual Safety Car', 'Periodo controlado en el que todos reducen velocidad sin que salga un coche fisico.'],
            DNF: ['Did Not Finish', 'El piloto tomo la salida, pero no llego al final por averia, accidente u otro problema.'],
            ERS: ['Energy Recovery System', 'Sistema que recupera energia y la entrega como potencia electrica adicional.']
        };

        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Cachea los elementos usados por Inicio
     */
    cacheDom() {
        this.navbar = document.getElementById('mainNavbar');
        this.profileDropdown = document.getElementById('profileDropdown');
        this.profileTrigger = this.profileDropdown?.querySelector('.rs-profile-dropdown__trigger');
        this.mobileMenuDropdown = document.getElementById('mobileMenuDropdown');
        this.mobileMenuTrigger = this.mobileMenuDropdown?.querySelector('.rs-navbar__menu-trigger');
        this.raceStripTitle = document.getElementById('raceStripTitle');
        this.raceStripMeta = document.getElementById('raceStripMeta');
        this.raceStripClocks = document.getElementById('raceStripClocks');
        this.raceStripAction = document.getElementById('raceStripAction');
        this.raceStripFlag = document.getElementById('raceStripFlag');
        this.heroSessionStatus = document.getElementById('heroSessionStatus');
        this.heroSessionTitle = document.getElementById('heroSessionTitle');
        this.heroSessionMeta = document.getElementById('heroSessionMeta');
        this.heroCountdown = document.getElementById('heroCountdown');
        this.liveNextName = document.getElementById('liveNextName');
        this.liveNextTime = document.getElementById('liveNextTime');
        this.liveDataStatus = document.getElementById('liveDataStatus');
        this.lastSessionTitle = document.getElementById('lastSessionTitle');
        this.lastSessionLink = document.getElementById('lastSessionLink');
        this.lastSessionResults = document.getElementById('lastSessionResults');
        this.driversStandings = document.getElementById('driversStandings');
        this.teamsStandings = document.getElementById('teamsStandings');
        this.homeNews = document.getElementById('homeNews');
        this.glossaryText = document.getElementById('glossaryText');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Asocia eventos de navegacion, scroll y glosario
     */
    bindEvents() {
        this.profileTrigger?.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            this.profileDropdown.classList.toggle('rs-profile-dropdown--open');
            this.mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
        });

        this.mobileMenuTrigger?.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            this.mobileMenuDropdown.classList.toggle('rs-navbar-mobile-menu--open');
            this.profileDropdown?.classList.remove('rs-profile-dropdown--open');
        });

        document.addEventListener('click', () => {
            this.profileDropdown?.classList.remove('rs-profile-dropdown--open');
            this.mobileMenuDropdown?.classList.remove('rs-navbar-mobile-menu--open');
        });

        document.querySelectorAll('[data-term]').forEach((button) => {
            button.addEventListener('click', () => this.showGlossaryTerm(button.dataset.term));
        });

        window.addEventListener('scroll', () => this.handleNavbarVisibility());
        window.addEventListener('resize', () => this.updateNavbarOffset());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Inicializa la página y refresca relojes
     */
    init() {
        this.updateNavbarOffset();
        this.renderFallbackBlocks();
        this.loadHomeData();
        this.loadLastSession();
        this.loadStandings();
        this.loadNews();
        this.showGlossaryTerm('SC');
        setInterval(() => this.updateDynamicTime(), 1000);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Lee JSON devolviendo un fallback seguro sin guardar arrays vacíos como respuesta definitiva
     * @param {string} url URL de la API
     * @param {*} fallback Valor por defecto
     * @returns {Promise<*>} Datos JSON o fallback
     */
    async fetchJson(url, fallback = null) {
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
            } catch (error) {
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

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga calendario y calcula próximo GP/sesión
     */
    async loadHomeData() {
        const meetings = await this.fetchJson(this.meetingsApi, []);
        const safeMeetings = Array.isArray(meetings) ? meetings : [];
        this.nextMeeting = this.getCurrentOrNextMeeting(safeMeetings);
        this.updateRaceStrip();
        await this.loadNextSession();
        this.renderHeroSession();
        this.renderLiveSummary();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Obtiene el GP actual o siguiente
     * @param {Array} meetings Listado de meetings
     * @returns {Object|null} Meeting
     */
    getCurrentOrNextMeeting(meetings) {
        const now = Date.now();
        return meetings.find((meeting) => new Date(meeting.date_end).getTime() >= now) || meetings[meetings.length - 1] || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga sesiones del próximo GP para elegir la siguiente
     */
    async loadNextSession() {
        if (!this.nextMeeting?.meeting_key) {
            return;
        }

        const sessions = await this.fetchJson(this.sessionsByMeetingApi(this.nextMeeting.meeting_key), []);
        const now = Date.now();
        this.nextSession = (Array.isArray(sessions) ? sessions : [])
            .sort((left, right) => new Date(left.date_start).getTime() - new Date(right.date_start).getTime())
            .find((session) => new Date(session.date_end).getTime() >= now) || null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Actualiza la franja fija de proximo GP
     */
    updateRaceStrip() {
        if (!this.nextMeeting) {
            this.raceStripTitle.textContent = 'Calendario no disponible';
            this.raceStripMeta.textContent = 'Revisa la conexión con APIs';
            this.raceStripClocks.textContent = '-';
            return;
        }

        this.raceStripTitle.textContent = this.nextMeeting.meeting_name || 'Gran Premio';
        this.raceStripMeta.textContent = this.formatDateRange(this.nextMeeting.date_start, this.nextMeeting.date_end);
        this.raceStripAction.innerHTML = `<a class="rs-race-strip__status" href="/calendar.html">Ver GP</a>`;
        this.raceStripFlag.style.display = this.nextMeeting.country_flag ? 'block' : 'none';
        if (this.nextMeeting.country_flag) {
            this.raceStripFlag.src = this.nextMeeting.country_flag;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Renderiza la tarjeta principal de próxima sesión
     */
    renderHeroSession() {
        const session = this.nextSession;
        const meeting = this.nextMeeting;

        if (!meeting) {
            this.heroSessionTitle.textContent = 'No hay calendario cargado';
            this.heroSessionMeta.textContent = 'El inicio queda preparado para mostrar el siguiente GP cuando responda la API.';
            return;
        }

        this.heroSessionTitle.textContent = session ? this.translateSessionName(session.session_name) : meeting.meeting_name;
        this.heroSessionMeta.innerHTML = `
            <span class="rs-home-session-meta">
                ${meeting.country_flag ? `<img src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name || 'país'}">` : ''}
                <span>${meeting.meeting_name || 'GP'} · ${this.formatSessionDate(session?.date_start || meeting.date_start)}</span>
            </span>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Renderiza resumen del modulo En Vivo
     */
    renderLiveSummary() {
        const session = this.nextSession;
        this.liveNextName.textContent = session ? this.translateSessionName(session.session_name) : 'Sin sesión';
        this.liveNextTime.textContent = session ? this.formatTimeOnly(session.date_start) : '-';
        this.liveDataStatus.textContent = session ? this.formatCircuitTimeOnly(session.date_start, this.nextMeeting?.gmt_offset) : '-';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Carga la ultima sesion completada con resultados reales antes de mostrarla
     */
    async loadLastSession() {
        if (!this.lastSessionResults) return;
        const meetings = await this.fetchJson(this.meetingsApi, []);
        const completedMeetings = (Array.isArray(meetings) ? meetings : [])
            .filter((meeting) => Number(meeting.meeting_key) > 0 && new Date(meeting.date_end).getTime() < Date.now())
            .sort((left, right) => new Date(right.date_end).getTime() - new Date(left.date_end).getTime())
            .slice(0, 4);

        for (const meeting of completedMeetings) {
            const sessions = await this.fetchJson(this.sessionsByMeetingApi(meeting.meeting_key), []);
            const completedSessions = (Array.isArray(sessions) ? sessions : [])
                .filter((session) => session.session_key && new Date(session.date_end).getTime() < Date.now())
                .sort((left, right) => new Date(right.date_end).getTime() - new Date(left.date_end).getTime());
            for (const session of completedSessions) {
                const results = await this.fetchJson(this.resultsApi(session.session_key), []);
                if (!Array.isArray(results) || !results.length) continue;
                const drivers = await this.fetchJson(this.driversApi(session.session_key), []);
                this.renderLastSession(meeting, session, results, Array.isArray(drivers) ? drivers : []);
                return;
            }
        }

        this.lastSessionTitle.textContent = 'Última sesión no disponible';
        this.lastSessionResults.innerHTML = '<span class="empty-state">OpenF1 todavía no ofrece resultados recientes.</span>';
    }

    renderLastSession(meeting, session, results, drivers) {
        const driverMap = new Map(drivers.map((driver) => [Number(driver.driver_number), driver]));
        const sorted = [...results].sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999));
        this.lastSessionTitle.textContent = `${meeting.meeting_name || 'Gran Premio'} · ${this.translateSessionName(session.session_name)}`;
        this.lastSessionLink.href = `/sessions.html?year=${this.year}&meetingKey=${meeting.meeting_key}&sessionKey=${session.session_key}`;
        this.lastSessionResults.innerHTML = sorted.map((row, index) => {
            const driver = driverMap.get(Number(row.driver_number)) || {};
            const name = driver.full_name || row.full_name || `Piloto ${row.driver_number || ''}`;
            const shortName = this.formatShortDriverName(name);
            const color = this.resolveTeamColor(driver.team_name || row.team_name);
            return `<span class="rs-home-last-session__driver" style="--team-color:${color}"><strong>${row.position || index + 1}</strong><span>${shortName}</span></span>`;
        }).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga top 3 de pilotos y constructores desde Jolpica
     */
    async loadStandings() {
        const [drivers, constructors] = await Promise.all([
            this.fetchJson(this.driverStandingsApi, []),
            this.fetchJson(this.constructorStandingsApi, [])
        ]);

        if (Array.isArray(drivers) && drivers.length) {
            this.renderRanking(this.driversStandings, drivers.slice(0, 3).map((row) => [
                row.position || '-',
                this.getDriverName(row.Driver),
                this.getDriverMeta(row),
                `${row.points || 0} pts`,
                this.getDriverImage(row.Driver),
                'driver'
            ]));
        }

        if (Array.isArray(constructors) && constructors.length) {
            this.renderRanking(this.teamsStandings, constructors.slice(0, 3).map((row) => [
                row.position || '-',
                row.Constructor?.name || 'Constructor',
                `${row.wins || 0} victorias`,
                `${row.points || 0} pts`,
                this.getConstructorLogo(row.Constructor?.constructorId || row.Constructor?.name),
                'constructor'
            ]));
        }

    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Carga titulares reales de GNews filtrando solo contenido de Fórmula 1
     */
    async loadNews() {
        const news = await this.fetchJson(this.newsApi, []);
        const f1News = (Array.isArray(news) ? news : []).filter((item) => this.isFormulaOneNews(`${item.title || ''} ${item.description || ''} ${item.content || ''}`));
        if (!f1News.length) {
            this.homeNews.innerHTML = `
                <article class="rs-home-news__item">
                    <img class="rs-home-news__image" src="/assets/img/LogoRS2.png" alt="Noticias RaceStream">
                    <div class="rs-home-news__body">
                        <h3>Noticias no disponibles</h3>
                    </div>
                </article>
            `;
            return;
        }

        const highlightedNews = f1News.slice(0, 4);
        this.homeNews.innerHTML = highlightedNews.map((item, index) => {
            const title = item.title || 'Noticia de Fórmula 1';
            const image = item.image || '/assets/img/LogoRS2.png';
            return `
                <a class="rs-home-news__item" href="/news.html#top">
                    <img class="rs-home-news__image" src="${image}" alt="Imagen de ${title}" loading="lazy" onerror="this.src='/assets/img/LogoRS2.png';">
                    <div class="rs-home-news__body">
                        <h3>${title}</h3>
                    </div>
                </a>
            `;
        }).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Renderiza bloques con datos internos temporales
     */
    renderFallbackBlocks() {
        this.renderRanking(this.driversStandings, [
            ['1', 'Pendiente de API Jolpica', 'Clasificación real', '-', '', 'driver'],
            ['2', 'Favoritos del usuario', 'Próxima mejora', '-', '', 'driver'],
            ['3', 'Evolución temporal', 'Próxima mejora', '-', '', 'driver']
        ]);
        this.renderRanking(this.teamsStandings, [
            ['1', 'Pendiente de API Jolpica', 'Constructores', '-', '', 'constructor'],
            ['2', 'Comparativa visual', 'Próxima mejora', '-', '', 'constructor'],
            ['3', 'Racha reciente', 'Próxima mejora', '-', '', 'constructor']
        ]);
        this.homeNews.innerHTML = [
            'RaceStream prepara el Live Center',
            'Calendario enriquecido',
            'Glosario integrado'
        ].map((title) => `
            <article class="rs-home-news__item">
                <h3>${title}</h3>
            </article>
        `).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 30-04-2026
     * @modified 04-05-2026
     * @description Renderiza un ranking compacto con placeholder gris cuando falta la foto del piloto
     * @param {HTMLElement} target Contenedor
     * @param {Array} rows Filas
     */
    renderRanking(target, rows) {
        target.innerHTML = rows.map(([position, name, meta, points, sourceUrl, type]) => {
            const mediaEndpoint = `${sourceUrl || ''}`.startsWith('/api/f1/media/driver') ? sourceUrl : '';
            const imageUrl = mediaEndpoint ? '' : (sourceUrl || (type === 'driver' ? '' : '/assets/img/LogoRS2.png'));
            const fallbackClass = type === 'driver' && !imageUrl && !mediaEndpoint ? ' rs-person-avatar--fallback' : '';
            const avatarClass = type === 'driver' ? ' rs-person-avatar' : '';
            const driverError = "this.onerror=null;this.closest('.rs-home-ranking__media').classList.add('rs-person-avatar--fallback');this.removeAttribute('src');";
            const teamError = "this.closest('.rs-home-ranking__media').classList.add('rs-home-ranking__media--fallback');this.src='/assets/img/LogoRS2.png';";
            return `
            <div class="rs-home-ranking__row">
                <span class="rs-home-ranking__pos">${position}</span>
                <span class="rs-home-ranking__media rs-home-ranking__media--${type}${avatarClass}${fallbackClass}"><img class="rs-home-ranking__image ${type === 'constructor' ? 'rs-home-ranking__image--team' : ''}" ${imageUrl ? `src="${imageUrl}"` : ''} data-media-url="${mediaEndpoint}" alt="Imagen de ${name}" loading="lazy" onerror="${type === 'driver' ? driverError : teamError}"></span>
                <div class="rs-home-ranking__content">
                    <strong>${name}</strong>
                    <span class="rs-home-ranking__meta">${meta}</span>
                </div>
                <span class="rs-home-ranking__points">${points}</span>
            </div>
        `;
        }).join('');
        this.hydrateDriverImages(target);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 04-05-2026
     * @description Sustituye el placeholder de pilotos por la foto real devuelta por OpenF1
     * @param {HTMLElement} target Contenedor del ranking
     */
    hydrateDriverImages(target) {
        target.querySelectorAll('img[data-media-url^="/api/f1/media/driver"]').forEach(async (image) => {
            const mediaBox = image.closest('.rs-home-ranking__media');
            mediaBox?.classList.add('rs-home-ranking__media--loading');
            const media = await this.fetchJson(image.dataset.mediaUrl, {});
            if (media?.headshotUrl) {
                this.loadImage(media.headshotUrl)
                    .then(() => {
                        image.src = media.headshotUrl;
                        mediaBox?.classList.remove('rs-home-ranking__media--loading', 'rs-home-ranking__media--fallback', 'rs-person-avatar--fallback');
                    })
                    .catch(() => {
                        image.removeAttribute('src');
                        mediaBox?.classList.remove('rs-home-ranking__media--loading');
                        mediaBox?.classList.add('rs-person-avatar--fallback');
                    });
                return;
            }
            image.removeAttribute('src');
            mediaBox?.classList.remove('rs-home-ranking__media--loading');
            mediaBox?.classList.add('rs-person-avatar--fallback');
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Precarga una imagen antes de mostrarla para evitar parpadeos o placeholders prematuros
     * @param {string} url Imagen remota
     * @returns {Promise<void>} Resultado de carga
     */
    loadImage(url) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve();
            image.onerror = reject;
            image.src = url;
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Actualiza relojes y cuenta atras
     */
    updateDynamicTime() {
        if (this.nextMeeting) {
            this.raceStripClocks.innerHTML = `
                <div class="rs-race-strip__clock-card">
                    <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-label">MI HORA</span><strong class="rs-race-strip__clock-value">${this.getNowTime()}</strong></div>
                    <div class="rs-race-strip__clock-divider"></div>
                    <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-subvalue">CIRCUITO</span><strong class="rs-race-strip__clock-track-value">${this.getCircuitNowTime(this.nextMeeting.gmt_offset)}</strong></div>
                </div>
            `;
        }
        this.heroCountdown.textContent = this.getCountdown(this.nextSession?.date_start || this.nextMeeting?.date_start);
        this.renderLiveSummary();
        this.refreshSessionIfFinished();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Avanza a la siguiente sesión cuando termina la actual
     */
    async refreshSessionIfFinished() {
        if (!this.nextSession || this.refreshingSession || Date.now() <= new Date(this.nextSession.date_end).getTime()) {
            return;
        }

        this.refreshingSession = true;
        await this.loadNextSession();
        this.renderHeroSession();
        this.renderLiveSummary();
        this.refreshingSession = false;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Muestra un concepto del glosario
     * @param {string} term Termino
     */
    showGlossaryTerm(term) {
        const [label, description] = this.glossary[term] || ['Concepto pendiente', 'Pendiente de documentar.'];
        this.glossaryText.innerHTML = `<strong>${label}:</strong> ${description}`;
        document.querySelectorAll('[data-term]').forEach((button) => {
            button.classList.toggle('rs-home-glossary__button--active', button.dataset.term === term);
        });
    }

    updateNavbarOffset() {
        document.documentElement.style.setProperty('--rs-navbar-height', `${this.navbar?.offsetHeight || 74}px`);
    }

    handleNavbarVisibility() {
        const currentY = window.scrollY;
        document.body.classList.toggle('rs-nav-hidden', currentY > this.lastScrollY && currentY > 120);
        this.lastScrollY = currentY;
    }

    formatClientDateTime(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        return date.toLocaleString('es-ES', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    }

    formatSessionDate(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        const day = date.getDate();
        const month = date.toLocaleDateString('es-ES', { month: 'short' }).replace('.', '').toUpperCase();
        const time = date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
        return `${day} ${month}, ${time}`;
    }

    formatTimeOnly(value) {
        if (!value) {
            return '-';
        }
        return new Date(value).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    }

    formatCircuitTimeOnly(value, gmtOffset) {
        if (!value) {
            return '-';
        }
        const date = new Date(new Date(value).getTime() + this.parseGmtOffsetToMinutes(gmtOffset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    formatDateRange(startValue, endValue) {
        if (!startValue || !endValue) {
            return '-';
        }
        const start = new Date(startValue);
        const end = new Date(endValue);
        const startMonth = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const endMonth = end.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const startDay = start.getDate();
        const endDay = end.getDate();

        return startMonth === endMonth
            ? `${startMonth} ${startDay}-${endDay}`
            : `${startMonth} ${startDay} - ${endMonth} ${endDay}`;
    }

    getCountdown(value) {
        if (!value) {
            return '--d --h --m';
        }
        const diff = new Date(value).getTime() - Date.now();
        if (diff <= 0) {
            return 'En curso';
        }
        const totalSeconds = Math.floor(diff / 1000);
        const days = Math.floor(totalSeconds / 86400);
        const hours = Math.floor((totalSeconds % 86400) / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;
        return `${days}d ${hours}h ${minutes}m ${seconds}s`;
    }

    getNowTime() {
        return new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    }

    getCircuitNowTime(gmtOffset) {
        const date = new Date(Date.now() + this.parseGmtOffsetToMinutes(gmtOffset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    parseGmtOffsetToMinutes(gmtOffset) {
        const sign = `${gmtOffset}`.startsWith('-') ? -1 : 1;
        const [hours = '0', minutes = '0'] = `${gmtOffset}`.replace('+', '').replace('-', '').split(':');
        return sign * ((parseInt(hours, 10) * 60) + parseInt(minutes, 10));
    }

    getSessionState(session) {
        const now = Date.now();
        const start = new Date(session.date_start).getTime();
        const end = new Date(session.date_end).getTime();
        return now >= start && now <= end ? 'En vivo' : 'Próxima';
    }

    translateSessionName(name) {
        const names = {
            'Practice 1': 'Libres 1',
            'Practice 2': 'Libres 2',
            'Practice 3': 'Libres 3',
            Qualifying: 'Clasificación',
            Race: 'Carrera',
            Sprint: 'Sprint',
            'Sprint Qualifying': 'Clasificación Sprint',
            'Sprint Shootout': 'Sprint Shootout'
        };
        return names[name] || name || 'Sesión';
    }

    getDriverName(driver) {
        return [driver?.givenName, driver?.familyName].filter(Boolean).join(' ') || driver?.code || 'Piloto';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Devuelve equipo y victorias del piloto usando la estructura real de Jolpica
     * @param {Object} row Fila de clasificacion Jolpica
     * @returns {string} Texto de resumen
     */
    getDriverMeta(row) {
        const constructors = Array.isArray(row?.Constructors) ? row.Constructors : [];
        const team = constructors.map((teamNode) => teamNode?.name).filter(Boolean).join(', ');
        const wins = Number(row?.wins || 0);
        return `${team || row?.Driver?.nationality || 'Piloto'} · ${wins} ${wins === 1 ? 'victoria' : 'victorias'}`;
    }

    getConstructorLogo(value) {
        const key = `${value || ''}`.toLowerCase().replace(/\s+/g, '-');
        const teams = { mercedes: 'mercedes', ferrari: 'ferrari', mclaren: 'mclaren', red_bull: 'red-bull-racing', 'red-bull': 'red-bull-racing', 'red-bull-racing': 'red-bull-racing', williams: 'williams', alpine: 'alpine', haas: 'haas', 'aston-martin': 'aston-martin', rb: 'racing-bulls', 'racing-bulls': 'racing-bulls', sauber: 'kick-sauber', audi: 'audi', cadillac: 'cadillac' };
        const team = teams[key] || key;
        const file = team.replace(/-/g, '');
        return `https://media.formula1.com/image/upload/c_lfill,w_48/q_auto/v1740000001/common/f1/2026/${team}/2026${file}logowhite.webp`;
    }

    formatShortDriverName(value) {
        const parts = `${value || ''}`.trim().split(/\s+/).filter(Boolean);
        if (parts.length < 2) return value || '-';
        return `${parts[0].charAt(0)}. ${parts.slice(1).join(' ')}`;
    }

    resolveTeamColor(value) {
        if (this.assets) {
            return this.assets.getTeamColor({ name: value }, this.year);
        }
        const key = `${value || ''}`.toLowerCase();
        if (key.includes('ferrari')) return '#e8002d';
        if (key.includes('mclaren')) return '#ff8000';
        if (key.includes('red bull')) return '#1e5bc6';
        if (key.includes('mercedes')) return '#00a19c';
        return '#64748b';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 30-04-2026
     * @description Devuelve el endpoint interno que resuelve la foto real del piloto en OpenF1
     * @param {Object} driver Piloto de Jolpica
     * @returns {string} URL de imagen o fallback local
     */
    getDriverImage(driver) {
        const number = driver?.permanentNumber || driver?.permanent_number || driver?.number;
        return number ? `/api/f1/media/driver?number=${number}` : '/assets/img/LogoRS2.png';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Construye el identificador visual usado por Formula1.com para pilotos
     * @param {Object} driver Piloto Jolpica
     * @returns {string} Identificador Formula1.com
     */
    getFormulaOneDriverAssetId(driver) {
        const given = `${driver?.givenName || ''}`.replace(/[^a-z]/gi, '').slice(0, 3).toLowerCase();
        const family = `${driver?.familyName || ''}`.replace(/[^a-z]/gi, '').slice(0, 3).toLowerCase();
        return given && family ? `${given}${family}01` : '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Formatea el nombre del piloto para la ruta de imagen de Formula1.com
     * @param {Object} driver Piloto Jolpica
     * @returns {string} Nombre con guiones bajos
     */
    toFormulaOneName(driver) {
        return [driver?.givenName, driver?.familyName]
            .filter(Boolean)
            .join('_')
            .replace(/[^a-zA-Z_]/g, '');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Valida que una noticia pertenezca claramente a Fórmula 1 antes de mostrarla
     * @param {string} value Texto agregado de la noticia
     * @returns {boolean} Indica si es noticia F1
     */
    isFormulaOneNews(value) {
        return /f[oó]rmula\s*1|formula\s*one|\bf1\b|grand prix|gran premio|fia|verstappen|hamilton|alonso|sainz|norris|leclerc|piastri|russell|antonelli|bearman|bortoleto|lindblad|lawson|tsunoda|ocon|gasly|albon|hulkenberg|hülkenberg|stroll|colapinto|ferrari|mclaren|mercedes|red bull|racing bulls|aston martin|williams|alpine|haas|sauber|cadillac|audi/i.test(value || '');
    }

}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 30-04-2026
 * @description Inicializa Inicio al cargar el DOM
 */
document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamHomePage = new RaceStreamHomePage();
});
