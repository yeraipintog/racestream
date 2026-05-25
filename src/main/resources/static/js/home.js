/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.5.1
 * @created 30-04-2026
 * @modified 19-05-2026
 * @description Lógica de Inicio con contadores estables, última carrera,
 *              próxima sesión, estadísticas reales y ayuda contextual
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
        this.raceResultsApi = `/api/f1/standings/race-results?year=${this.year}`;
        this.liveStatusApi = '/api/f1/live/status';
        this.newsApi = '/api/news/f1?limit=10';
        this.nextMeeting = null;
        this.nextSession = null;
        this.liveStatus = null;
        this.refreshingSession = false;
        this.glossary = {
            SC: ['Safety Car', 'Coche de seguridad que reduce el ritmo y agrupa a los pilotos cuando hay peligro en pista.'],
            VSC: ['Virtual Safety Car', 'Periodo controlado en el que todos reducen velocidad sin que salga un coche físico.'],
            DNF: ['Did Not Finish', 'El piloto tomó la salida, pero no llegó al final por avería, accidente u otro problema.'],
            ERS: ['Energy Recovery System', 'Sistema que recupera energía y la entrega como potencia eléctrica adicional.']
        };

        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 07-05-2026
     * @description Cachea los elementos usados por Inicio
     */
    cacheDom() {
        this.heroSessionStatus = document.getElementById('heroSessionStatus');
        this.heroSessionTitle = document.getElementById('heroSessionTitle');
        this.heroSessionMeta = document.getElementById('heroSessionMeta');
        this.heroCountdown = document.getElementById('heroCountdown');
        this.lastSessionTitle = document.getElementById('lastSessionTitle');
        this.lastSessionLink = document.getElementById('lastSessionLink');
        this.lastSessionResults = document.getElementById('lastSessionResults');
        this.seasonStatsPanel = document.getElementById('seasonStatsPanel');
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
     * @description Asocia eventos propios de Inicio y glosario
     */
    bindEvents() {
        document.querySelectorAll('[data-term]').forEach((button) => {
            button.addEventListener('click', () => this.showGlossaryTerm(button.dataset.term));
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Inicializa la página y refresca relojes
     */
    init() {
        this.renderFallbackBlocks();
        this.loadHomeData();
        this.loadLastSession();
        this.loadStandings();
        this.loadSeasonStats();
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
        return window.RaceStreamApi.fetchJson(url, fallback, { attempts: 3, delayBase: 180 });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 07-05-2026
     * @description Carga calendario y calcula próximo GP/sesión
     */
    async loadHomeData() {
        const [meetings, liveStatus] = await Promise.all([
            this.fetchJson(this.meetingsApi, []),
            this.fetchJson(this.liveStatusApi, null)
        ]);
        const safeMeetings = Array.isArray(meetings) ? meetings : [];
        this.liveStatus = this.isActiveLiveStatus(liveStatus) ? liveStatus : null;
        this.nextMeeting = this.liveStatus
            ? this.meetingFromLiveStatus(this.liveStatus, safeMeetings)
            : this.getCurrentOrNextMeeting(safeMeetings);
        await this.loadNextSession();
        this.renderHeroSession();
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

    isActiveLiveStatus(status) {
        const value = `${status?.status || ''}`.toLowerCase();
        return Boolean(status?.session?.session_key)
            && (value.includes('directo') || value.includes('retrasada') || value.includes('esperando datos'));
    }

    meetingFromLiveStatus(status, meetings) {
        const session = status?.session || {};
        const meetingKey = `${session.meeting_key || ''}`;
        const scheduled = meetings.find((meeting) => `${meeting.meeting_key || ''}` === meetingKey);
        if (scheduled) {
            return scheduled;
        }
        return {
            meeting_key: session.meeting_key,
            meeting_name: session.meeting_name,
            meeting_official_name: session.meeting_official_name,
            country_name: session.country_name,
            country_flag: session.country_flag,
            location: session.location,
            date_start: session.date_start,
            date_end: session.date_end,
            gmt_offset: session.gmt_offset
        };
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga sesiones del próximo GP para elegir la siguiente
     */
    async loadNextSession() {
        if (this.liveStatus?.session?.session_key) {
            this.nextSession = this.liveStatus.session;
            return;
        }
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
     * @description Renderiza la tarjeta principal de próxima sesión
     */
    renderHeroSession() {
        const session = this.nextSession;
        const meeting = this.nextMeeting;

        if (!meeting) {
            this.heroSessionTitle.textContent = 'Recarga forzada';
            this.heroSessionMeta.innerHTML = 'La API debe cargar calendario. <button class="rs-link-chip" type="button" onclick="window.location.reload()">Reintentar</button>';
            return;
        }

        const header = document.querySelector('.rs-home-next-card .rs-home-card__header span');
        if (header) {
            header.textContent = this.liveStatus
                ? `En Vivo · ${this.liveStatus.status || 'Sesión actual'}`
                : `Próxima sesión · ${this.formatSessionDate(session?.date_start || meeting.date_start)}`;
        }
        this.heroSessionTitle.textContent = session
            ? `${this.translateSessionName(session.session_name)}`
            : 'Sesión por confirmar';
        this.heroSessionMeta.innerHTML = `
            <span class="rs-home-session-meta">
                ${meeting.country_flag ? `<img src="${meeting.country_flag}" alt="Bandera de ${meeting.country_name || 'país'}">` : ''}
                <span>${this.escapeHtml(this.getMeetingPlaceLabel(meeting))}</span>
            </span>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Carga la última carrera completada con resultados reales antes de mostrarla
     */
    async loadLastSession() {
        if (!this.lastSessionResults) return;
        const [meetings, liveStatus] = await Promise.all([
            this.fetchJson(this.meetingsApi, []),
            this.fetchJson(this.liveStatusApi, null)
        ]);
        const activeSessionKey = this.isActiveLiveStatus(liveStatus)
            ? `${liveStatus.session.session_key || ''}`
            : '';
        const completedMeetings = (Array.isArray(meetings) ? meetings : [])
            .filter((meeting) => Number(meeting.meeting_key) > 0 && new Date(meeting.date_end).getTime() < Date.now())
            .sort((left, right) => new Date(right.date_end).getTime() - new Date(left.date_end).getTime())
            .slice(0, 4);

        for (const meeting of completedMeetings) {
            const sessions = await this.fetchJson(this.sessionsByMeetingApi(meeting.meeting_key), []);
            const completedSessions = (Array.isArray(sessions) ? sessions : [])
                .filter((session) => session.session_key && new Date(session.date_end).getTime() < Date.now())
                .filter((session) => `${session.session_key}` !== activeSessionKey)
                .filter((session) => this.isRaceSession(session.session_name))
                .sort((left, right) => new Date(right.date_end).getTime() - new Date(left.date_end).getTime());
            for (const session of completedSessions) {
                const results = await this.fetchJson(this.resultsApi(session.session_key), []);
                if (!Array.isArray(results) || !results.length) continue;
                const drivers = await this.fetchJson(this.driversApi(session.session_key), []);
                this.renderLastSession(meeting, session, results, Array.isArray(drivers) ? drivers : []);
                return;
            }
        }

        this.lastSessionTitle.textContent = 'Última Carrera no disponible';
        this.lastSessionResults.innerHTML = '<span class="empty-state">OpenF1 todavía no ofrece resultados recientes.</span>';
    }

    renderLastSession(meeting, session, results, drivers) {
        const driverMap = new Map(drivers.map((driver) => [Number(driver.driver_number), driver]));
        const sorted = [...results].sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999));
        this.lastSessionTitle.innerHTML = `
            ${this.renderMeetingFlag(meeting)}
            <span class="rs-home-last-session__gp">${this.escapeHtml(this.getMeetingPlaceLabel(meeting))}</span>
        `;
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
                this.getDriverImage(row.Driver, this.getDriverConstructor(row)),
                'driver'
            ]));
        } else {
            this.renderApiRetry(this.driversStandings, 'Pilotos');
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
        } else {
            this.renderApiRetry(this.teamsStandings, 'Escuderías');
        }

    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 24-05-2026
     * @description Calcula estadísticas compactas de temporada con datos reales
     *              disponibles y estados claros si falta una métrica
     */
    async loadSeasonStats() {
        if (!this.seasonStatsPanel) return;
        this.seasonStatsPanel.innerHTML = '<div class="loading-state">Calculando estadísticas...</div>';
        const [drivers, constructors, races] = await Promise.all([
            this.fetchJson(this.driverStandingsApi, []),
            this.fetchJson(this.constructorStandingsApi, []),
            this.fetchJson(this.raceResultsApi, [])
        ]);
        const safeDrivers = Array.isArray(drivers) ? drivers : [];
        const safeConstructors = Array.isArray(constructors) ? constructors : [];
        const topWins = this.topDriverByField(safeDrivers, 'wins', 'victorias');
        const topFastestLaps = this.topFastestLaps(Array.isArray(races) ? races : []);
        const stats = [
            topWins,
            this.driverChampionshipLeader(safeDrivers),
            topFastestLaps,
            this.constructorChampionshipLeader(safeConstructors)
        ];
        this.seasonStatsPanel.innerHTML = stats.map((item) => `
            <div class="rs-home-season-stat">
                <span>${this.escapeHtml(item.label)}</span>
                <strong>${this.escapeHtml(item.value)}</strong>
                <small>${this.escapeHtml(item.meta)}</small>
            </div>
        `).join('');
    }

    topDriverByField(rows, field, suffix) {
        const sorted = [...rows]
            .map((row) => ({
                name: this.getDriverName(row.Driver),
                value: Number(row?.[field] || 0)
            }))
            .filter((row) => row.value > 0)
            .sort((left, right) => right.value - left.value);
        if (!sorted.length) {
            return this.pendingStat('Más victorias', 'Jolpica todavía no devuelve victorias para esta temporada.');
        }
        const leader = sorted[0];
        return {
            label: 'Más victorias',
            value: leader.name,
            meta: `${leader.value} ${leader.value === 1 ? 'victoria' : suffix}`
        };
    }

    driverChampionshipLeader(rows) {
        const leader = [...rows]
            .filter((row) => row?.Driver)
            .sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999))[0];
        if (!leader) {
            return this.pendingStat('Líder del Mundial', 'Jolpica todavía no devuelve clasificación de pilotos.');
        }
        return {
            label: 'Líder del Mundial',
            value: this.getDriverName(leader.Driver),
            meta: `${leader.points || 0} pts`
        };
    }

    constructorChampionshipLeader(rows) {
        const leader = [...rows]
            .filter((row) => row?.Constructor)
            .sort((left, right) => (Number(left.position) || 999) - (Number(right.position) || 999))[0];
        if (!leader) {
            return this.pendingStat('Mejor escudería', 'Jolpica todavía no devuelve clasificación de constructores.');
        }
        return {
            label: 'Mejor escudería',
            value: leader.Constructor?.name || 'Escudería',
            meta: `${leader.points || 0} pts`
        };
    }

    topFastestLaps(races) {
        const counts = new Map();
        races.forEach((race) => {
            const results = race?.Results || race?.results || [];
            results.forEach((result) => {
                const rank = result?.FastestLap?.rank || result?.fastestLap?.rank;
                if (`${rank}` !== '1') return;
                const name = this.getDriverName(result.Driver || result.driver);
                counts.set(name, (counts.get(name) || 0) + 1);
            });
        });
        const sorted = [...counts.entries()].sort((left, right) => right[1] - left[1]);
        if (!sorted.length) {
            return this.pendingStat('Más vueltas rápidas', 'Jolpica todavía no devuelve vueltas rápidas suficientes.');
        }
        const [name, total] = sorted[0];
        return {
            label: 'Más vueltas rápidas',
            value: name,
            meta: `${total} ${total === 1 ? 'vuelta rápida' : 'vueltas rápidas'}`
        };
    }

    pendingStat(label, message) {
        return {
            label,
            value: 'Pendiente',
            meta: message
        };
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
            this.renderApiRetry(this.homeNews, 'Noticias');
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
     * @description Renderiza estados de espera mientras llegan datos reales
     */
    renderFallbackBlocks() {
        this.driversStandings.innerHTML = '<div class="loading-state">Cargando datos oficiales...</div>';
        this.teamsStandings.innerHTML = '<div class="loading-state">Cargando datos oficiales...</div>';
        this.homeNews.innerHTML = '<div class="loading-state">Cargando noticias...</div>';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Muestra recarga manual cuando falta una carga obligatoria de API
     * @param {HTMLElement} target Contenedor
     * @param {string} label Datos esperados
     */
    renderApiRetry(target, label) {
        target.innerHTML = `
            <div class="rs-api-retry empty-state">
                <strong>${label}</strong>
                <span>Recarga forzada</span>
                <button class="rs-button" type="button" onclick="window.location.reload()">Reintentar</button>
            </div>
        `;
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
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 07-05-2026
     * @description Actualiza la cuenta atras de Inicio sin sobrescribir la franja comun
     */
    updateDynamicTime() {
        this.heroCountdown.textContent = this.getCountdown(this.nextSession?.date_start || this.nextMeeting?.date_start);
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
        const parts = [];
        if (days > 0) parts.push(`${days}d`);
        if (hours > 0) parts.push(`${hours}h`);
        parts.push(`${minutes}m`, `${seconds}s`);
        return parts.join(' ');
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

    isRaceSession(name) {
        const value = `${name || ''}`.toLowerCase();
        return value === 'race' || value === 'sprint';
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

    getDriverConstructor(row) {
        return Array.isArray(row?.Constructors) ? row.Constructors[0] : row?.Constructor;
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

    renderMeetingFlag(meeting) {
        if (!meeting?.country_flag) return '';
        const country = this.escapeHtml(this.translateCountry(meeting.country_name || 'país'));
        const flag = this.escapeHtml(meeting.country_flag);
        return `<img class="rs-flag-inline" src="${flag}" alt="Bandera de ${country}">`;
    }

    getMeetingLocationLabel(meeting) {
        return meeting?.location || meeting?.jolpica_locality || meeting?.circuit_short_name || meeting?.meeting_name || 'Gran Premio';
    }

    getMeetingCountryLabel(meeting) {
        return this.translateCountry(meeting?.country_name || meeting?.jolpica_country || 'país');
    }

    getMeetingPlaceLabel(meeting) {
        const location = this.getMeetingLocationLabel(meeting);
        const country = this.getMeetingCountryLabel(meeting);
        if (!location || location === country) return country;
        return `${location}, ${country}`;
    }

    translateCountry(country) {
        const labels = {
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
        const value = `${country || ''}`.trim();
        return labels[value] || value || 'país';
    }

    escapeHtml(value) {
        return `${value ?? ''}`
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
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
    getDriverImage(driver, constructor = null) {
        const assetImage = this.assets?.getDriverImage?.(driver, constructor, this.year, 64);
        if (assetImage) {
            return assetImage;
        }
        const number = this.assets?.getDriverNumber?.(driver)
            || driver?.permanentNumber
            || driver?.permanent_number
            || driver?.number;
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
