/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.4
 * @created 04-05-2026
 * @modified 27-05-2026
 * @description Útilidades visuales de Fórmula 1 para pilotos, escuderías, nacionalidades, banderas e imágenes
 */
class RaceStreamF1Assets {

    static teamColors = {
        mercedes: '#00a19c',
        ferrari: '#e8002d',
        mclaren: '#ff8000',
        redbullracing: '#1e5bc6',
        williams: '#00a3e0',
        alpine: '#0090ff',
        astonmartin: '#006f62',
        haas: '#b6babd',
        kicksauber: '#52e252',
        audi: '#c7d2fe',
        rb: '#315c88',
        racingbulls: '#315c88',
        alphatauri: '#5e8faa',
        alfaromeo: '#900000',
        cadillac: '#a67c52'
    };

    static nationalityCodes = {
        Argentine: 'ARG',
        Australian: 'AUS',
        Austrian: 'AUT',
        Belgian: 'BEL',
        Brazilian: 'BRA',
        British: 'GBR',
        Canadian: 'CAN',
        Chinese: 'CHN',
        Colombian: 'COL',
        Danish: 'DEN',
        Dutch: 'NED',
        Finnish: 'FIN',
        French: 'FRA',
        German: 'GER',
        Indian: 'IND',
        Irish: 'IRL',
        Italian: 'ITA',
        Japanese: 'JPN',
        Malaysian: 'MYS',
        Mexican: 'MEX',
        Monegasque: 'MON',
        'New Zealander': 'NZL',
        Polish: 'POL',
        Portuguese: 'POR',
        Russian: 'RUS',
        Spanish: 'ESP',
        Swedish: 'SWE',
        Swiss: 'SUI',
        Thai: 'THA',
        Venezuelan: 'VEN',
        American: 'USA'
    };

    static countryCodes = {
        Argentina: 'ARG',
        Australia: 'AUS',
        Austria: 'AUT',
        Azerbaijan: 'AZE',
        Bahrain: 'BHR',
        Belgium: 'BEL',
        Brazil: 'BRA',
        Canada: 'CAN',
        China: 'CHN',
        France: 'FRA',
        Germany: 'GER',
        Hungary: 'HUN',
        India: 'IND',
        Italy: 'ITA',
        Japan: 'JPN',
        Malaysia: 'MYS',
        Mexico: 'MEX',
        Monaco: 'MON',
        Netherlands: 'NED',
        Portugal: 'POR',
        Qatar: 'QAT',
        Russia: 'RUS',
        Singapore: 'SGP',
        Spain: 'ESP',
        Sweden: 'SWE',
        Switzerland: 'SUI',
        Turkey: 'TUR',
        UAE: 'ARE',
        UK: 'GBR',
        USA: 'USA',
        'Saudi Arabia': 'SAU',
        'United Arab Emirates': 'ARE',
        'United States': 'USA',
        'United States of America': 'USA'
    };

    static nationalityIso2 = {
        Argentine: 'ar',
        Australian: 'au',
        Austrian: 'at',
        Belgian: 'be',
        Brazilian: 'br',
        British: 'gb',
        Canadian: 'ca',
        Chinese: 'cn',
        Colombian: 'co',
        Danish: 'dk',
        Dutch: 'nl',
        Finnish: 'fi',
        French: 'fr',
        German: 'de',
        Indian: 'in',
        Irish: 'ie',
        Italian: 'it',
        Japanese: 'jp',
        Malaysian: 'my',
        Mexican: 'mx',
        Monegasque: 'mc',
        'New Zealander': 'nz',
        Polish: 'pl',
        Portuguese: 'pt',
        Russian: 'ru',
        Spanish: 'es',
        Swedish: 'se',
        Swiss: 'ch',
        Thai: 'th',
        Venezuelan: 've',
        American: 'us'
    };

