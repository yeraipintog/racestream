/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.0
 * @created 04-05-2026
 * @modified 14-05-2026
 * @description Lógica de Clasificación con limite publico, aviso histórico de constructores, tablas y detalle por GP
 */
class RaceStreamStandingsPage {

    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.params = new URLSearchParams(window.location.search);
        this.year = Number(this.params.get('year')) || new Date().getFullYear();
        this.publicLimited = false;
        this.activeType = this.params.get('type') === 'constructors' ? 'constructors' : 'drivers';
        this.selectedDriver = this.params.get('driverId') || 'all';
        this.selectedConstructor = this.params.get('constructorId') || 'all';
        this.driverStandings = [];
        this.constructorStandings = [];
        this.meetings = [];
        this.raceResults = null;
        this.requestId = 0;
        this.abortController = null;
        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Cachea controles principales de la página
     */
    cacheDom() {
        this.yearInput = document.getElementById('standingsYearInput');
        this.seasonMeta = document.getElementById('standingsSeasonMeta');
        this.driversTab = document.getElementById('standingsDriversTab');
        this.constructorsTab = document.getElementById('standingsConstructorsTab');
        this.tableTitle = document.getElementById('standingsTableTitle');
        this.entityFilter = document.getElementById('standingsEntityFilter');
        this.selectedMedia = document.getElementById('standingsSelectedMedia');
        this.tableContent = document.getElementById('standingsTableContent');
    }

    bindEvents() {
        this.yearInput.addEventListener('change', () => {
            if (this.publicLimited) {
                this.year = new Date().getFullYear();
                this.yearInput.value = String(this.year);
                this.updateUrl();
                return;
            }
            this.year = Number(this.yearInput.value);
            this.selectedDriver = 'all';
            this.selectedConstructor = 'all';
            this.updateUrl();
            this.loadSeason();
        });
        this.driversTab.addEventListener('click', () => this.setActiveType('drivers'));
        this.constructorsTab.addEventListener('click', () => this.setActiveType('constructors'));
        this.entityFilter.addEventListener('change', () => {
            if (this.publicLimited) {
                this.selectedDriver = 'all';
                this.selectedConstructor = 'all';
                this.entityFilter.value = 'all';
                this.updateUrl();
                this.renderActiveTable();
                return;
            }
            if (this.activeType === 'drivers') {
                this.selectedDriver = this.entityFilter.value;
            } else {
                this.selectedConstructor = this.entityFilter.value;
            }
            this.updateUrl();
            this.renderActiveTable();
        });
    }

    async init() {
        await this.resolvePublicAccess();
        await this.loadSeasons();
        await this.loadSeason();
    }

    async resolvePublicAccess() {
        const user = await window.RaceStreamApi.getCurrentUser();
        this.publicLimited = !user?.authenticated;
        if (!this.publicLimited) return;
        this.year = new Date().getFullYear();
        this.selectedDriver = 'all';
        this.selectedConstructor = 'all';
        this.updateUrl();
    }

    async loadSeasons() {
        if (this.publicLimited) {
            this.yearInput.innerHTML = `<option value="${this.year}" selected>${this.year}</option>`;
            this.yearInput.disabled = true;
            return;
        }
        const seasons = await this.fetchJson('/api/f1/standings/seasons', []);
        const safeSeasons = Array.isArray(seasons) && seasons.length
            ? seasons
            : Array.from({ length: new Date().getFullYear() - 1949 }, (_, index) => ({ season: new Date().getFullYear() - index }));
        this.yearInput.innerHTML = safeSeasons
            .map((item) => `<option value="${item.season}" ${Number(item.season) === this.year ? 'selected' : ''}>${item.season}</option>`)
            .join('');
    }

