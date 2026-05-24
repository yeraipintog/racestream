/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.5.0
 * @created 03-05-2026
 * @modified 24-05-2026
 * @description Fórmula 1 En Vivo con mapa Canvas, timing limpio, telemetría
 *              suavizada y bloques de carrera optimizados
 */
class RaceStreamLivePage {
    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 23-05-2026
     * @modified 24-05-2026
     * @description Inicializa la página live concreta y sus intervalos necesarios
     */
    constructor() {
        this.page = document.body.dataset.livePage || 'home';
        this.common = new window.RaceStreamLiveCommon();
        this.driverMap = new Map();
        this.leaderboardMap = new Map();
        this.telemetryBuffers = new Map();
        this.gapMode = 'leader';
        this.selectedDriver = null;
        this.map = {
            points: new Map(),
            targets: new Map(),
            trace: [],
            bounds: null,
            raf: 0,
            lastDrawn: [],
            resizeBound: false
        };
        this.cacheDom();
        this.renderSkeletons();
        this.init();
    }

    cacheDom() {
        [
            'leaderboard', 'weatherPanel', 'raceControlPanel', 'pitPanel',
            'radioPanel', 'overtakesPanel', 'liveMapPanel', 'positionPanel',
            'telemetryPanel'
        ].forEach((id) => {
            this[id] = document.getElementById(id);
        });
    }

    init() {
        this.common.schedule('status', () => this.refreshStatus(), 5000, 5);
        if (this.page === 'home') {
            this.common.schedule('map', () => this.refreshMap(), 900, 8);
        } else if (this.page === 'timing') {
            this.common.schedule('timing', () => this.refreshTiming(), 4500, 5);
        } else if (this.page === 'race') {
            this.common.schedule('race', () => this.refreshRace(), 14000, 4);
        }
        window.addEventListener('beforeunload', () => {
            window.cancelAnimationFrame(this.map.raf);
            this.common.stop();
        });
    }

    renderSkeletons() {
        const skeleton = '<div class="rs-live-skeleton"></div>';
        [
            this.leaderboard, this.weatherPanel, this.raceControlPanel,
            this.pitPanel, this.radioPanel, this.overtakesPanel,
            this.liveMapPanel, this.positionPanel, this.telemetryPanel
        ].forEach((panel) => {
            if (panel) panel.innerHTML = skeleton;
        });
    }

    async refreshStatus() {
        const data = await this.common.fetchBlock('status', '/api/f1/live/status', null);
        if (!data || typeof data !== 'object') return;
        this.statusData = data;
        this.common.renderStatusCard(data);
        if (!this.blockData) {
            this.common.setGeneratedAt(data.generatedAt);
        }
    }

    async refreshMap() {
        const data = await this.common.fetchBlock('map', '/api/f1/live/map', null);
        if (!data || typeof data !== 'object') {
            this.renderUnavailable(this.liveMapPanel, 'No se ha podido confirmar el mapa de En Vivo.');
            return;
        }
        this.blockData = data;
        this.indexDrivers(data);
        this.pushTelemetry(data.carDataLatest);
        this.common.setGeneratedAt(data.generatedAt);
        this.common.renderStaleFlags(data);
        this.renderPositionStrip(data);
        this.renderMapCanvas(data);
        this.renderDriverTelemetry(data);
    }

    async refreshTiming() {
        const data = await this.common.fetchBlock('timing', '/api/f1/live/timing', null);
        if (!data || typeof data !== 'object') {
            this.renderUnavailable(this.leaderboard, 'No se ha podido confirmar la tabla de tiempos.');
            return;
        }
        this.blockData = data;
        this.indexDrivers(data);
        this.common.setGeneratedAt(data.generatedAt);
        this.common.renderStaleFlags(data);
        this.renderTimingTable(data);
    }

    async refreshRace() {
        const data = await this.common.fetchBlock('race', '/api/f1/live/race', null);
        if (!data || typeof data !== 'object') {
            this.renderUnavailable(this.raceControlPanel, 'No se ha podido confirmar la actividad de carrera.');
            return;
        }
        this.blockData = data;
        this.indexDrivers(data);
        this.common.setGeneratedAt(data.generatedAt);
        this.common.renderStaleFlags(data);
        this.renderRace(data);
    }

