/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 04-05-2026
 * @modified 05-05-2026
 * @description Lógica de Clasificación con temporadas históricas, tablas de pilotos, escuderías, banderas y detalle por GP
 */
class RaceStreamStandingsPage {

    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.params = new URLSearchParams(window.location.search);
        this.year = Number(this.params.get('year')) || new Date().getFullYear();
        this.activeType = 'drivers';
        this.selectedDriver = 'all';
        this.selectedConstructor = 'all';
        this.driverStandings = [];
        this.constructorStandings = [];
        this.meetings = [];
        this.raceResults = null;
        this.requestId = 0;
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
            this.year = Number(this.yearInput.value);
            this.selectedDriver = 'all';
            this.selectedConstructor = 'all';
            window.history.replaceState({}, '', `?year=${this.year}`);
            this.loadSeason();
        });
        this.driversTab.addEventListener('click', () => this.setActiveType('drivers'));
        this.constructorsTab.addEventListener('click', () => this.setActiveType('constructors'));
        this.entityFilter.addEventListener('change', () => {
            if (this.activeType === 'drivers') {
                this.selectedDriver = this.entityFilter.value;
            } else {
                this.selectedConstructor = this.entityFilter.value;
            }
            this.renderActiveTable();
        });
    }

    async init() {
        await this.loadSeasons();
        await this.loadSeason();
    }

    async loadSeasons() {
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
        this.raceResults = null;
        this.tableContent.innerHTML = '<p class="loading-state">Cargando clasificación oficial...</p>';
        this.entityFilter.disabled = true;

        const [drivers, constructors, meetings] = await Promise.all([
            this.fetchArray(`/api/f1/standings/drivers?year=${this.year}`, 4),
            this.fetchArray(`/api/f1/standings/constructors?year=${this.year}`, 4),
            this.fetchArray(`/api/f1/schedule/calendar-meetings?year=${this.year}`, 3)
        ]);

        if (requestId !== this.requestId) return;
        this.driverStandings = drivers;
        this.constructorStandings = constructors;
        this.meetings = meetings;
        this.populateEntityFilter();
        this.entityFilter.disabled = false;
        this.renderActiveTable();
    }

    setActiveType(type) {
        this.activeType = type;
        this.driversTab.classList.toggle('rs-standings-tab--active', type === 'drivers');
        this.constructorsTab.classList.toggle('rs-standings-tab--active', type === 'constructors');
        this.driversTab.setAttribute('aria-pressed', type === 'drivers');
        this.constructorsTab.setAttribute('aria-pressed', type === 'constructors');
        this.populateEntityFilter();
        this.renderActiveTable();
    }

    populateEntityFilter() {
        if (this.activeType === 'drivers') {
            this.tableTitle.textContent = 'Clasificación de pilotos';
            this.entityFilter.innerHTML = '<option value="all">Todos los pilotos</option>' + this.driverStandings.map((row) => {
                const driver = row.Driver || {};
                return `<option value="${this.assets.escape(driver.driverId)}">${this.assets.escape(this.assets.getDriverName(driver))}</option>`;
            }).join('');
            this.entityFilter.value = this.selectedDriver;
            return;
        }

        this.tableTitle.textContent = 'Clasificación de escuderías';
        this.entityFilter.innerHTML = '<option value="all">Todas las escuderías</option>' + this.constructorStandings.map((row) => {
            const constructor = row.Constructor || {};
            return `<option value="${this.assets.escape(constructor.constructorId)}">${this.assets.escape(this.assets.getConstructorName(constructor))}</option>`;
        }).join('');
        this.entityFilter.value = this.selectedConstructor;
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
            this.tableContent.innerHTML = '<p class="empty-state">No hay clasificación de pilotos disponible para esta temporada.</p>';
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
        return `
            <tr>
                <td><strong class="rs-standings-position">${this.assets.escape(row.position || '-')}</strong></td>
                <td><a class="rs-standings-person-cell" href="/drivers.html?year=${this.year}&driverId=${this.assets.escape(driver.driverId)}">${this.assets.personAvatar(driver, 'rs-standings-avatar', { constructor, year: this.year })}<strong>${this.assets.escape(this.assets.getDriverName(driver))}</strong></a></td>
                <td>${this.assets.nationalityBadge(driver.nationality)}</td>
                <td><span class="rs-standings-team-cell">${this.assets.teamMark(constructor, this.year)}<span>${this.assets.escape(this.assets.getConstructorName(constructor))}</span></span></td>
                <td><strong class="rs-standings-points">${this.assets.formatPoints(row.points)} pts</strong></td>
            </tr>
        `;
    }

    renderConstructorsTable() {
        if (!this.constructorStandings.length) {
            this.tableContent.innerHTML = '<p class="empty-state">No hay clasificación de escuderías disponible para esta temporada.</p>';
            return;
        }
        this.tableContent.innerHTML = `
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table">
                    <thead><tr><th>Posición</th><th>Escudería</th><th>Nacionalidad</th><th>Motor</th><th>Puntos</th></tr></thead>
                    <tbody>${this.constructorStandings.map((row) => this.renderConstructorStandingRow(row)).join('')}</tbody>
                </table>
            </div>
        `;
    }

    renderConstructorStandingRow(row) {
        const constructor = row.Constructor || {};
        const color = this.assets.getTeamColor(constructor, this.year);
        return `
            <tr style="--team-color:${color}">
                <td><strong class="rs-standings-position">${this.assets.escape(row.position || '-')}</strong></td>
                <td><a class="rs-standings-team-cell" href="/teams.html?year=${this.year}&constructorId=${this.assets.escape(constructor.constructorId)}">${this.assets.teamMark(constructor, this.year)}<strong>${this.assets.escape(this.assets.getConstructorName(constructor))}</strong></a></td>
                <td>${this.assets.nationalityBadge(constructor.nationality)}</td>
                <td>${this.assets.escape(this.assets.getEngineName(constructor))}</td>
                <td><strong class="rs-standings-points">${this.assets.formatPoints(row.points)} pts</strong></td>
            </tr>
        `;
    }

    renderDriverDetailTable(races) {
        const selectedRows = races
            .map((race) => ({ race, result: this.findDriverRaceResult(race, this.selectedDriver) }))
            .filter((item) => item.result);

        if (!selectedRows.length) {
            this.tableContent.innerHTML = '<p class="empty-state">No hay resultados de carrera para este piloto en la temporada seleccionada.</p>';
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
                            <td>${this.assets.escape(this.formatRacePosition(result))}</td>
                            <td><strong class="rs-standings-points">${this.assets.formatPoints(result.points)} pts</strong></td>
                        </tr>
                    `).join('')}</tbody>
                </table>
            </div>
        `;
    }

    renderConstructorDetailTable(races) {
        const selectedRows = races
            .map((race) => ({ race, results: this.findConstructorRaceResults(race, this.selectedConstructor) }))
            .filter((item) => item.results.length);

        if (!selectedRows.length) {
            this.tableContent.innerHTML = '<p class="empty-state">No hay resultados de carrera para esta escuderia en la temporada seleccionada.</p>';
            return;
        }

        this.tableContent.innerHTML = `
            <div class="rs-standings-table-wrap">
                <table class="rs-standings-table rs-standings-table--detail">
                    <thead><tr><th>Gran Premio</th><th>Fechas</th><th>Posición final carrera</th><th>Puntos totales</th></tr></thead>
                    <tbody>${selectedRows.map(({ race, results }) => `
                        <tr>
                            <td>${this.renderRaceCell(race)}</td>
                            <td>${this.formatRaceDateRange(race)}</td>
                            <td><div class="rs-standings-result-list">${results.map((result) => this.renderDriverRaceResult(result)).join('')}</div></td>
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
            const row = this.driverStandings.find((item) => item.Driver?.driverId === this.selectedDriver);
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
            const row = this.constructorStandings.find((item) => item.Constructor?.constructorId === this.selectedConstructor);
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
            this.raceResults = await this.fetchArray(`/api/f1/standings/race-results?year=${this.year}`, 4);
        }
        return this.raceResults;
    }

    findDriverRaceResult(race, driverId) {
        const selected = this.driverStandings.find((row) => row.Driver?.driverId === driverId)?.Driver || {};
        return (race.Results || []).find((result) => {
            const driver = result.Driver || {};
            return driver.driverId === driverId
                || (selected.code && driver.code === selected.code)
                || (selected.familyName && this.assets.normalize(driver.familyName) === this.assets.normalize(selected.familyName));
        });
    }

    findConstructorRaceResults(race, constructorId) {
        const selected = this.constructorStandings.find((row) => row.Constructor?.constructorId === constructorId)?.Constructor || {};
        return (race.Results || []).filter((result) => {
            const constructor = result.Constructor || {};
            return constructor.constructorId === constructorId
                || this.assets.normalize(constructor.name) === this.assets.normalize(selected.name)
                || this.assets.getTeamSlug(constructor.constructorId || constructor.name, this.year)
                    === this.assets.getTeamSlug(selected.constructorId || selected.name, this.year);
        });
    }

    getConstructorDrivers(constructorId) {
        return this.driverStandings
            .filter((row) => (row.Constructors || []).some((constructor) => constructor.constructorId === constructorId))
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

    async fetchArray(url, attempts = 3) {
        const data = await this.fetchJson(url, [], { attempts, retryEmpty: true });
        return Array.isArray(data) ? data : [];
    }

    async fetchJson(url, fallback, options = {}) {
        const cacheKey = `rs-cache:${url}`;
        const attempts = options.attempts || 3;
        for (let attempt = 0; attempt < attempts; attempt++) {
            try {
                const response = await fetch(url, { cache: 'no-store' });
                if (response.ok) {
                    const data = await response.json();
                    if (options.retryEmpty && Array.isArray(data) && !data.length && attempt < attempts - 1) {
                        await this.wait(220 * (attempt + 1));
                        continue;
                    }
                    if (!(Array.isArray(data) && !data.length)) {
                        localStorage.setItem(cacheKey, JSON.stringify(data));
                    }
                    return data;
                }
            } catch {
                await this.wait(220 * (attempt + 1));
            }
        }
        const cached = localStorage.getItem(cacheKey);
        return cached ? JSON.parse(cached) : fallback;
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
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamStandingsPage = new RaceStreamStandingsPage();
});
