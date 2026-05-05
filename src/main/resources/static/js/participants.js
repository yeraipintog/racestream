/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 04-05-2026
 * @modified 05-05-2026
 * @description Lógica compartida para páginas de Pilotos y Escuderías con tarjetas desplegables por temporada
 */
class RaceStreamParticipantsPage {

    constructor() {
        this.assets = window.RaceStreamF1Assets;
        this.mode = document.body.dataset.rsPage || 'drivers';
        this.params = new URLSearchParams(window.location.search);
        this.year = Number(this.params.get('year')) || new Date().getFullYear();
        this.pendingDriverId = this.params.get('driverId');
        this.pendingConstructorId = this.params.get('constructorId');
        this.driverStandings = [];
        this.constructorStandings = [];
        this.raceResults = [];
        this.cacheDom();
        this.bindEvents();
        this.init();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Cachea controles y contenedores principales
     */
    cacheDom() {
        this.yearInput = document.getElementById('participantsYearInput');
        this.seasonMeta = document.getElementById('participantsSeasonMeta');
        this.grid = document.getElementById('participantsGrid');
    }

    bindEvents() {
        this.yearInput.addEventListener('change', () => {
            this.year = Number(this.yearInput.value);
            window.history.replaceState({}, '', `?year=${this.year}`);
            this.loadSeason();
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
        this.grid.innerHTML = '<p class="loading-state">Cargando datos oficiales...</p>';
        const [drivers, constructors, races] = await Promise.all([
            this.fetchArray(`/api/f1/standings/drivers?year=${this.year}`, 4),
            this.fetchArray(`/api/f1/standings/constructors?year=${this.year}`, 4),
            this.fetchArray(`/api/f1/standings/race-results?year=${this.year}`, 3)
        ]);
        this.driverStandings = drivers;
        this.constructorStandings = constructors;
        this.raceResults = races;
        this.render();
    }

    render() {
        if (this.mode === 'teams') {
            this.renderTeams();
        } else {
            this.renderDrivers();
        }
        this.grid.querySelectorAll('[data-card-id]').forEach((card) => {
            card.addEventListener('click', () => this.toggleCard(card.dataset.cardId));
        });
        this.assets.hydrateDriverImages(this.grid, this.fetchJson.bind(this), this.loadImage.bind(this));
        window.RaceStreamFavorites?.bind(this.grid);
        this.expandRequestedCard();
    }

    renderDrivers() {
        this.seasonMeta.textContent = `Temporada ${this.year} · ${this.driverStandings.length} pilotos`;
        if (!this.driverStandings.length) {
            this.grid.innerHTML = '<p class="empty-state">No hay pilotos disponibles para esta temporada.</p>';
            return;
        }
        this.grid.innerHTML = this.driverStandings.map((row, index) => this.renderDriverCard(row, index)).join('');
    }

    renderDriverCard(row, index) {
        const driver = row.Driver || {};
        const constructor = (row.Constructors || [])[0] || {};
        const name = this.assets.getDriverName(driver);
        const [firstName, ...restName] = name.split(' ');
        const familyName = restName.join(' ') || firstName;
        const color = this.assets.getTeamColor(constructor, this.year);
        const teammate = this.findTeammate(row, constructor.constructorId);
        const teammateGap = teammate ? Number(row.points || 0) - Number(teammate.points || 0) : 0;
        const maxTeamPoints = Math.max(Number(row.points || 0), Number(teammate?.points || 0), 1);
        const birthDate = this.formatBirthDate(driver.dateOfBirth);
        const age = this.getAge(driver.dateOfBirth);
        const races = this.getRaceCountForDriver(driver);
        const compactClass = this.year < 2024 ? ' rs-participant-card--compact' : '';
        return `
            <article class="rs-participant-card rs-driver-card${compactClass}" style="--team-color:${color};" data-card-id="driver-${index}" data-driver-id="${this.assets.escape(driver.driverId)}">
                <div class="rs-participant-card__top">
                    <div>
                        <span class="rs-participant-card__eyebrow">${this.assets.escape(this.assets.getConstructorName(constructor))}</span>
                        <h2><span>${this.assets.escape(firstName)}</span><strong>${this.assets.escape(familyName)}</strong></h2>
                    </div>
                    <div class="rs-participant-card__actions">
                        ${this.renderFavoriteButton('Piloto', driver.driverId, name, `/drivers.html?year=${this.year}&driverId=${driver.driverId}`, `${this.assets.getConstructorName(constructor)} · ${this.assets.formatPoints(row.points)} pts`)}
                        <span class="rs-participant-card__toggle" aria-label="Posición actual de ${this.assets.escape(name)}">P${this.assets.escape(row.position || '-')}</span>
                    </div>
                </div>
                <div class="rs-driver-card__body">
                    <div class="rs-driver-card__info">
                        <span class="rs-driver-card__number">${this.assets.escape(driver.permanentNumber || driver.code || '-')}</span>
                        ${this.assets.nationalityBadge(driver.nationality)}
                    </div>
                    ${this.year >= 2024 ? `<div class="rs-driver-card__portrait">${this.assets.personAvatar(driver, 'rs-driver-card__avatar', { constructor, year: this.year, size: 440 })}</div>` : ''}
                </div>
                <div class="rs-participant-stats">
                    ${this.renderStat('Carreras', races || '-')}
                    ${this.renderStat('Victorias', row.wins || 0)}
                    ${this.renderStat('Puntos', `${this.assets.formatPoints(row.points)} pts`)}
                </div>
                <div class="rs-participant-card__details">
                    <div class="rs-participant-detail-grid">
                        ${this.renderDetail('Nacimiento', birthDate)}
                        ${this.renderDetail('Edad', age ? `${age} años` : '-')}
                        ${this.renderDetail('Escudería', this.assets.getConstructorName(constructor))}
                        ${this.renderDetail('Código', driver.code || '-')}
                        ${this.renderDetail('Compañero', teammate ? this.assets.getDriverName(teammate.Driver) : '-')}
                        ${this.renderDetail('Diferencia', teammate ? `${teammateGap >= 0 ? '+' : ''}${this.assets.formatPoints(teammateGap)} pts` : '-')}
                    </div>
                    ${teammate ? this.renderComparison(row, teammate, maxTeamPoints) : ''}
                </div>
            </article>
        `;
    }

    renderTeams() {
        this.seasonMeta.textContent = `Temporada ${this.year} · ${this.constructorStandings.length} escuderías`;
        if (!this.constructorStandings.length) {
            this.grid.innerHTML = '<p class="empty-state">No hay escuderías disponibles para esta temporada.</p>';
            return;
        }
        this.grid.innerHTML = this.constructorStandings.map((row, index) => this.renderTeamCard(row, index)).join('');
    }

    renderTeamCard(row, index) {
        const constructor = row.Constructor || {};
        const drivers = this.getTeamDrivers(constructor.constructorId);
        const color = this.assets.getTeamColor(constructor, this.year);
        const car = this.assets.getTeamCar(constructor, this.year);
        const races = this.getRaceCountForConstructor(constructor);
        const compactClass = this.year < 2024 ? ' rs-participant-card--compact' : '';
        return `
            <article class="rs-participant-card rs-team-card${compactClass}" style="--team-color:${color};" data-card-id="team-${index}" data-constructor-id="${this.assets.escape(constructor.constructorId)}">
                <div class="rs-participant-card__top">
                    <div>
                        <span class="rs-participant-card__eyebrow">${this.assets.nationalityBadge(constructor.nationality)}</span>
                        <h2><strong>${this.assets.escape(this.assets.getConstructorName(constructor))}</strong></h2>
                    </div>
                    <div class="rs-team-card__actions">
                        ${this.renderFavoriteButton('Escudería', constructor.constructorId, this.assets.getConstructorName(constructor), `/teams.html?year=${this.year}&constructorId=${constructor.constructorId}`, `${this.assets.formatPoints(row.points)} pts · ${drivers.map((driverRow) => this.assets.getDriverName(driverRow.Driver)).join(' / ')}`)}
                        ${this.assets.teamMark(constructor, this.year, 'rs-team-card__mark')}
                        <span class="rs-participant-card__toggle" aria-label="Posición actual de ${this.assets.escape(this.assets.getConstructorName(constructor))}">P${this.assets.escape(row.position || '-')}</span>
                    </div>
                </div>
                <div class="rs-team-card__drivers">${drivers.map((driverRow) => this.renderDriverChip(driverRow.Driver)).join('')}</div>
                ${this.year >= 2024 ? `<div class="rs-team-card__car">
                    <img src="${car}" alt="Coche de ${this.assets.escape(this.assets.getConstructorName(constructor))}" loading="lazy" onerror="this.remove();">
                </div>` : ''}
                <div class="rs-participant-stats">
                    ${this.renderStat('Carreras', races || '-')}
                    ${this.renderStat('Victorias', row.wins || 0)}
                    ${this.renderStat('Puntos', `${this.assets.formatPoints(row.points)} pts`)}
                </div>
                <div class="rs-participant-card__details">
                    <div class="rs-participant-detail-grid">
                        ${this.renderDetail('Pilotos', drivers.map((driverRow) => this.assets.getDriverName(driverRow.Driver)).join(' / ') || '-')}
                        ${this.renderDetail('Motor', this.assets.getEngineName(constructor))}
                    </div>
                    <div class="rs-team-card__balance">${drivers.map((driverRow) => this.renderDriverBalance(driverRow, Number(row.points || 0))).join('')}</div>
                </div>
            </article>
        `;
    }

    renderDriverChip(driver) {
        const row = this.driverStandings.find((item) => item.Driver?.driverId === driver?.driverId);
        const constructor = (row?.Constructors || [])[0] || {};
        return `<span class="rs-team-card__driver-chip">${this.assets.personAvatar(driver, 'rs-team-card__driver-avatar', { constructor, year: this.year })}<span>${this.assets.escape(this.assets.getDriverName(driver))}</span></span>`;
    }

    renderFavoriteButton(type, externalId, title, url, description) {
        return window.RaceStreamFavorites
            ? window.RaceStreamFavorites.button({ type, externalId, title, url, description })
            : '';
    }

    getRaceCountForDriver(driver) {
        return this.raceResults.filter((race) =>
            (race.Results || []).some((result) => result.Driver?.driverId === driver?.driverId
                || (driver?.code && result.Driver?.code === driver.code))).length;
    }

    getRaceCountForConstructor(constructor) {
        return this.raceResults.filter((race) =>
            (race.Results || []).some((result) => result.Constructor?.constructorId === constructor?.constructorId
                || this.assets.getTeamSlug(result.Constructor?.constructorId || result.Constructor?.name, this.year)
                    === this.assets.getTeamSlug(constructor?.constructorId || constructor?.name, this.year))).length;
    }

    renderDriverBalance(row, teamPoints) {
        const driver = row.Driver || {};
        const percent = teamPoints ? Math.min(100, Math.round((Number(row.points || 0) / teamPoints) * 100)) : 0;
        return `
            <div class="rs-participant-comparison__row">
                <span>${this.assets.escape(this.assets.getDriverName(driver))}</span>
                <strong>${this.assets.formatPoints(row.points)} pts</strong>
                <div class="rs-participant-comparison__bar"><i style="width:${percent}%"></i></div>
            </div>
        `;
    }

    renderComparison(row, teammate, maxPoints) {
        return `
            <div class="rs-participant-comparison">
                ${[row, teammate].map((item) => {
                    const percent = Math.round((Number(item.points || 0) / maxPoints) * 100);
                    return `
                        <div class="rs-participant-comparison__row">
                            <span>${this.assets.escape(this.assets.getDriverName(item.Driver))}</span>
                            <strong>${this.assets.formatPoints(item.points)} pts</strong>
                            <div class="rs-participant-comparison__bar"><i style="width:${percent}%"></i></div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    }

    renderStat(label, value) {
        return `<div><span>${label}</span><strong>${value}</strong></div>`;
    }

    renderRawStat(label, value) {
        return `<div><span>${label}</span><strong>${value}</strong></div>`;
    }

    renderDetail(label, value) {
        return `<div><span>${label}</span><strong>${this.assets.escape(value)}</strong></div>`;
    }

    renderRawDetail(label, value) {
        return `<div><span>${label}</span><strong>${value}</strong></div>`;
    }

    toggleCard(cardId) {
        let expandedCard = null;
        this.grid.querySelectorAll('.rs-participant-card').forEach((card) => {
            const expanded = card.dataset.cardId === cardId && !card.classList.contains('rs-participant-card--expanded');
            card.classList.toggle('rs-participant-card--expanded', expanded);
            if (expanded) expandedCard = card;
        });
        expandedCard?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    expandRequestedCard() {
        const target = this.mode === 'teams' && this.pendingConstructorId
            ? this.grid.querySelector(`[data-constructor-id="${CSS.escape(this.pendingConstructorId)}"]`)
            : this.pendingDriverId
                ? this.grid.querySelector(`[data-driver-id="${CSS.escape(this.pendingDriverId)}"]`)
                : null;
        if (!target) return;
        this.toggleCard(target.dataset.cardId);
        this.pendingDriverId = null;
        this.pendingConstructorId = null;
    }

    formatBirthDate(value) {
        return value ? new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
    }

    getAge(value) {
        if (!value) return 0;
        const birth = new Date(value);
        const today = new Date();
        let age = today.getFullYear() - birth.getFullYear();
        const beforeBirthday = today.getMonth() < birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate());
        return beforeBirthday ? age - 1 : age;
    }

    findTeammate(row, constructorId) {
        return this.driverStandings.find((item) =>
            item !== row && (item.Constructors || []).some((constructor) => constructor.constructorId === constructorId));
    }

    getTeamDrivers(constructorId) {
        return this.driverStandings
            .filter((row) => (row.Constructors || []).some((constructor) => constructor.constructorId === constructorId))
            .slice(0, 2);
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
    window.raceStreamParticipantsPage = new RaceStreamParticipantsPage();
});