    indexDrivers(data) {
        this.driverMap = new Map(this.common.safeArray(data.drivers).map((driver) => [Number(driver.driver_number), driver]));
        this.leaderboardMap = new Map(this.common.safeArray(data.leaderboard).map((row) => [Number(row.driverNumber), row]));
    }

    renderTimingTable(data) {
        const rows = this.common.safeArray(data.leaderboard);
        if (!rows.length) {
            this.leaderboard.innerHTML = this.common.empty(this.message(data, 'leaderboard', 'Esperando clasificación o posiciones confirmadas.'));
            return;
        }
        this.leaderboard.innerHTML = `
            <table class="rs-live-table rs-live-table--timing">
                <thead>
                    <tr>
                        <th>Posición</th><th>Piloto</th><th>Equipo</th><th>Líder</th><th>Intervalo</th>
                        <th>Mejor vuelta</th><th>Última vuelta</th><th>V. actual</th>
                        <th>V. neumático</th><th>Neumático</th><th>S1</th><th>S2</th><th>S3</th>
                    </tr>
                </thead>
                <tbody>${rows.map((row) => `
                    <tr>
                        <td>${this.escape(row.position)}</td>
                        <td>${this.renderDriver(row.driverNumber, row)}</td>
                        <td>${this.escape(row.team)}</td>
                        <td>${this.escape(this.formatGap(row.gap, row.position))}</td>
                        <td>${this.escape(this.formatGap(row.interval, row.position))}</td>
                        <td>${this.escape(this.formatLapDuration(row.bestLap))}</td>
                        <td>${this.escape(this.formatLapDuration(row.lastLap))}</td>
                        <td>${this.escape(row.currentLap)}</td>
                        <td>${this.escape(row.tyreLaps)}</td>
                        <td>${this.renderTyre(row.tyre)}</td>
                        <td>${this.sector(row.s1, row.s1Status)}</td>
                        <td>${this.sector(row.s2, row.s2Status)}</td>
                        <td>${this.sector(row.s3, row.s3Status)}</td>
                    </tr>
                `).join('')}</tbody>
            </table>
            <p class="rs-live-help">Si falta un dato, RaceStream mantiene la celda vacía en lugar de inventarlo.</p>
        `;
    }

    renderPositionStrip(data) {
        if (!this.positionPanel) return;
        const rows = this.common.safeArray(data.leaderboard);
        if (!rows.length) {
            this.positionPanel.innerHTML = this.common.empty(this.message(data, 'leaderboard', 'Esperando posiciones confirmadas.'));
            return;
        }
        this.positionPanel.innerHTML = `
            <div class="rs-live-position-strip__top">
                <strong>Posiciones</strong>
                <div class="rs-live-gap-toggle" role="group" aria-label="Modo de gap">
                    <button type="button" class="${this.gapMode === 'leader' ? 'is-active' : ''}" data-gap-mode="leader">Líder</button>
                    <button type="button" class="${this.gapMode === 'interval' ? 'is-active' : ''}" data-gap-mode="interval">Intervalo</button>
                </div>
            </div>
            <div class="rs-live-position-strip__list">
                ${rows.map((row) => this.positionPill(row)).join('')}
            </div>
        `;
        this.positionPanel.querySelectorAll('[data-gap-mode]').forEach((button) => {
            button.addEventListener('click', () => {
                this.gapMode = button.dataset.gapMode;
                this.renderPositionStrip(data);
            });
        });
        this.positionPanel.querySelectorAll('[data-driver-select]').forEach((button) => {
            button.addEventListener('click', () => {
                this.selectedDriver = Number(button.dataset.driverSelect);
                this.renderPositionStrip(data);
                this.renderDriverTelemetry(data);
            });
        });
    }

    positionPill(row) {
        const number = Number(row.driverNumber);
        const driver = this.shortDriver(row.driver || this.driverName(number));
        const value = this.gapMode === 'interval'
            ? this.formatGap(row.interval, row.position)
            : this.formatGap(row.gap, row.position);
        const selected = number === this.selectedDriver ? ' is-active' : '';
        return `
            <button type="button" class="rs-live-position-pill${selected}" data-driver-select="${this.escape(number)}">
                <span class="rs-live-position-pill__pos">P${this.escape(row.position)}</span>
                <span class="rs-live-driver__badge" style="--team:${this.escape(row.teamColour || this.teamColour(this.driverMap.get(number)))}">${this.escape(number)}</span>
                <span class="rs-live-position-pill__name">${this.escape(driver)}</span>
                <span class="rs-live-position-pill__gap">${this.escape(value)}</span>
            </button>
        `;
    }

