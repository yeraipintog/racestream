/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.6.0
 * @created 04-05-2026
 * @modified 27-05-2026
 * @description Lógica compartida para Pilotos y Escuderías con histórico autenticado, aviso histórico y tarjetas por temporada
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
        this.driverTitles = [];
        this.constructorTitles = [];
        this.raceResults = [];
        this.seasonFilterLocked = false;
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
     * @description Cachea controles y contenedores principales
     */
    cacheDom() {
        this.yearInput = document.getElementById('participantsYearInput');
        this.seasonMeta = document.getElementById('participantsSeasonMeta');
        this.grid = document.getElementById('participantsGrid');
    }

    bindEvents() {
        this.yearInput.addEventListener('change', () => {
            if (this.seasonFilterLocked) {
                this.yearInput.value = String(this.year);
                return;
            }
            this.year = Number(this.yearInput.value);
            window.history.replaceState({}, '', `?year=${this.year}`);
            this.loadSeason();
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 04-05-2026
     * @modified 27-05-2026
     * @description Inicializa acceso público, selector de temporadas y tarjetas
     * @returns {Promise<void>} Carga completada
     */
    async init() {
        await this.applySeasonAccess();
        await this.loadSeasons();
        await this.loadSeason();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Bloquea históricos para invitados y fuerza la temporada actual
     * @returns {Promise<void>} Acceso aplicado
     */
    async applySeasonAccess() {
        const access = await window.RaceStreamApi.resolveSeasonAccess(this.year);
        this.seasonFilterLocked = access.locked;
        this.year = access.year;
        this.yearInput.value = String(this.year);
        if (access.locked && this.params.get('year') && Number(this.params.get('year')) !== access.currentYear) {
            window.history.replaceState({}, '', `?year=${this.year}`);
        }
        window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 27-05-2026
     * @description Carga temporadas disponibles y conserva el bloqueo público
     * @returns {Promise<void>} Carga completada
     */
    async loadSeasons() {
        const seasons = await this.fetchJson('/api/f1/standings/seasons', []);
        const safeSeasons = Array.isArray(seasons) && seasons.length
            ? seasons
            : Array.from({ length: new Date().getFullYear() - 1949 }, (_, index) => ({ season: new Date().getFullYear() - index }));
        this.yearInput.innerHTML = safeSeasons
            .map((item) => `<option value="${item.season}" ${Number(item.season) === this.year ? 'selected' : ''}>${item.season}</option>`)
            .join('');
        window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
    }

    async loadSeason() {
        const requestId = ++this.requestId;
        this.abortController?.abort();
        this.abortController = new AbortController();
        this.driverStandings = [];
        this.constructorStandings = [];
        this.driverTitles = [];
        this.constructorTitles = [];
        this.grid.innerHTML = '<p class="loading-state">Cargando datos oficiales...</p>';
        this.yearInput.disabled = true;
        this.yearInput.setAttribute('aria-busy', 'true');
        const [drivers, constructors, driverTitles, constructorTitles] = await Promise.all([
            this.fetchArray(`/api/f1/standings/drivers?year=${this.year}`, 4, this.abortController.signal),
            this.fetchArray(`/api/f1/standings/constructors?year=${this.year}`, 4, this.abortController.signal),
            this.fetchArray('/api/f1/standings/driver-titles', 3, this.abortController.signal),
            this.fetchArray('/api/f1/standings/constructor-titles', 3, this.abortController.signal)
        ]);
        if (requestId !== this.requestId) return;
        this.driverStandings = drivers;
        this.constructorStandings = constructors;
        this.driverTitles = driverTitles;
        this.constructorTitles = constructorTitles;
        this.yearInput.disabled = this.seasonFilterLocked;
        this.yearInput.removeAttribute('aria-busy');
        window.RaceStreamApi.setSeasonFilterLocked(this.yearInput, this.seasonFilterLocked);
        this.render();
    }

    render() {
        if (this.mode === 'teams') {
            this.renderTeams();
        } else {
            this.renderDrivers();
        }
        this.grid.querySelectorAll('[data-card-id]').forEach((card) => {
            card.addEventListener('click', (event) => {
                if (event.target.closest('a, button, input, label')) return;
                this.toggleCard(card.dataset.cardId);
            });
        });
        this.assets.hydrateDriverImages(this.grid, this.fetchJson.bind(this), this.loadImage.bind(this));
        window.RaceStreamFavorites?.bind(this.grid);
        this.expandRequestedCard();
    }

    renderDrivers() {
        this.seasonMeta.textContent = `Temporada ${this.year} · ${this.driverStandings.length} pilotos`;
        if (!this.driverStandings.length) {
            this.renderApiRetry('Pilotos');
            return;
        }
        this.grid.innerHTML = this.driverStandings.map((row, index) => this.renderDriverCard(row, index)).join('');
    }

    renderDriverCard(row, index) {
        const driver = row.Driver || {};
        const constructor = (row.Constructors || [])[0] || {};
        const driverId = this.getDriverRowId(row);
        const name = this.assets.getDriverName(driver);
        const [firstName, ...restName] = name.split(' ');
        const familyName = restName.join(' ') || firstName;
        const color = this.assets.getTeamColor(constructor, this.year);
        const teammate = this.findTeammate(row, constructor.constructorId);
        const maxTeamPoints = Math.max(Number(row.points || 0), Number(teammate?.points || 0), 1);
        const birthDate = this.formatBirthDate(driver.dateOfBirth);
        const worldTitles = this.getDriverWorldTitles(row);
        const races = Number(row.race_count || 0);
        const driverNumber = this.getDriverSeasonNumber(row);
        const driverNumberTitle = driverNumber ? `Número ${driverNumber}` : 'Número no disponible en la fuente histórica';
        const compactClass = this.year < 2024 ? ' rs-participant-card--compact' : '';
        const favorite = this.renderFavoriteButton(
            'Piloto',
            driverId,
            name,
            `/drivers.html?year=${this.year}&driverId=${driverId}`,
            `${this.assets.getConstructorName(constructor)} · ${this.assets.formatPoints(row.points)} pts`
        );
        return `
            <article class="rs-participant-card rs-driver-card${compactClass}" style="--team-color:${color};" data-card-id="driver-${index}" data-driver-id="${this.assets.escape(driverId)}">
                <div class="rs-participant-card__top">
                    <div>
                        <span class="rs-participant-card__eyebrow">${this.assets.escape(this.assets.getConstructorName(constructor))}</span>
                        <h2><span>${this.assets.escape(firstName)}</span><strong>${this.assets.escape(familyName)}</strong></h2>
                    </div>
                    <div class="rs-participant-card__actions">
                        ${favorite}
                        <span class="rs-participant-card__toggle" aria-label="Posición actual de ${this.assets.escape(name)}">P${this.assets.escape(row.position || '-')}</span>
                    </div>
                </div>
                <div class="rs-driver-card__body">
                    <div class="rs-driver-card__info">
                        <span class="rs-driver-card__number" title="${this.assets.escape(driverNumberTitle)}">${driverNumber ? this.assets.escape(driverNumber) : '&mdash;'}</span>
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
                        ${this.renderDetail('Mundiales', worldTitles)}
                        ${this.renderDetail('Nacimiento', birthDate)}
                    </div>
                    ${teammate ? this.renderComparison(row, teammate, maxTeamPoints) : ''}
                </div>
            </article>
        `;
    }

    renderTeams() {
        this.seasonMeta.textContent = `Temporada ${this.year} · ${this.constructorStandings.length} escuderías`;
        const historicalNotice = this.renderHistoricalConstructorsNotice();
        if (!this.constructorStandings.length) {
            if (this.year < 1958) {
                this.grid.innerHTML = `${historicalNotice}<p class="empty-state">No hay datos históricos de escuderías para esta temporada.</p>`;
            } else {
                this.renderApiRetry('Escuderías');
            }
            return;
        }
        this.grid.innerHTML = historicalNotice + this.constructorStandings.map((row, index) => this.renderTeamCard(row, index)).join('');
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 14-05-2026
     * @modified 14-05-2026
     * @description Devuelve aviso histórico para escuderías previas al campeonato oficial de constructores
     * @returns {string} HTML del aviso
     */
    renderHistoricalConstructorsNotice() {
        if (this.year >= 1958) return '';
        return `
            <div class="rs-historical-notice">
                El Campeonato de Constructores empezó oficialmente en 1958. En temporadas anteriores estos datos son una referencia histórica y no una clasificación oficial del campeonato.
            </div>
        `;
    }

    renderTeamCard(row, index) {
        const constructor = row.Constructor || {};
        const constructorId = this.getConstructorRowId(row);
        const drivers = this.getTeamDrivers(constructorId);
        const mainDrivers = drivers.slice(0, 2);
        const color = this.assets.getTeamColor(constructor, this.year);
        const car = this.assets.getTeamCar(constructor, this.year);
        const races = Number(row.race_count || 0);
        const worldTitles = this.getConstructorWorldTitles(row);
        const compactClass = this.year < 2024 ? ' rs-participant-card--compact' : '';
        const favorite = this.renderFavoriteButton(
            'Escudería',
            constructorId,
            this.assets.getConstructorName(constructor),
            `/teams.html?year=${this.year}&constructorId=${constructorId}`,
            `${this.assets.formatPoints(row.points)} pts · Mundiales: ${worldTitles}`
        );
        return `
            <article class="rs-participant-card rs-team-card${compactClass}" style="--team-color:${color};" data-card-id="team-${index}" data-constructor-id="${this.assets.escape(constructorId)}">
                <div class="rs-participant-card__top">
                    <div>
                        <span class="rs-participant-card__eyebrow">${this.assets.nationalityBadge(constructor.nationality)}</span>
                        <h2><strong>${this.assets.escape(this.assets.getConstructorName(constructor))}</strong></h2>
                    </div>
                    <div class="rs-team-card__actions">
                        ${favorite}
                        ${this.assets.teamMark(constructor, this.year, 'rs-team-card__mark')}
                        <span class="rs-participant-card__toggle" aria-label="Posición actual de ${this.assets.escape(this.assets.getConstructorName(constructor))}">P${this.assets.escape(row.position || '-')}</span>
                    </div>
                </div>
                <div class="rs-team-card__drivers">${mainDrivers.map((driverRow) => this.renderDriverChip(driverRow.Driver)).join('')}</div>
                ${this.year >= 2024 ? `<div class="rs-team-card__car">
                    <img src="${car}" alt="Coche de ${this.assets.escape(this.assets.getConstructorName(constructor))}" loading="lazy" onerror="this.closest('.rs-team-card__car').classList.add('rs-team-card__car--missing');this.remove();">
                    <span class="rs-team-card__car-fallback">Imagen del coche no disponible</span>
                </div>` : ''}
                <div class="rs-participant-stats">
                    ${this.renderStat('Carreras', races || '-')}
                    ${this.renderStat('Victorias', row.wins || 0)}
                    ${this.renderStat('Puntos', `${this.assets.formatPoints(row.points)} pts`)}
                </div>
                <div class="rs-participant-card__details">
                    <div class="rs-participant-detail-grid">
                        ${this.renderDetail('Mundiales', worldTitles)}
                        ${this.renderDetail('Nacionalidad', constructor.nationality || '-')}
                    </div>
                    <div class="rs-team-card__balance">${drivers.map((driverRow) => this.renderDriverBalance(driverRow, Number(row.points || 0))).join('')}</div>
                </div>
            </article>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Invita a recargar cuando una respuesta obligatoria llega vacía
     * @param {string} label Datos esperados
     */
    renderApiRetry(label) {
        this.grid.innerHTML = window.RaceStreamApi.retryButton(label, 'No se han podido cargar los participantes de la temporada.');
    }

    renderDriverChip(driver) {
        const row = this.driverStandings.find((item) => item.Driver?.driverId === driver?.driverId);
        const constructor = (row?.Constructors || [])[0] || {};
        return `
            <span class="rs-team-card__driver-chip">
                ${this.assets.personAvatar(driver, 'rs-team-card__driver-avatar', { constructor, year: this.year })}
                <span>${this.assets.escape(this.assets.getDriverName(driver))}</span>
            </span>
        `;
    }

    renderFavoriteButton(type, externalId, title, url, description) {
        const normalizedDescription = `${description || ''}`.replace(/Â·/g, '·');
        const safeDescription = `${type}`.toLowerCase().includes('escuder')
            ? normalizedDescription.split('·')[0].trim()
            : normalizedDescription;
        return window.RaceStreamFavorites
            ? window.RaceStreamFavorites.button({ type, externalId, seasonYear: this.year, title, url, description: safeDescription })
            : '';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Devuelve identificador estable de piloto para tarjetas y favoritos
     * @param {Object} row Fila de piloto
     * @returns {string} Identificador estable
     */
    getDriverRowId(row) {
        const driver = row?.Driver || {};
        return driver.driverId || row?.stable_id || this.assets.normalize(this.assets.getDriverName(driver));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Devuelve identificador estable de escudería para tarjetas y favoritos
     * @param {Object} row Fila de escudería
     * @returns {string} Identificador estable
     */
    getConstructorRowId(row) {
        const constructor = row?.Constructor || {};
        return constructor.constructorId || row?.stable_id || this.assets.normalize(this.assets.getConstructorName(constructor));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @modified 11-05-2026
     * @description Obtiene el número de carrera enriquecido por el backend sin consultar resultados en el navegador
     * @param {Object} row Fila de clasificación del piloto
     * @returns {string} Número usado por el piloto en esa temporada
     */
    getDriverSeasonNumber(row) {
        const driver = row?.Driver || {};
        return row?.season_number || driver.seasonNumber || row?.fallback_number || driver.fallbackNumber || '';
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

    renderDetail(label, value) {
        return `<div><span>${label}</span><strong>${this.assets.escape(value)}</strong></div>`;
    }

    toggleCard(cardId) {
        this.grid.querySelectorAll('.rs-participant-card').forEach((card) => {
            const expanded = card.dataset.cardId === cardId && !card.classList.contains('rs-participant-card--expanded');
            card.classList.toggle('rs-participant-card--expanded', expanded);
        });
    }

    expandRequestedCard() {
        const target = this.mode === 'teams' && this.pendingConstructorId
            ? this.grid.querySelector(`[data-constructor-id="${this.escapeSelector(this.pendingConstructorId)}"]`)
            : this.pendingDriverId
                ? this.grid.querySelector(`[data-driver-id="${this.escapeSelector(this.pendingDriverId)}"]`)
                : null;
        if (!target) return;
        this.toggleCard(target.dataset.cardId);
        target.classList.add('rs-participant-card--target');
        window.setTimeout(() => target.scrollIntoView({ block: 'center', behavior: 'smooth' }), 120);
        window.setTimeout(() => target.classList.remove('rs-participant-card--target'), 2200);
        this.pendingDriverId = null;
        this.pendingConstructorId = null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 26-05-2026
     * @modified 26-05-2026
     * @description Escapa selectores CSS sin depender de navegadores antiguos que no soporten CSS.escape
     * @param {string} value Valor usado en un selector de atributo
     * @returns {string} Selector seguro
     */
    escapeSelector(value) {
        const text = `${value || ''}`;
        if (window.CSS && typeof CSS.escape === 'function') {
            return CSS.escape(text);
        }
        return text.replace(/["\\]/g, '\\$&');
    }

    formatBirthDate(value) {
        return value ? new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
    }

    getDriverWorldTitles(row) {
        if (row?.worldTitlesTotal !== undefined && row?.worldTitlesTotal !== null) {
            return Number(row.worldTitlesTotal);
        }
        const driver = row?.Driver || {};
        const keys = [
            driver.driverId,
            row?.stable_id,
            this.assets.normalize(this.assets.getDriverName(driver))
        ].filter(Boolean).map((value) => this.assets.normalize(value));
        const titleRow = this.driverTitles.find((item) => keys.includes(this.assets.normalize(item.driverId))
            || keys.includes(this.assets.normalize(item.stable_id))
            || keys.includes(this.assets.normalize(item.name)));
        if (row?.titlesLoaded === false && !titleRow) {
            return '↻';
        }
        return titleRow ? Number(titleRow.titles || 0) : '—';
    }

    getConstructorWorldTitles(row) {
        if (row?.worldTitlesTotal !== undefined && row?.worldTitlesTotal !== null) {
            return Number(row.worldTitlesTotal);
        }
        const constructor = row?.Constructor || {};
        const keys = [
            constructor.constructorId,
            row?.stable_id,
            this.assets.normalize(this.assets.getConstructorName(constructor))
        ].filter(Boolean).map((value) => this.assets.normalize(value));
        const titleRow = this.constructorTitles.find((item) => keys.includes(this.assets.normalize(item.constructorId))
            || keys.includes(this.assets.normalize(item.stable_id))
            || keys.includes(this.assets.normalize(item.name)));
        if (row?.titlesLoaded === false && !titleRow) {
            return '↻';
        }
        return titleRow ? Number(titleRow.titles || 0) : '—';
    }

    findTeammate(row, constructorId) {
        return this.driverStandings.find((item) =>
            item !== row && (item.Constructors || []).some((constructor) => constructor.constructorId === constructorId));
    }

    getTeamDrivers(constructorId) {
        const selectedRow = this.constructorStandings.find((row) => this.getConstructorRowId(row) === constructorId);
        const selected = selectedRow?.Constructor || {};
        if (Array.isArray(selectedRow?.Drivers) && selectedRow.Drivers.length) {
            return selectedRow.Drivers
                .map((driver) => this.driverStandings.find((row) => row.Driver?.driverId === driver.driverId) || { Driver: driver, points: 0 })
                .filter((row) => row.Driver);
        }
        const selectedSlug = this.assets.getTeamSlug(selected.constructorId || selected.name, this.year);
        return this.driverStandings
            .filter((row) => (row.Constructors || []).some((constructor) =>
                constructor.constructorId === constructorId
                || this.assets.getTeamSlug(constructor.constructorId || constructor.name, this.year) === selectedSlug));
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
    window.raceStreamParticipantsPage = new RaceStreamParticipantsPage();
});
