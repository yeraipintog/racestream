/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.8
 * @created 30-04-2026
 * @modified 11-05-2026
 * @description Carga y renderiza noticias completas de Fórmula 1 desde el backend RaceStream con filtro estricto
 */
class RaceStreamNewsPage {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 30-04-2026
     * @modified 11-05-2026
     * @description Constructor de Noticias con franja superior delegada al módulo común
     */
    constructor() {
        this.newsApi = '/api/news/f1?limit=10';
        this.nextMeetingApi = `/api/f1/schedule/current-or-next-meeting?year=${new Date().getFullYear()}`;
        this.nextSessionApi = `/api/f1/schedule/next-session?year=${new Date().getFullYear()}`;
        this.newsList = document.getElementById('newsList');
        this.raceStripTitle = document.getElementById('raceStripTitle');
        this.raceStripMeta = document.getElementById('raceStripMeta');
        this.raceStripAction = document.getElementById('raceStripAction');
        this.raceStripClocks = document.getElementById('raceStripClocks');
        this.raceStripFlag = document.getElementById('raceStripFlag');
        this.selectedIndex = null;
        this.nextMeeting = null;
        this.nextSession = null;
        this.ownsRaceStrip = !window.raceStreamRaceStrip;
        if (this.ownsRaceStrip) this.loadNextMeeting();
        this.loadNews();
        if (this.ownsRaceStrip) setInterval(() => this.updateRaceStripClocks(), 1000);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Carga el próximo GP para sincronizar la franja superior con el resto de páginas
     */
    async loadNextMeeting() {
        this.nextMeeting = await this.fetchJson(this.nextMeetingApi, null);
        this.nextSession = await this.fetchJson(this.nextSessionApi, null);
        if (!this.nextMeeting?.meeting_name) {
            this.raceStripTitle.textContent = 'Calendario no disponible';
            this.raceStripMeta.textContent = '-';
            this.raceStripClocks.textContent = '-';
            return;
        }

        this.raceStripTitle.textContent = this.nextMeeting.meeting_name;
        this.raceStripMeta.textContent = this.formatDateRange(this.nextMeeting.date_start, this.nextMeeting.date_end);
        this.renderRaceStripAction();
        this.raceStripFlag.style.display = this.nextMeeting.country_flag ? 'block' : 'none';
        if (this.nextMeeting.country_flag) {
            this.raceStripFlag.src = this.nextMeeting.country_flag;
        }
        this.updateRaceStripClocks();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Obtiene noticias del backend, descarta temas ajenos a F1 y pinta el listado completo
     */
    async loadNews() {
        const news = await this.fetchJson(this.newsApi, []);
        const f1News = (Array.isArray(news) ? news : []).filter((item) => this.isFormulaOneNews(`${item.title || ''} ${item.description || ''} ${item.content || ''}`));
        if (!f1News.length) {
            this.newsList.innerHTML = '<p class="empty-state">No hay noticias de Fórmula 1 disponibles ahora mismo.</p>';
            return;
        }

        this.newsList.innerHTML = f1News.map((item, index) => this.renderArticle(item, index)).join('');
        this.bindArticleClicks();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Renderiza una noticia con todos los campos útiles de GNews
     * @param {Object} item Noticia GNews
     * @param {number} index Posicion de la noticia
     * @returns {string} HTML de la noticia
     */
    renderArticle(item, index) {
        const title = this.escapeHtml(item.title || 'Noticia de Fórmula 1');
        const content = this.escapeHtml(item.content || 'GNews no ha incluido contenido ampliado para esta noticia.');
        const image = this.escapeAttribute(item.image || '/assets/img/LogoRS2.png');
        const url = this.escapeAttribute(item.url || '#');
        const sourceName = this.escapeHtml(item.source?.name || 'GNews');
        const date = this.formatDate(item.publishedAt);
        const expanded = index === this.selectedIndex;
        const selectedClass = expanded ? ' rs-news-card--selected rs-news-card--expanded' : '';

        return `
            <article class="rs-news-card${selectedClass}" id="article-${index}">
                <img class="rs-news-card__image" src="${image}" alt="Imagen de ${title}" loading="lazy" onerror="this.src='/assets/img/LogoRS2.png';">
                <div class="rs-news-card__body">
                    <span class="rs-news-card__source">${sourceName}</span>
                    <h2>${title}</h2>
                    <div class="rs-news-card__meta">
                        <span>${date}</span>
                    </div>
                    <div class="rs-news-card__content">
                        <p>${content}</p>
                    </div>
                    <a class="rs-button rs-button--primary rs-news-card__original" href="${url}" target="_blank" rel="noopener noreferrer">Leer noticia original</a>
                </div>
            </article>
        `;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Asocia el despliegue de noticias a hover y click táctil
     */
    bindArticleClicks() {
        this.newsList.querySelectorAll('.rs-news-card').forEach((card, index) => {
            card.addEventListener('click', (event) => {
                if (event.target.closest('a')) return;
                this.toggleArticle(index);
            });
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Despliega una sola noticia y contrae la anterior
     * @param {number} index Indice de la noticia
     */
    toggleArticle(index) {
        const target = this.newsList.querySelector(`#article-${index}`);
        this.setArticleExpanded(index, !target?.classList.contains('rs-news-card--expanded'));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Marca una noticia como desplegada o contraída
     * @param {number} index Indice de la noticia
     * @param {boolean} open Indica si queda desplegada
     */
    setArticleExpanded(index, open) {
        this.newsList.querySelectorAll('.rs-news-card').forEach((card, cardIndex) => {
            const expanded = cardIndex === index && open;
            card.classList.toggle('rs-news-card--expanded', expanded);
            card.classList.toggle('rs-news-card--selected', expanded);
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Lee JSON devolviendo fallback seguro sin cachear respuestas vacías
     * @param {string} url URL a consultar
     * @param {*} fallback Valor fallback
     * @returns {Promise<*>} JSON o fallback
     */
    async fetchJson(url, fallback) {
        return window.RaceStreamApi
            ? window.RaceStreamApi.fetchJson(url, fallback, { attempts: 3, retryEmpty: true })
            : fallback;
    }

    formatDate(value) {
        return value ? new Date(value).toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' }) : 'Fecha no disponible';
    }

    updateRaceStripClocks() {
        if (!this.nextMeeting?.date_start) {
            return;
        }

        this.raceStripClocks.innerHTML = `
            <div class="rs-race-strip__clock-card">
                <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-label">MI HORA</span><strong class="rs-race-strip__clock-value">${this.getNowTime()}</strong></div>
                <span class="rs-race-strip__clock-divider"></span>
                <div class="rs-race-strip__clock-row"><span class="rs-race-strip__clock-subvalue">CIRCUITO</span><span class="rs-race-strip__clock-track-value">${this.getCircuitNowTime(this.nextMeeting.gmt_offset)}</span></div>
            </div>
        `;
    }

    renderRaceStripAction() {
        this.raceStripAction.innerHTML = `<span class="rs-race-strip__status">${this.getCountdown(this.nextSession?.session_name || 'Sesión', this.nextSession?.date_start || this.nextMeeting.date_start)}</span>`;
    }

    formatDateRange(startValue, endValue) {
        if (!startValue || !endValue) {
            return '-';
        }
        const start = new Date(startValue);
        const end = new Date(endValue);
        const startMonth = start.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        const endMonth = end.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
        return startMonth === endMonth
            ? `${startMonth} ${start.getDate()}-${end.getDate()}`
            : `${startMonth} ${start.getDate()} - ${endMonth} ${end.getDate()}`;
    }

    getNowTime() {
        return new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    }

    getCircuitNowTime(offset) {
        const date = new Date(Date.now() + this.parseOffset(offset || '+00:00') * 60000);
        return `${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
    }

    parseOffset(offset) {
        const sign = `${offset}`.startsWith('-') ? -1 : 1;
        const [hours = '0', minutes = '0'] = `${offset}`.replace('+', '').replace('-', '').split(':');
        return sign * (Number(hours) * 60 + Number(minutes));
    }

    getCountdown(sessionName, value) {
        const diff = new Date(value).getTime() - Date.now();
        if (diff <= 0) {
            return 'En curso';
        }
        const days = Math.floor(diff / 86400000);
        const hours = Math.floor((diff % 86400000) / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);
        return `${this.translateSessionName(sessionName)} en ${days}d ${hours}h ${minutes}m`;
    }

    translateSessionName(name) {
        return ({ 'Practice 1': 'Libres 1', 'Practice 2': 'Libres 2', 'Practice 3': 'Libres 3', 'Sprint Qualifying': 'Clasif. sprint', Qualifying: 'Clasificación', Race: 'Carrera', Sprint: 'Sprint' })[name] || name || 'Sesión';
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Comprueba que el texto de GNews pertenece claramente a Fórmula 1
     * @param {string} value Texto agregado de título, descripción y contenido
     * @returns {boolean} Indica si la noticia es válida para RaceStream
     */
    isFormulaOneNews(value) {
        return /f[oó]rmula\s*1|formula\s*one|\bf1\b|grand prix|gran premio|fia|verstappen|hamilton|alonso|sainz|norris|leclerc|piastri|russell|antonelli|bearman|bortoleto|lindblad|lawson|tsunoda|ocon|gasly|albon|hulkenberg|hülkenberg|stroll|colapinto|ferrari|mclaren|mercedes|red bull|racing bulls|aston martin|williams|alpine|haas|sauber|cadillac|audi/i.test(value || '');
    }

    escapeHtml(value) {
        return `${value}`.replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
    }

    escapeAttribute(value) {
        return this.escapeHtml(value);
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 30-04-2026
 * @description Inicializa Noticias cuando el DOM está listo
 */
document.addEventListener('DOMContentLoaded', () => {
    window.raceStreamNewsPage = new RaceStreamNewsPage();
});