    renderRace(data) {
        if (this.weatherPanel) this.renderWeather(data);
        if (this.raceControlPanel) {
            const items = [...this.common.safeArray(data.raceControl)].reverse();
            this.raceControlPanel.innerHTML = items.length
                ? this.common.renderRows(items, (item, index) => {
                    const label = item.category || item.flag || item.scope || 'Aviso';
                    const marker = index === 0 ? '<span class="rs-live-tag">Más reciente</span>' : '';
                    const lap = item.lap_number ? ` · vuelta ${this.escape(item.lap_number)}` : '';
                    return `${marker}<strong>${this.escape(label)}${lap}:</strong> ${this.escape(item.message || item.reason || 'Sin detalle')} <time>${this.common.formatTime(item.date)}</time>`;
                })
                : this.common.empty(this.message(data, 'raceControl', 'Dirección de carrera no ha publicado mensajes para esta sesión.'));
        }
        if (this.overtakesPanel) {
            this.overtakesPanel.innerHTML = this.renderList(data, 'overtakes', (item) => {
                const lap = item.lapNumber || item.lap_number || '—';
                return `<strong>${this.driverName(item.overtaking_driver_number)}</strong> a ${this.driverName(item.overtaken_driver_number)} · vuelta ${this.escape(lap)}`;
            });
        }
        if (this.radioPanel) {
            this.radioPanel.innerHTML = this.renderList(data, 'teamRadio', (item) => {
                const url = this.common.safeUrl(item.recording_url);
                const src = url ? `/api/f1/media/team-radio?url=${encodeURIComponent(url)}` : '';
                return `
                    <strong>${this.driverName(item.driver_number)}</strong> ${this.common.formatTime(item.date)}
                    ${src ? `<audio controls preload="none" src="${src}"></audio>` : '<span>Audio no disponible</span>'}
                `;
            });
        }
        if (this.pitPanel) {
            this.pitPanel.innerHTML = this.renderList(data, 'pits', (item) => {
                const duration = item.stop_duration ?? item.lane_duration ?? item.pit_duration ?? '—';
                return `<strong>${this.driverName(item.driver_number)}</strong> vuelta ${this.escape(item.lap_number || '—')} · ${this.escape(this.formatSeconds(duration))}`;
            });
        }
    }

    renderWeather(data) {
        const weather = this.common.last(data.weather);
        if (!weather) {
            this.weatherPanel.innerHTML = this.common.empty(this.message(data, 'weather', 'Sin meteorología disponible.'));
            return;
        }
        const windMs = this.numberOrNull(weather.wind_speed);
        const windKmh = windMs === null ? '—' : `${(windMs * 3.6).toFixed(1)} km/h`;
        this.weatherPanel.innerHTML = `
            <div class="rs-live-weather-grid">
                ${this.weatherCard('Aire', `${this.value(weather.air_temperature)} ºC`, 'Temperatura ambiente.')}
                ${this.weatherCard('Pista', `${this.value(weather.track_temperature)} ºC`, 'Cuanto más caliente, más sufren los neumáticos.')}
                ${this.weatherCard('Humedad', `${this.value(weather.humidity)}%`, 'Afecta a la refrigeración y al agarre.')}
                ${this.weatherCard('Viento', `${windMs === null ? '—' : `${windMs.toFixed(1)} m/s`} · ${windKmh}`, 'Puede cambiar frenadas y estabilidad.')}
                ${this.weatherCard('Presión', `${this.value(weather.pressure)} mbar`, 'Presión atmosférica registrada por OpenF1.')}
                ${this.weatherCard('Lluvia', weather.rainfall ? 'Sí' : 'No', 'Si llueve, cambian neumáticos y referencias de frenada.')}
            </div>
        `;
    }