    static countryIso2 = {
        Argentina: 'ar',
        Australia: 'au',
        Austria: 'at',
        Azerbaijan: 'az',
        Bahrain: 'bh',
        Belgium: 'be',
        Brazil: 'br',
        Canada: 'ca',
        China: 'cn',
        France: 'fr',
        Germany: 'de',
        Hungary: 'hu',
        India: 'in',
        Italy: 'it',
        Japan: 'jp',
        Malaysia: 'my',
        Mexico: 'mx',
        Monaco: 'mc',
        Netherlands: 'nl',
        Portugal: 'pt',
        Qatar: 'qa',
        Russia: 'ru',
        Singapore: 'sg',
        Spain: 'es',
        Sweden: 'se',
        Switzerland: 'ch',
        Turkey: 'tr',
        UAE: 'ae',
        UK: 'gb',
        USA: 'us',
        'Saudi Arabia': 'sa',
        'United Arab Emirates': 'ae',
        'United States': 'us',
        'United States of America': 'us'
    };

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Escapa texto antes de insertarlo en HTML
     * @param {*} value Valor original
     * @returns {string} Texto seguro
     */
    static escape(value) {
        return `${value ?? ''}`
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    static normalize(value) {
        return `${value || ''}`.toLowerCase()
            .normalize('NFD')
            .replace(/\p{M}/gu, '')
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '');
    }

    static getDriverName(driver) {
        const name = [
            driver?.givenName || driver?.first_name,
            driver?.familyName || driver?.last_name
        ].filter(Boolean).join(' ') || driver?.full_name || driver?.driver_name || driver?.name || driver?.code || driver?.name_acronym || 'Piloto';
        return name.replace(/^Andrea\s+Kimi\s+Antonelli$/i, 'Kimi Antonelli');
    }