    async loadSeason() {
        const requestId = ++this.requestId;
        this.abortController?.abort();
        this.abortController = new AbortController();
        this.raceResults = null;
        this.driverStandings = [];
        this.constructorStandings = [];
        this.entityFilter.innerHTML = '<option value="">Cargando...</option>';
        this.tableContent.innerHTML = '<p class="loading-state">Cargando clasificación oficial...</p>';
        this.yearInput.disabled = true;
        this.yearInput.setAttribute('aria-busy', 'true');
        this.entityFilter.disabled = true;

        const [drivers, constructors, meetings] = await Promise.all([
            this.fetchArray(`/api/f1/standings/drivers?year=${this.year}`, 4, this.abortController.signal),
            this.fetchArray(`/api/f1/standings/constructors?year=${this.year}`, 4, this.abortController.signal),
            this.fetchArray(`/api/f1/schedule/calendar-meetings?year=${this.year}`, 3, this.abortController.signal)
        ]);

        if (requestId !== this.requestId) return;
        this.raceResults = null;
        this.driverStandings = drivers;
        this.constructorStandings = constructors;
        this.meetings = meetings;
        this.syncTypeControls();
        this.populateEntityFilter();
        this.updateUrl();
        this.yearInput.disabled = this.publicLimited;
        this.yearInput.removeAttribute('aria-busy');
        this.entityFilter.disabled = this.publicLimited;
        this.renderActiveTable();
    }