    weatherCard(label, value, help) {
        return `<div class="rs-live-weather-card"><span>${this.escape(label)}</span><strong>${this.escape(value)}</strong><small>${this.escape(help)}</small></div>`;
    }

    renderMapCanvas(data) {
        const panel = this.liveMapPanel;
        if (!panel) return;
        const locations = this.normalizedLocations(data.locationLatest);
        const trace = this.normalizedLocations(data.locationTrace);
        const carData = this.common.safeArray(data.carDataLatest);
        if (locations.length < 2) {
            panel.innerHTML = carData.length
                ? `<div class="rs-live-map rs-live-map--empty">${this.telemetryCards(carData)}</div><p class="rs-live-map__note">Hay telemetría del coche, pero OpenF1 todavía no ha publicado ubicación suficiente para dibujar el mapa.</p>`
                : this.common.empty(this.message(data, 'locationLatest', 'Esperando telemetría del mapa.'));
            return;
        }

        this.map.trace = trace.length >= 12 ? trace : [];
        this.map.bounds = this.locationBounds(this.map.trace.length ? this.map.trace : locations);
        if (!this.map.bounds) {
            panel.innerHTML = this.common.empty('Esperando telemetría del mapa.');
            return;
        }
        this.map.targets = new Map(locations.map((location) => {
            const projected = this.projectLocation(location, this.map.bounds);
            return [Number(location.driver_number), projected];
        }));
        if (!this.selectedDriver || !this.map.targets.has(this.selectedDriver)) {
            this.selectedDriver = this.common.safeArray(data.leaderboard)[0]?.driverNumber || locations[0].driver_number;
        }
        if (!panel.querySelector('canvas')) {
            panel.innerHTML = `
                <div class="rs-live-map">
                    <canvas class="rs-live-map__canvas" aria-label="Mapa en directo"></canvas>
                </div>
                <p class="rs-live-map__note">El trazado se reconstruye con ubicaciones recientes de OpenF1. La posición es orientativa y se suaviza visualmente entre actualizaciones.</p>
            `;
            const canvas = panel.querySelector('canvas');
            canvas.addEventListener('click', (event) => this.selectDriverFromCanvas(event, data));
            if (!this.map.resizeBound) {
                window.addEventListener('resize', () => this.resizeCanvas(canvas));
                this.map.resizeBound = true;
            }
            this.startMapLoop(canvas);
        }
    }

    startMapLoop(canvas) {
        const draw = () => {
            this.resizeCanvas(canvas);
            this.drawMap(canvas);
            this.map.raf = window.requestAnimationFrame(draw);
        };
        window.cancelAnimationFrame(this.map.raf);
        draw();
    }