    static getDriverNumber(driver) {
        const number = driver?.seasonNumber || driver?.season_number || driver?.permanentNumber || driver?.permanent_number || driver?.driver_number || driver?.number || '';
        return `${number}` === '\\N' ? '' : number;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Devuelve el código deportivo visible cuando no hay foto fiable del piloto
     * @param {Object} driver Piloto
     * @returns {string} Código del piloto
     */
    static getDriverCode(driver) {
        return driver?.code || driver?.name_acronym || driver?.driver_code || this.getDriverInitials(driver);
    }

    static getConstructorName(constructor) {
        return constructor?.name || 'Escudería';
    }

    static getConstructorId(constructor) {
        return constructor?.constructorId || this.normalize(constructor?.name);
    }

    static getTeamSlug(value, year = new Date().getFullYear()) {
        const key = this.normalize(value);
        if (key.includes('red-bull')) return 'redbullracing';
        if (key.includes('alpha-tauri') || key.includes('alphatauri')) return 'alphatauri';
        if (key.includes('racing-bulls') || key.includes('visa-cash-app') || key.includes('rb-f1') || key === 'rb') {
            if (year <= 2023) return 'alphatauri';
            return year === 2024 ? 'rb' : 'racingbulls';
        }
        if (key.includes('aston-martin')) return 'astonmartin';
        if (key.includes('alfa-romeo')) return 'alfaromeo';
        if (key.includes('audi')) return 'audi';
        if (key.includes('kick') || key.includes('stake') || key.includes('sauber')) {
            if (year >= 2026) return 'audi';
            return year <= 2023 ? 'alfaromeo' : 'kicksauber';
        }
        if (key.includes('mercedes')) return 'mercedes';
        if (key.includes('ferrari')) return 'ferrari';
        if (key.includes('mclaren')) return 'mclaren';
        if (key.includes('williams')) return 'williams';
        if (key.includes('alpine')) return 'alpine';
        if (key.includes('haas')) return 'haas';
        if (key.includes('cadillac')) return 'cadillac';
        return key;
    }

    static getTeamColor(constructor, year = new Date().getFullYear()) {
        const slug = this.getTeamSlug(constructor?.constructorId || constructor?.name, year);
        return this.teamColors[slug] || '#64748b';
    }

    static getTeamLogo(constructor, year = new Date().getFullYear()) {
        const slug = this.getTeamSlug(constructor?.constructorId || constructor?.name, year);
        const file = slug.replace(/-/g, '');
        return slug ? `https://media.formula1.com/image/upload/c_lfill,w_96/q_auto/v1740000001/common/f1/${year}/${slug}/${year}${file}logowhite.webp` : '';
    }

    static getTeamCar(constructor, year = new Date().getFullYear()) {
        const slug = this.getTeamSlug(constructor?.constructorId || constructor?.name, year);
        const file = slug.replace(/-/g, '');
        return slug ? `https://media.formula1.com/image/upload/c_lfill,w_720/q_auto/v1740000001/common/f1/${year}/${slug}/${year}${file}carright.webp` : '';
    }

    static getTeamInitials(constructor) {
        const name = this.getConstructorName(constructor);
        return name.split(/\s+/).map((part) => part[0]).join('').slice(0, 3).toUpperCase();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Devuelve iniciales legibles como último fallback de pilotos sin código
     * @param {Object} driver Piloto
     * @returns {string} Iniciales del piloto
     */
    static getDriverInitials(driver) {
        const name = this.getDriverName(driver);
        return name.split(/\s+/).map((part) => part[0]).join('').slice(0, 3).toUpperCase();
    }

    static getNationalityCode(value) {
        return this.nationalityCodes[value] || `${value || '---'}`.slice(0, 3).toUpperCase();
    }

    static getCountryCode(value) {
        return this.countryCodes[value] || `${value || 'GP'}`.slice(0, 3).toUpperCase();
    }

    static getNationalityIso2(value) {
        return this.nationalityIso2[value] || '';
    }

    static getCountryIso2(value) {
        return this.countryIso2[value] || '';
    }

    static flagUrl(iso2) {
        return iso2 ? `https://flagcdn.com/w40/${iso2}.png` : '';
    }

    static nationalityBadge(value, className = '') {
        const code = this.getNationalityCode(value);
        const flag = this.flagUrl(this.getNationalityIso2(value));
        return `
            <span class="rs-country-badge ${className}" title="${this.escape(value || code)}">
                ${flag ? `<img src="${flag}" alt="Bandera de ${this.escape(value || code)}" loading="lazy">` : ''}
                <span>${this.escape(code)}</span>
            </span>
        `;
    }

    static countryFlag(value, className = '') {
        const flag = this.flagUrl(this.getCountryIso2(value));
        return flag ? `<img class="rs-flag-inline ${className}" src="${flag}" alt="Bandera de ${this.escape(value || 'país')}" loading="lazy">` : '';
    }

    static formatPoints(value) {
        const points = Number(value);
        return Number.isFinite(points) ? points.toLocaleString('es-ES', { maximumFractionDigits: 2 }) : value || 0;
    }

    static personAvatar(driver, className = '', options = {}) {
        const number = this.getDriverNumber(driver);
        const name = this.getDriverName(driver);
        const year = Number(options.year || new Date().getFullYear());
        if (!this.hasTrustedSeasonDriverImage(year)) {
            return `
                <span class="rs-person-avatar rs-person-avatar--code ${className}" title="${this.escape(name)}">
                    <span>${this.escape(this.getDriverCode(driver))}</span>
                </span>
            `;
        }
        const image = this.getDriverImage(driver, options.constructor, options.year, options.size || 64);
        const mediaUrl = number && !image ? `/api/f1/media/driver?number=${number}` : '';
        const fallbackClass = image ? '' : ' rs-person-avatar--fallback';
        return `
            <span class="rs-person-avatar${fallbackClass} ${className}">
                <img
                    ${image ? `src="${image}"` : ''}
                    ${mediaUrl ? `data-media-url="${mediaUrl}"` : ''}
                    alt="Foto de ${this.escape(name)}"
                    loading="lazy"
                    onerror="this.onerror=null;this.closest('.rs-person-avatar').classList.add('rs-person-avatar--fallback');this.removeAttribute('src');">
            </span>
        `;
    }

    static getDriverImage(driver, constructor, year = new Date().getFullYear(), width = 64) {
        const assetId = this.getDriverAssetId(driver);
        const team = this.getTeamSlug(constructor?.constructorId || constructor?.name || constructor, year);
        if (!assetId || !team) return '';
        const assetYear = assetId === 'jacdoo01' && year === 2024 ? 2025 : year;
        const assetTeam = this.getDriverAssetTeam(assetId, team, year);
        return `https://media.formula1.com/image/upload/c_lfill,w_${width}/q_auto`
            + `/d_common:f1:${assetYear}:fallback:driver:${assetYear}fallbackdriverright.webp`
            + `/v1740000001/common/f1/${assetYear}/${assetTeam}/${assetId}/${assetYear}${assetTeam}${assetId}right.webp`;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 11-05-2026
     * @modified 27-05-2026
     * @description Corrige equipos de assets oficiales cuando el piloto cambió de escudería esa temporada
     * @param {string} assetId Identificador oficial de piloto
     * @param {string} team Equipo calculado
     * @param {number} year Temporada
     * @returns {string} Equipo para la URL de imagen
     */
    static getDriverAssetTeam(assetId, team, year) {
        if (assetId === 'jacdoo01' && year === 2024) return 'alpine';
        if (assetId === 'lialaw01' && year === 2025) return 'redbullracing';
        return team;
    }

    static hasTrustedSeasonDriverImage(year) {
        const season = Number(year || new Date().getFullYear());
        return season >= 2024 && season <= 2026;
    }

    static getDriverAssetId(driver) {
        const fullName = this.getDriverName(driver);
        const nameParts = fullName.split(/\s+/).filter(Boolean);
        const rawGiven = `${driver?.givenName || driver?.first_name || nameParts[0] || ''}`.replace(/^Andrea\s+Kimi$/i, 'Kimi');
        const given = rawGiven.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/[^a-z\s-]/g, '').trim().split(/\s+/)[0] || '';
        const family = `${driver?.familyName || driver?.last_name || nameParts.slice(1).join(' ')}`.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/[^a-z\s-]/g, '').trim();
        if (!given || !family) return '';
        if (family === 'antonelli' && /kimi|andrea/i.test(rawGiven)) return 'andant01';
        const parts = family.split(/\s+/).filter(Boolean);
        const particles = new Set(['da', 'de', 'del', 'do', 'dos', 'du', 'van', 'von']);
        const familyCode = parts.length > 1 && particles.has(parts[parts.length - 2]) ? `${parts[parts.length - 2]}${parts[parts.length - 1]}` : parts[parts.length - 1];
        return `${given.slice(0, 3)}${familyCode.slice(0, 3)}01`;
    }

    static teamMark(constructor, year, className = '') {
        const name = this.getConstructorName(constructor);
        if (Number(year || new Date().getFullYear()) < 2024) {
            return `
                <span class="rs-team-mark rs-team-mark--fallback ${className}" title="${this.escape(name)}">
                    <span>${this.escape(this.getTeamInitials(constructor))}</span>
                </span>
            `;
        }
        const logo = this.getTeamLogo(constructor, year);
        return `
            <span class="rs-team-mark ${className}">
                <img src="${logo}" alt="Logo de ${this.escape(name)}" loading="lazy" onerror="this.onerror=null;this.closest('.rs-team-mark').classList.add('rs-team-mark--fallback');this.remove();">
                <span>${this.escape(this.getTeamInitials(constructor))}</span>
            </span>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Hidrata fotos reales de pilotos desde el endpoint interno sin bloquear la tabla
     * @param {HTMLElement} root Contenedor
     * @param {Function} fetchJson Funcion de carga JSON
     * @param {Function} loadImage Funcion de precarga de imagen
     */
    static hydrateDriverImages(root, fetchJson, loadImage) {
        root.querySelectorAll('img[data-media-url^="/api/f1/media/driver"]').forEach(async (image) => {
            const wrapper = image.closest('.rs-person-avatar');
            const fallbackSrc = image.getAttribute('src');
            if (fallbackSrc) {
                wrapper?.classList.remove('rs-person-avatar--fallback');
            }
            const media = await fetchJson(image.dataset.mediaUrl, {});
            if (!media?.headshotUrl) {
                if (!image.getAttribute('src')) wrapper?.classList.add('rs-person-avatar--fallback');
                return;
            }
            if (image.src === media.headshotUrl) return;
            loadImage(media.headshotUrl)
                .then(() => {
                    image.src = media.headshotUrl;
                    wrapper?.classList.remove('rs-person-avatar--fallback');
                })
                .catch(() => {
                    if (fallbackSrc) {
                        image.src = fallbackSrc;
                        wrapper?.classList.remove('rs-person-avatar--fallback');
                    } else {
                        image.removeAttribute('src');
                        wrapper?.classList.add('rs-person-avatar--fallback');
                    }
                });
        });
    }
}

window.RaceStreamF1Assets = RaceStreamF1Assets;