    setActiveType(type) {
        this.activeType = type;
        this.syncTypeControls();
        this.populateEntityFilter();
        this.updateUrl();
        this.renderActiveTable();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Sincroniza los botones de tipo con la pestaña activa
     */
    syncTypeControls() {
        this.driversTab.classList.toggle('rs-standings-tab--active', this.activeType === 'drivers');
        this.constructorsTab.classList.toggle('rs-standings-tab--active', this.activeType === 'constructors');
        this.driversTab.setAttribute('aria-pressed', this.activeType === 'drivers');
        this.constructorsTab.setAttribute('aria-pressed', this.activeType === 'constructors');
    }

    populateEntityFilter() {
        if (this.activeType === 'drivers') {
            this.tableTitle.textContent = 'Clasificación de pilotos';
            const options = this.driverStandings.map((row) => {
                const driver = row.Driver || {};
                return `<option value="${this.assets.escape(this.getDriverFilterId(row))}">${this.assets.escape(this.assets.getDriverName(driver))}</option>`;
            });
            if (this.selectedDriver !== 'all' && !this.driverStandings.some((row) => this.getDriverFilterId(row) === this.selectedDriver)) {
                this.selectedDriver = 'all';
            }
            this.entityFilter.innerHTML = '<option value="all">Todos los pilotos</option>' + options.join('');
            this.entityFilter.value = this.selectedDriver;
            return;
        }

        this.tableTitle.textContent = 'Clasificación de escuderías';
        const options = this.constructorStandings.map((row) => {
            const constructor = row.Constructor || {};
            return `<option value="${this.assets.escape(this.getConstructorFilterId(row))}">${this.assets.escape(this.assets.getConstructorName(constructor))}</option>`;
        });
        if (this.selectedConstructor !== 'all' && !this.constructorStandings.some((row) => this.getConstructorFilterId(row) === this.selectedConstructor)) {
            this.selectedConstructor = 'all';
        }
        this.entityFilter.innerHTML = '<option value="all">Todas las escuderías</option>' + options.join('');
        this.entityFilter.value = this.selectedConstructor;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Conserva temporada, pestaña y filtro seleccionados al recargar
     */
    updateUrl() {
        const params = new URLSearchParams();
        params.set('year', this.year);
        params.set('type', this.activeType);
        if (this.activeType === 'drivers' && this.selectedDriver !== 'all') params.set('driverId', this.selectedDriver);
        if (this.activeType === 'constructors' && this.selectedConstructor !== 'all') params.set('constructorId', this.selectedConstructor);
        window.history.replaceState({}, '', `?${params.toString()}`);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Devuelve identificador estable de piloto para filtros y enlaces
     * @param {Object} row Fila de piloto
     * @returns {string} Identificador estable
     */
    getDriverFilterId(row) {
        const driver = row?.Driver || {};
        return driver.driverId || row?.stable_id || this.assets.normalize(this.assets.getDriverName(driver));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Devuelve identificador estable de escuderia para filtros y enlaces
     * @param {Object} row Fila de escuderia
     * @returns {string} Identificador estable
     */
    getConstructorFilterId(row) {
        const constructor = row?.Constructor || {};
        return constructor.constructorId || row?.stable_id || this.assets.normalize(this.assets.getConstructorName(constructor));
    }

    async renderActiveTable() {
        const requestId = ++this.requestId;
        this.renderSelectedMedia();
        this.updateSeasonMeta();

        if (this.activeType === 'drivers' && this.selectedDriver !== 'all') {
            this.tableContent.innerHTML = '<p class="loading-state">Cargando resultados por Gran Premio...</p>';
            const races = await this.loadRaceResults();
            if (requestId !== this.requestId) return;
            this.renderDriverDetailTable(races);
            return;
        }

        if (this.activeType === 'constructors' && this.selectedConstructor !== 'all') {
            this.tableContent.innerHTML = '<p class="loading-state">Cargando resultados por Gran Premio...</p>';
            const races = await this.loadRaceResults();
            if (requestId !== this.requestId) return;
            this.renderConstructorDetailTable(races);
            return;
        }

        if (this.activeType === 'drivers') {
            this.renderDriversTable();
        } else {
            this.renderConstructorsTable();
        }
    }

    renderDriversTable() {
        if (!this.driverStandings.length) {
            this.renderApiRetry('Pilotos');
            return;
        }
        this.tableContent.innerHTML = `
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table">
                    <thead><tr><th>Posición</th><th>Piloto</th><th>Nacionalidad</th><th>Escudería</th><th>Puntos</th></tr></thead>
                    <tbody>${this.driverStandings.map((row) => this.renderDriverStandingRow(row)).join('')}</tbody>
                </table>
            </div>
        `;
        this.assets.hydrateDriverImages(this.tableContent, this.fetchJson.bind(this), this.loadImage.bind(this));
    }

    renderDriverStandingRow(row) {
        const driver = row.Driver || {};
        const constructor = (row.Constructors || [])[0] || {};
        const driverUrl = `/drivers.html?year=${this.year}&driverId=${this.assets.escape(this.getDriverFilterId(row))}`;
        return `
            <tr>
                <td><strong class="rs-standings-position">${this.assets.escape(row.position || '-')}</strong></td>
                <td>
                    <a class="rs-standings-person-cell" href="${driverUrl}">
                        ${this.assets.personAvatar(driver, 'rs-standings-avatar', { constructor, year: this.year })}
                        <strong>${this.assets.escape(this.assets.getDriverName(driver))}</strong>
                    </a>
                </td>
                <td>${this.assets.nationalityBadge(driver.nationality)}</td>
                <td><span class="rs-standings-team-cell">${this.assets.teamMark(constructor, this.year)}<span>${this.assets.escape(this.assets.getConstructorName(constructor))}</span></span></td>
                <td><strong class="rs-standings-points">${this.assets.formatPoints(row.points)} pts</strong></td>
            </tr>
        `;
    }

    renderConstructorsTable() {
        const historicalNotice = this.renderHistoricalConstructorsNotice();
        if (!this.constructorStandings.length) {
            if (this.year < 1958) {
                this.tableContent.innerHTML = `${historicalNotice}<p class="empty-state">No hay datos históricos de escuderías para esta temporada.</p>`;
            } else {
                this.renderApiRetry('Escuderías');
            }
            return;
        }
        this.tableContent.innerHTML = `
            ${historicalNotice}
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table">
                    <thead><tr><th>Posición</th><th>Escudería</th><th>Nacionalidad</th><th>Pilotos</th><th>Puntos</th></tr></thead>
                    <tbody>${this.constructorStandings.map((row) => this.renderConstructorStandingRow(row)).join('')}</tbody>
                </table>
            </div>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Devuelve aviso histórico para datos de escuderías anteriores a 1958
     * @returns {string} HTML del aviso
     */
    renderHistoricalConstructorsNotice() {
        if (this.year >= 1958) return '';
        return `
            <div class="rs-historical-notice">
                El Campeonato de Constructores empezó oficialmente en 1958. En temporadas anteriores estos datos son una referencia histórica por escudería/equipo y no una clasificación oficial del campeonato.
            </div>
        `;
    }

    renderConstructorStandingRow(row) {
        const constructor = row.Constructor || {};
        const color = this.assets.getTeamColor(constructor, this.year);
        const drivers = this.getConstructorDrivers(this.getConstructorFilterId(row))
            .map((driver) => this.assets.getDriverName(driver))
            .join(' / ');
        const teamUrl = `/teams.html?year=${this.year}&constructorId=${this.assets.escape(this.getConstructorFilterId(row))}`;
        return `
            <tr style="--team-color:${color}">
                <td><strong class="rs-standings-position">${this.assets.escape(row.position || '-')}</strong></td>
                <td>
                    <a class="rs-standings-team-cell" href="${teamUrl}">
                        ${this.assets.teamMark(constructor, this.year)}
                        <strong>${this.assets.escape(this.assets.getConstructorName(constructor))}</strong>
                    </a>
                </td>
                <td>${this.assets.nationalityBadge(constructor.nationality)}</td>
                <td>${this.assets.escape(drivers || '-')}</td>
                <td><strong class="rs-standings-points">${this.assets.formatPoints(row.points)} pts</strong></td>
            </tr>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Muestra recarga manual cuando faltan datos obligatorios de API
     * @param {string} label Datos esperados
     */
    renderApiRetry(label) {
        this.tableContent.innerHTML = window.RaceStreamApi.retryButton(label, 'No se han podido cargar los datos oficiales.');
    }

    renderDriverDetailTable(races) {
        const selectedRows = races.map((race) => ({ race, result: this.findDriverRaceResult(race, this.selectedDriver) }));

        if (!races.length) {
            this.renderApiRetry('Resultados');
            return;
        }

        this.tableContent.innerHTML = `
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table rs-standings-table--detail">
                    <thead><tr><th>Gran Premio</th><th>Fechas</th><th>Posición final carrera</th><th>Puntos</th></tr></thead>
                    <tbody>${selectedRows.map(({ race, result }) => `
                        <tr>
                            <td>${this.renderRaceCell(race)}</td>
                            <td>${this.formatRaceDateRange(race)}</td>
                            <td>${result ? this.assets.escape(this.formatRacePosition(result)) : 'Sin participación / sin resultado'}</td>
                            <td><strong class="rs-standings-points">${result ? this.assets.formatPoints(result.points) : '0'} pts</strong></td>
                        </tr>
                    `).join('')}</tbody>
                </table>
            </div>
        `;
    }

    renderConstructorDetailTable(races) {
        const selectedRows = races.map((race) => ({ race, results: this.findConstructorRaceResults(race, this.selectedConstructor) }));

        if (!races.length) {
            this.renderApiRetry('Resultados');
            return;
        }

        this.tableContent.innerHTML = `
            ${this.renderHistoricalConstructorsNotice()}
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table rs-standings-table--detail">
                    <thead><tr><th>Gran Premio</th><th>Fechas</th><th>Posición final carrera</th><th>Puntos totales</th></tr></thead>
                    <tbody>${selectedRows.map(({ race, results }) => `
                        <tr>
                            <td>${this.renderRaceCell(race)}</td>
                            <td>${this.formatRaceDateRange(race)}</td>
                            <td><div class="rs-standings-result-list">${results.length ? results.map((result) => this.renderDriverRaceResult(result)).join('') : 'Sin participación / sin resultado'}</div></td>
                            <td><strong class="rs-standings-points">${this.assets.formatPoints(results.reduce((total, result) => total + Number(result.points || 0), 0))} pts</strong></td>
                        </tr>
                    `).join('')}</tbody>
                </table>
            </div>
        `;
        this.assets.hydrateDriverImages(this.tableContent, this.fetchJson.bind(this), this.loadImage.bind(this));
    }

    renderSelectedMedia() {
        if (this.activeType === 'drivers' && this.selectedDriver !== 'all') {
            const row = this.driverStandings.find((item) => this.getDriverFilterId(item) === this.selectedDriver);
            const constructor = (row?.Constructors || [])[0] || {};
            this.selectedMedia.innerHTML = row ? `
                <span class="rs-standings-selected-media__group">
                    ${this.assets.personAvatar(row.Driver, 'rs-standings-selected-media__avatar', { constructor, year: this.year })}
                    ${this.assets.teamMark(constructor, this.year, 'rs-standings-selected-media__team')}
                </span>
            ` : '';
            this.assets.hydrateDriverImages(this.selectedMedia, this.fetchJson.bind(this), this.loadImage.bind(this));
            return;
        }

        if (this.activeType === 'constructors' && this.selectedConstructor !== 'all') {
            const row = this.constructorStandings.find((item) => this.getConstructorFilterId(item) === this.selectedConstructor);
            const drivers = this.getConstructorDrivers(this.selectedConstructor);
            this.selectedMedia.innerHTML = row ? `
                <span class="rs-standings-selected-media__group">
                    ${this.assets.teamMark(row.Constructor, this.year, 'rs-standings-selected-media__team')}
                    ${drivers.map((driver) => this.assets.personAvatar(driver, 'rs-standings-selected-media__avatar', { constructor: row.Constructor, year: this.year })).join('')}
                </span>
            ` : '';
            this.assets.hydrateDriverImages(this.selectedMedia, this.fetchJson.bind(this), this.loadImage.bind(this));
            return;
        }

        this.selectedMedia.innerHTML = '';
    }

    renderDriverRaceResult(result) {
        const driver = result.Driver || {};
        return `
            <span class="rs-standings-result-driver">
                ${this.assets.personAvatar(driver, 'rs-standings-result-driver__avatar', { constructor: result.Constructor, year: this.year })}
                <span>${this.assets.escape(this.assets.getDriverName(driver))} · ${this.assets.escape(this.formatRacePosition(result))}</span>
            </span>
        `;
    }

    renderRaceCell(race) {
        const meeting = this.findMeetingForRace(race);
        const country = meeting?.country_name || meeting?.jolpica_country || race.Circuit?.Location?.country || '';
        const flag = meeting?.country_flag;
        return `
            <span class="rs-standings-race-cell">
                ${flag ? `<img class="rs-flag-inline" src="${flag}" alt="Bandera de ${this.assets.escape(country)}">` : this.assets.countryFlag(country)}
                <strong>${this.assets.escape(race.raceName || meeting?.meeting_name || 'Gran Premio')}</strong>
            </span>
        `;
    }

    renderCodePill(code, label) {
        return `<span class="rs-standings-code" title="${this.assets.escape(label || code)}">${this.assets.escape(code)}</span>`;
    }

    async loadRaceResults() {
        if (!this.raceResults) {
            this.raceResults = await this.fetchArray(`/api/f1/standings/race-results?year=${this.year}`, 4, this.abortController?.signal);
        }
        return this.raceResults;
    }

    findDriverRaceResult(race, driverId) {
        const selected = this.driverStandings.find((row) => this.getDriverFilterId(row) === driverId)?.Driver || {};
        const selectedName = this.assets.normalize(this.assets.getDriverName(selected));
        return (race.Results || []).find((result) => {
            const driver = result.Driver || {};
            const resultName = this.assets.normalize(this.assets.getDriverName(driver));
            return driver.driverId === driverId
                || (selected.code && driver.code === selected.code)
                || (selected.familyName && this.assets.normalize(driver.familyName) === this.assets.normalize(selected.familyName))
                || (selectedName && resultName === selectedName);
        });
    }

    findConstructorRaceResults(race, constructorId) {
        const selected = this.constructorStandings.find((row) => this.getConstructorFilterId(row) === constructorId)?.Constructor || {};
        return (race.Results || []).filter((result) => {
            const constructor = result.Constructor || {};
            return constructor.constructorId === constructorId
                || this.assets.normalize(constructor.name) === this.assets.normalize(selected.name)
                || this.assets.getTeamSlug(constructor.constructorId || constructor.name, this.year)
                    === this.assets.getTeamSlug(selected.constructorId || selected.name, this.year);
        });
    }

    getConstructorDrivers(constructorId) {
        const selected = this.constructorStandings.find((row) => this.getConstructorFilterId(row) === constructorId)?.Constructor || {};
        const selectedSlug = this.assets.getTeamSlug(selected.constructorId || selected.name, this.year);
        return this.driverStandings
            .filter((row) => (row.Constructors || []).some((constructor) =>
                constructor.constructorId === constructorId
                || this.assets.getTeamSlug(constructor.constructorId || constructor.name, this.year) === selectedSlug))
            .map((row) => row.Driver)
            .filter(Boolean)
            .slice(0, 2);
    }

    findMeetingForRace(race) {
        const round = `${race.round || ''}`;
        return this.meetings.find((meeting) => `${meeting.jolpica_round || ''}` === round)
            || this.meetings.find((meeting) => this.assets.normalize(meeting.meeting_name) === this.assets.normalize(race.raceName));
    }

    formatRaceDateRange(race) {
        const meeting = this.findMeetingForRace(race);
        if (meeting?.date_start && meeting?.date_end) {
            return this.formatDateRange(meeting.date_start, meeting.date_end);
        }
        return race.date ? new Date(race.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
    }

    formatDateRange(startValue, endValue) {
        const start = new Date(startValue);
        const end = new Date(endValue);
        const startText = start.toLocaleDateString('es-ES', { day: '2-digit', month: 'short' });
        const endText = end.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
        const sameDay = start.getFullYear() === end.getFullYear()
            && start.getMonth() === end.getMonth()
            && start.getDate() === end.getDate();
        if (sameDay) {
            return end.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
        }
        return `${startText} - ${endText}`;
    }

    formatRacePosition(result) {
        const translated = this.translateRaceStatus(result);
        if (translated.code === 'OK') {
            return `P${result.position || result.positionOrder || result.positionText || '-'}`;
        }
        if (translated.code === 'LAP') {
            return `P${result.positionOrder || result.position || result.positionText || '-'} · ${translated.label}`;
        }
        return `${translated.code} · ${translated.label}`;
    }

    translateRaceStatus(result) {
        const raw = `${result?.status || ''}`.trim();
        const positionText = `${result?.positionText || ''}`.trim().toUpperCase();
        if (/did not start|withdrawn/i.test(raw) || positionText === 'W') return { code: 'DNS', label: 'No tomó la salida' };
        if (/disqualified/i.test(raw) || positionText === 'D') return { code: 'DSQ', label: 'Descalificado' };
        if (/retired|accident|collision|engine|gearbox|brake|electrical|spun off|puncture|overheating|transmission|hydraulics/i.test(raw) || positionText === 'R') {
            return { code: 'DNF', label: raw ? this.translateStatusText(raw) : 'Retirado' };
        }
        if (/lapped|\+\d+\s*lap/i.test(raw)) return { code: 'LAP', label: this.translateStatusText(raw) };
        if (!raw || /finished/i.test(raw)) return { code: 'OK', label: 'Finalizado' };
        return { code: 'INFO', label: this.translateStatusText(raw) };
    }

    translateStatusText(value) {
        const raw = `${value || ''}`.trim();
        const normalized = raw.toLowerCase();
        if (normalized === 'lapped') return 'Doblado';
        if (normalized === 'retired') return 'Retirado';
        if (normalized === 'did not start') return 'No tomó la salida';
        if (normalized === 'disqualified') return 'Descalificado';
        const lapMatch = raw.match(/\+?(\d+)\s*lap/i);
        if (lapMatch) {
            const laps = Number(lapMatch[1]);
            return `+${laps} ${laps === 1 ? 'vuelta' : 'vueltas'}`;
        }
        return raw || '-';
    }

    updateSeasonMeta() {
        const count = this.activeType === 'drivers' ? this.driverStandings.length : this.constructorStandings.length;
        this.seasonMeta.textContent = `Temporada ${this.year} · ${count} ${this.activeType === 'drivers' ? 'pilotos' : 'escuderías'}`;
    }

    async fetchArray(url, attempts = 3, signal = undefined) {
        return window.RaceStreamApi.fetchArray(url, { attempts, signal });
    }

    async fetchJson(url, fallback, options = {}) {
        return window.RaceStreamApi.fetchJson(url, fallback, options);
    }

    loadImage(url) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve();
            image.onerror = () => reject();
            image.src = url;
        });
    }

    wait(milliseconds) {
        return window.RaceStreamApi.wait(milliseconds);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamStandingsPage = new RaceStreamStandingsPage();
});