    resizeCanvas(canvas) {
        const rect = canvas.getBoundingClientRect();
        const ratio = window.devicePixelRatio || 1;
        const width = Math.max(1, Math.round(rect.width * ratio));
        const height = Math.max(1, Math.round(rect.height * ratio));
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }
    }

    drawMap(canvas) {
        const context = canvas.getContext('2d');
        if (!context || !this.map.bounds) return;
        const ratio = window.devicePixelRatio || 1;
        const width = canvas.width;
        const height = canvas.height;
        context.clearRect(0, 0, width, height);
        context.save();
        context.scale(ratio, ratio);
        const cssWidth = width / ratio;
        const cssHeight = height / ratio;
        this.drawTrack(context, cssWidth, cssHeight);
        this.map.lastDrawn = [];
        this.map.targets.forEach((target, driverNumber) => {
            const current = this.map.points.get(driverNumber) || target;
            const next = {
                driver_number: driverNumber,
                x: current.x + (target.x - current.x) * 0.14,
                y: current.y + (target.y - current.y) * 0.14
            };
            this.map.points.set(driverNumber, next);
            const row = this.leaderboardMap.get(driverNumber) || {};
            const colour = row.teamColour || this.teamColour(this.driverMap.get(driverNumber));
            const px = next.x * cssWidth;
            const py = next.y * cssHeight;
            this.map.lastDrawn.push({ driverNumber, x: px, y: py });
            context.beginPath();
            context.arc(px, py, driverNumber === this.selectedDriver ? 15 : 12, 0, Math.PI * 2);
            context.fillStyle = 'rgba(12, 18, 30, .96)';
            context.fill();
            context.lineWidth = driverNumber === this.selectedDriver ? 4 : 3;
            context.strokeStyle = colour;
            context.stroke();
            context.fillStyle = '#fff';
            context.font = '800 10px Arial, sans-serif';
            context.textAlign = 'center';
            context.textBaseline = 'middle';
            context.fillText(String(driverNumber), px, py);
        });
        context.restore();
    }

    drawTrack(context, width, height) {
        context.lineWidth = 5;
        context.lineCap = 'round';
        context.lineJoin = 'round';
        context.strokeStyle = 'rgba(255, 255, 255, .26)';
        const groups = new Map();
        this.map.trace.forEach((item) => {
            const key = Number(item.driver_number);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(this.projectLocation(item, this.map.bounds));
        });
        if (!groups.size) {
            this.drawFallbackTrack(context, width, height);
            return;
        }
        groups.forEach((items) => {
            if (items.length < 4) return;
            items.sort((left, right) => new Date(left.date).getTime() - new Date(right.date).getTime());
            context.beginPath();
            items.forEach((point, index) => {
                const x = point.x * width;
                const y = point.y * height;
                if (index === 0) context.moveTo(x, y);
                else context.lineTo(x, y);
            });
            context.stroke();
        });
    }

    drawFallbackTrack(context, width, height) {
        const points = [
            [.12, .58], [.20, .40], [.36, .50], [.50, .62], [.67, .52],
            [.58, .34], [.42, .20], [.67, .17], [.88, .23], [.78, .45],
            [.55, .39], [.28, .43], [.12, .58]
        ];
        context.beginPath();
        points.forEach(([x, y], index) => {
            if (index === 0) context.moveTo(x * width, y * height);
            else context.lineTo(x * width, y * height);
        });
        context.stroke();
    }

    selectDriverFromCanvas(event, data) {
        const rect = event.currentTarget.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;
        const hit = this.map.lastDrawn
            .map((point) => ({ ...point, distance: Math.hypot(point.x - x, point.y - y) }))
            .sort((left, right) => left.distance - right.distance)[0];
        if (hit && hit.distance <= 28) {
            this.selectedDriver = hit.driverNumber;
            this.renderPositionStrip(data);
            this.renderDriverTelemetry(data);
        }
    }

    renderDriverTelemetry(data) {
        if (!this.telemetryPanel) return;
        const rows = this.common.safeArray(data.leaderboard);
        const selected = rows.find((row) => Number(row.driverNumber) === Number(this.selectedDriver)) || rows[0] || {};
        const driverNumber = Number(selected.driverNumber);
        const car = this.common.safeArray(data.carDataLatest).find((item) => Number(item.driver_number) === driverNumber) || {};
        const speed = this.numberOrNull(car.speed);
        const rpm = this.numberOrNull(car.rpm);
        this.telemetryPanel.innerHTML = `
            <article class="rs-live-telemetry-card">
                <div class="rs-live-telemetry-card__head">
                    <div>${this.renderDriver(driverNumber, selected)}</div>
                    <span>${this.escape(selected.team || 'Equipo no disponible')}</span>
                </div>
                <div class="rs-live-telemetry-grid">
                    ${this.gauge('Velocidad', speed, 360, 'km/h')}
                    ${this.gauge('RPM', rpm, 15000, 'rpm')}
                    <div class="rs-live-gear"><span>Marcha</span><strong>${this.escape(car.n_gear ?? '—')}</strong></div>
                    <div class="rs-live-telemetry-meta">
                        <span>Neumático</span>${this.renderTyre(selected.tyre)}
                        <span>Vuelta</span><strong>${this.escape(selected.currentLap || '—')}</strong>
                        <span>Gap</span><strong>${this.escape(this.formatGap(selected.gap, selected.position))}</strong>
                        <span>Intervalo</span><strong>${this.escape(this.formatGap(selected.interval, selected.position))}</strong>
                    </div>
                    <div class="rs-live-telemetry-sectors">
                        ${this.sector(selected.s1, selected.s1Status)}
                        ${this.sector(selected.s2, selected.s2Status)}
                        ${this.sector(selected.s3, selected.s3Status)}
                    </div>
                    <canvas class="rs-live-telemetry-chart" aria-label="Gráfico de acelerador, freno y DRS"></canvas>
                </div>
            </article>
        `;
        this.drawTelemetryChart(this.telemetryPanel.querySelector('canvas'), driverNumber);
    }

    gauge(label, value, max, unit) {
        const percent = value === null ? 0 : Math.max(0, Math.min(100, (value / max) * 100));
        return `
            <div class="rs-live-gauge" style="--value:${percent}%">
                <span>${this.escape(label)}</span>
                <strong>${value === null ? '—' : this.escape(Math.round(value))}</strong>
                <small>${this.escape(unit)}</small>
            </div>
        `;
    }

    drawTelemetryChart(canvas, driverNumber) {
        if (!canvas) return;
        const buffer = this.telemetryBuffers.get(Number(driverNumber)) || [];
        const rect = canvas.getBoundingClientRect();
        const ratio = window.devicePixelRatio || 1;
        canvas.width = Math.max(1, Math.round(rect.width * ratio));
        canvas.height = Math.max(1, Math.round(rect.height * ratio));
        const context = canvas.getContext('2d');
        if (!context) return;
        context.scale(ratio, ratio);
        const width = canvas.width / ratio;
        const height = canvas.height / ratio;
        context.clearRect(0, 0, width, height);
        context.strokeStyle = 'rgba(255,255,255,.12)';
        context.lineWidth = 1;
        for (let i = 1; i < 4; i++) {
            context.beginPath();
            context.moveTo(0, (height / 4) * i);
            context.lineTo(width, (height / 4) * i);
            context.stroke();
        }
        if (buffer.length < 2) return;
        context.beginPath();
        buffer.forEach((item, index) => {
            const x = (index / Math.max(1, buffer.length - 1)) * width;
            const y = height - (Number(item.throttle || 0) / 100) * height;
            if (index === 0) context.moveTo(x, y);
            else context.lineTo(x, y);
        });
        context.strokeStyle = '#22c55e';
        context.lineWidth = 2;
        context.stroke();
        buffer.forEach((item, index) => {
            const x = (index / Math.max(1, buffer.length - 1)) * width;
            if (Number(item.brake || 0) > 0) {
                context.fillStyle = 'rgba(239, 68, 68, .55)';
                context.fillRect(x, height - 14, 4, 14);
            }
            if (this.drsState(item.drs).active) {
                context.fillStyle = 'rgba(56, 189, 248, .55)';
                context.fillRect(x, 0, 4, 14);
            }
        });
    }

    pushTelemetry(items) {
        this.common.safeArray(items).forEach((item) => {
            const number = Number(item.driver_number);
            if (!number) return;
            const buffer = this.telemetryBuffers.get(number) || [];
            if (!buffer.length || buffer[buffer.length - 1].date !== item.date) {
                buffer.push(item);
            }
            this.telemetryBuffers.set(number, buffer.slice(-40));
        });
    }

    telemetryCards(items) {
        return `<div class="rs-live-mini-grid">${items.slice(0, 8).map((item) => `
            <div class="rs-live-mini-card">
                <strong>${this.driverName(item.driver_number)}</strong>
                <span>${this.escape(item.speed ?? '—')} km/h · RPM ${this.escape(item.rpm ?? '—')}</span>
                <span>Marcha ${this.escape(item.n_gear ?? '—')} · DRS ${this.escape(this.drsState(item.drs).label)}</span>
            </div>
        `).join('')}</div>`;
    }

    normalizedLocations(value) {
        return this.common.safeArray(value)
            .map((item) => ({
                ...item,
                x: Number(item.x),
                y: Number(item.y),
                driver_number: Number(item.driver_number)
            }))
            .filter((item) => Number.isFinite(item.x) && Number.isFinite(item.y) && item.driver_number > 0);
    }

    locationBounds(locations) {
        const xs = locations.map((item) => item.x);
        const ys = locations.map((item) => item.y);
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);
        if (maxX === minX || maxY === minY) return null;
        return { minX, maxX, minY, maxY };
    }

    projectLocation(location, bounds) {
        return {
            driver_number: location.driver_number,
            date: location.date,
            x: .05 + ((location.x - bounds.minX) / (bounds.maxX - bounds.minX)) * .90,
            y: .05 + ((bounds.maxY - location.y) / (bounds.maxY - bounds.minY)) * .90
        };
    }

    renderList(data, field, mapper) {
        const items = this.common.safeArray(data[field]);
        return items.length
            ? this.common.renderRows(items, mapper)
            : this.common.empty(this.message(data, field, 'Sin datos disponibles.'));
    }

    renderDriver(number, row = null) {
        const driver = this.driverMap.get(Number(number));
        const data = row || this.leaderboardMap.get(Number(number)) || {};
        const colour = data.teamColour || this.teamColour(driver);
        return `<span class="rs-live-driver"><span class="rs-live-driver__badge" style="--team:${this.escape(colour)}">${this.escape(number || '—')}</span>${this.escape(data.driver || driver?.full_name || `Piloto ${number || '—'}`)}</span>`;
    }

    driverName(number) {
        const driver = this.driverMap.get(Number(number));
        return this.escape(driver?.name_acronym || driver?.last_name || driver?.full_name || `#${number || '—'}`);
    }

    shortDriver(name) {
        const clean = `${name || ''}`.trim();
        if (!clean) return 'Piloto';
        const parts = clean.split(/\s+/);
        return parts.length > 1 ? `${parts[0][0]}. ${parts[parts.length - 1]}` : clean;
    }

    teamColour(driver) {
        const colour = `${driver?.team_colour || driver?.team_color || ''}`.replace('#', '');
        return /^[0-9a-f]{6}$/i.test(colour) ? `#${colour}` : '#e5e7eb';
    }

    renderTyre(value) {
        const raw = `${value || '—'}`.toUpperCase();
        const key = raw.includes('SOFT') ? 'soft'
            : raw.includes('MEDIUM') ? 'medium'
                : raw.includes('HARD') ? 'hard'
                    : raw.includes('INTER') ? 'intermediate'
                        : raw.includes('WET') ? 'wet'
                            : 'unknown';
        const config = {
            soft: ['S', 'Blando'],
            medium: ['M', 'Medio'],
            hard: ['H', 'Duro'],
            intermediate: ['I', 'Intermedio'],
            wet: ['W', 'Lluvia'],
            unknown: ['—', '—']
        }[key];
        return `<span class="rs-live-tyre rs-live-tyre--${key}"><span class="rs-live-tyre__icon" aria-hidden="true">${this.escape(config[0])}</span>${this.escape(config[1])}</span>`;
    }

    sector(value, status) {
        return `<span class="rs-live-sector rs-live-sector--${this.escape(status || 'neutral')}">${this.escape(this.formatSector(value))}</span>`;
    }

    formatLapDuration(value) {
        const seconds = this.numberOrNull(value);
        if (seconds === null || seconds <= 0) return '—';
        const minutes = Math.floor(seconds / 60);
        const rest = (seconds - minutes * 60).toFixed(3).padStart(6, '0');
        return `${minutes}:${rest}`;
    }

    formatSector(value) {
        const seconds = this.numberOrNull(value);
        return seconds === null || seconds <= 0 ? '—' : seconds.toFixed(3);
    }

    formatGap(value, position) {
        const text = `${value ?? ''}`.trim();
        const isLeader = Number(position) === 1;
        const normalized = text.replace('+', '');
        const numeric = this.numberOrNull(normalized);
        if (isLeader && (!text || numeric === 0 || text.toLowerCase() === 'null')) {
            return 'Líder';
        }
        return text && text.toLowerCase() !== 'null' ? text : '—';
    }

    formatSeconds(value) {
        const seconds = this.numberOrNull(value);
        return seconds === null ? '—' : `${seconds.toFixed(2)} s`;
    }

    numberOrNull(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    value(value) {
        return value === null || value === undefined || value === '' ? '—' : value;
    }

    drsState(value) {
        const drs = Number(value);
        if ([10, 12, 14].includes(drs)) return { active: true, label: 'Activo' };
        if (drs === 8) return { active: false, label: 'Disponible' };
        return { active: false, label: 'Apagado' };
    }

    message(data, field, fallback) {
        return data?.messages?.[field] || fallback;
    }

    renderUnavailable(panel, message) {
        if (panel) panel.innerHTML = this.common.empty(message);
    }

    escape(value) {
        return this.common.escape(value);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamLivePage = new RaceStreamLivePage();
});
