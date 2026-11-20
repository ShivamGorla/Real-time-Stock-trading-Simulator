
/* StockSim Dashboard Controller
   Milestone 2: Dynamic Stock Listings, Search & Interactive Chart
*/

document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    var inr = (window.StockSim && window.StockSim.inr) ||
        new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        });

    var formatINR = (window.StockSim && window.StockSim.formatINR) ||
        function (n) {
            return inr.format(Number(n) || 0);
        };

    var state = {
        stocks: [],
        filteredStocks: [],
        selectedSymbol: 'RELIANCE',
        selectedTimeframe: '1D',
        rawHistory: [],
        historyRequestId: 0,
        searchRequestId: 0,
        searchDebounceTimer: null,
        isMock: false,
        historyIsMock: false,
        orderSide: 'BUY',
        orderQuantity: 1,
        availableCash: 100000.00
    };

    var els = {
        noticeBar: document.getElementById('market-feed-notice'),
        overviewGrid: document.getElementById('market-overview-grid'),
        watchlist: document.getElementById('watchlist-items'),

        searchInput: document.getElementById('watchlist-search'),
        searchClear: document.getElementById('watchlist-search-clear'),
        searchCount: document.getElementById('watchlist-count'),

        selectedSymbol: document.getElementById('selected-stock-symbol'),
        selectedName: document.getElementById('selected-stock-name'),
        selectedPrice: document.getElementById('selected-stock-price'),
        selectedChange: document.getElementById('selected-stock-change'),
        selectedHigh: document.getElementById('selected-stock-high'),
        selectedLow: document.getElementById('selected-stock-low'),
        selectedSector: document.getElementById('selected-stock-sector'),
        selectedUpdated: document.getElementById('selected-stock-updated'),

        chartSvg: document.getElementById('stock-chart'),
        chartLine: document.getElementById('chart-line'),
        chartArea: document.getElementById('chart-area'),
        chartDot: document.getElementById('chart-dot'),
        chartOpenLine: document.getElementById('chart-open-line'),
        chartGrid: document.getElementById('chart-grid'),
        chartAxis: document.getElementById('chart-axis'),

        chartLoading: document.getElementById('chart-loading'),
        chartError: document.getElementById('chart-error'),
        chartErrorMsg: document.getElementById('chart-error-msg'),
        chartRetryBtn: document.getElementById('chart-retry-btn'),
        chartEmpty: document.getElementById('chart-empty'),

        chartRangeLabel: document.getElementById('chart-range-label'),
        chartPointsCount: document.getElementById('chart-points-count'),

        timeframePills: document.querySelectorAll('.timeframe-pill'),

        ticketSymbol: document.getElementById('ticket-stock-symbol'),
        ticketName: document.getElementById('ticket-stock-name'),
        ticketPrice: document.getElementById('ticket-stock-price'),
        ticketTabBuy: document.getElementById('ticket-tab-buy'),
        ticketTabSell: document.getElementById('ticket-tab-sell'),
        ticketQty: document.getElementById('ticket-qty'),
        ticketQtyDec: document.getElementById('ticket-qty-dec'),
        ticketQtyInc: document.getElementById('ticket-qty-inc'),
        ticketUnitPrice: document.getElementById('ticket-unit-price'),
        ticketEstimatedTotal: document.getElementById('ticket-estimated-total'),
        ticketAvailableCash: document.getElementById('ticket-available-cash'),
        ticketInsufficientWarning: document.getElementById('ticket-insufficient-warning'),
        ticketSubmitBtn: document.getElementById('ticket-submit-btn'),
        ticketSubmitText: document.getElementById('ticket-submit-text'),
        ticketFeedback: document.getElementById('ticket-feedback'),
        ticketFeedbackMsg: document.getElementById('ticket-feedback-msg'),

        holdingsTbody: document.getElementById('holdings-tbody'),
        holdingsEmpty: document.getElementById('holdings-empty'),
        tradesTbody: document.getElementById('trades-tbody'),
        tradesEmpty: document.getElementById('trades-empty')
    };

    function cssVar(name) {
        return getComputedStyle(document.documentElement)
            .getPropertyValue(name).trim();
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (m) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[m];
        });
    }

    function showNotice(message) {
        if (!els.noticeBar) return;

        els.noticeBar.hidden = false;
        els.noticeBar.textContent = message;
    }

    /* ---------- Market Overview ---------- */

    function renderOverview(overview) {
        if (!els.overviewGrid || !overview) return;

        var b = overview.benchmark;
        var tg = overview.topGainer;
        var tl = overview.topLoser;
        var ma = overview.mostActive;

        if (!b || !tg || !tl || !ma) {
            els.overviewGrid.textContent = 'Market overview is unavailable.';
            return;
        }

        var bUp = Number(b.change) >= 0;
        var tgUp = Number(tg.change) >= 0;
        var tlUp = Number(tl.change) >= 0;
        var maUp = Number(ma.change) >= 0;

        els.overviewGrid.innerHTML = [
            '<div class="panel overview-card">',
                '<div class="overview-head">',
                    '<span class="overview-title">',
                        escapeHtml(b.name),
                    '</span>',
                    '<span class="tag-sim">Market overview</span>',
                '</div>',
                '<div class="overview-val num">',
                    Number(b.value || 0).toLocaleString('en-IN', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    }),
                '</div>',
                '<div class="overview-sub num ',
                    bUp ? 'is-gain' : 'is-loss',
                '">',
                    bUp ? '+' : '−',
                    Math.abs(Number(b.change) || 0).toFixed(2),
                    ' (',
                    bUp ? '+' : '−',
                    Math.abs(Number(b.changePercent) || 0).toFixed(2),
                    '%)',
                '</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head">',
                    '<span class="overview-title">Top Gainer</span>',
                    '<span class="tag-sim">Market data</span>',
                '</div>',
                '<div class="overview-split">',
                    '<div>',
                        '<strong class="overview-sym">',
                            escapeHtml(tg.symbol),
                        '</strong>',
                        '<span class="overview-comp">',
                            escapeHtml(tg.companyName),
                        '</span>',
                    '</div>',
                    '<div class="r">',
                        '<div class="overview-val-sm num">',
                            formatINR(tg.currentPrice),
                        '</div>',
                        '<div class="overview-sub num is-gain">',
                            '+',
                            Math.abs(Number(tg.changePercent) || 0).toFixed(2),
                            '%',
                        '</div>',
                    '</div>',
                '</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head">',
                    '<span class="overview-title">Top Loser</span>',
                    '<span class="tag-sim">Market data</span>',
                '</div>',
                '<div class="overview-split">',
                    '<div>',
                        '<strong class="overview-sym">',
                            escapeHtml(tl.symbol),
                        '</strong>',
                        '<span class="overview-comp">',
                            escapeHtml(tl.companyName),
                        '</span>',
                    '</div>',
                    '<div class="r">',
                        '<div class="overview-val-sm num">',
                            formatINR(tl.currentPrice),
                        '</div>',
                        '<div class="overview-sub num is-loss">',
                            '−',
                            Math.abs(Number(tl.changePercent) || 0).toFixed(2),
                            '%',
                        '</div>',
                    '</div>',
                '</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head">',
                    '<span class="overview-title">Most Active</span>',
                    '<span class="tag-sim">Market data</span>',
                '</div>',
                '<div class="overview-split">',
                    '<div>',
                        '<strong class="overview-sym">',
                            escapeHtml(ma.symbol),
                        '</strong>',
                        '<span class="overview-comp">',
                            escapeHtml(ma.companyName),
                        '</span>',
                    '</div>',
                    '<div class="r">',
                        '<div class="overview-val-sm num">',
                            formatINR(ma.currentPrice),
                        '</div>',
                        '<div class="overview-sub num ',
                            maUp ? 'is-gain' : 'is-loss',
                        '">',
                            maUp ? '+' : '−',
                            Math.abs(Number(ma.changePercent) || 0).toFixed(2),
                            '%',
                        '</div>',
                    '</div>',
                '</div>',
            '</div>'
        ].join('');
    }

    /* ---------- Watchlist ---------- */

    function renderWatchlist(stocksToRender) {
        if (!els.watchlist) return;

        var list = Array.isArray(stocksToRender)
            ? stocksToRender
            : state.filteredStocks;

        if (els.searchCount) {
            var total = state.stocks.length;

            els.searchCount.textContent = list.length === total
                ? total + ' stocks'
                : list.length + ' of ' + total + ' matches';
        }

        if (list.length === 0) {
            var query = els.searchInput
                ? els.searchInput.value.trim()
                : '';

            els.watchlist.innerHTML = [
                '<div class="search-empty-state">',
                    '<svg viewBox="0 0 24 24" fill="none" ',
                        'stroke="currentColor" stroke-width="2" ',
                        'stroke-linecap="round" aria-hidden="true">',
                        '<circle cx="11" cy="11" r="8"/>',
                        '<line x1="21" y1="21" x2="16.65" y2="16.65"/>',
                        '<line x1="8" y1="11" x2="14" y2="11"/>',
                    '</svg>',
                    '<strong>No stocks matching "',
                        escapeHtml(query),
                    '"</strong>',
                    '<p>Try searching by ticker or company name.</p>',
                '</div>'
            ].join('');

            return;
        }

        var html = '';

        list.forEach(function (stock) {
            var up = Number(stock.change) >= 0;
            var isSelected = stock.symbol === state.selectedSymbol;

            html += [
                '<div class="stock-item',
                    isSelected ? ' is-active' : '',
                '" data-symbol="',
                    escapeHtml(stock.symbol),
                '" tabindex="0" role="button" aria-pressed="',
                    isSelected,
                '">',
                    '<div class="stock-item-info">',
                        '<strong class="stock-item-sym">',
                            escapeHtml(stock.symbol),
                        '</strong>',
                        '<span class="stock-item-name">',
                            escapeHtml(stock.companyName),
                        '</span>',
                    '</div>',
                    '<div class="stock-item-quote r">',
                        '<span class="stock-item-price num">',
                            formatINR(stock.currentPrice),
                        '</span>',
                        '<span class="stock-item-change num ',
                            up ? 'is-gain' : 'is-loss',
                        '">',
                            up ? '+' : '−',
                            Math.abs(Number(stock.changePercent) || 0).toFixed(2),
                            '%',
                        '</span>',
                    '</div>',
                '</div>'
            ].join('');
        });

        els.watchlist.innerHTML = html;

        els.watchlist.querySelectorAll('.stock-item').forEach(function (item) {
            item.addEventListener('click', function () {
                selectStock(item.dataset.symbol);
            });

            item.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    selectStock(item.dataset.symbol);
                }
            });
        });
    }

    /* ---------- Stock Selection ---------- */

    function selectStock(symbol) {
        var stock = state.stocks.find(function (item) {
            return item.symbol === symbol;
        });

        if (!stock) {
            console.warn('Stock not found:', symbol);
            return;
        }

        state.selectedSymbol = symbol;

        renderWatchlist(state.filteredStocks);

        var up = Number(stock.change) >= 0;

        if (els.selectedSymbol) {
            els.selectedSymbol.textContent = stock.symbol;
        }

        if (els.selectedName) {
            els.selectedName.textContent = stock.companyName;
        }

        if (els.selectedPrice) {
            els.selectedPrice.textContent = formatINR(stock.currentPrice);
        }

        if (els.selectedChange) {
            els.selectedChange.textContent =
                (up ? '+' : '−') +
                formatINR(Math.abs(Number(stock.change) || 0)) +
                ' (' +
                (up ? '+' : '−') +
                Math.abs(Number(stock.changePercent) || 0).toFixed(2) +
                '%)';

            els.selectedChange.className =
                'stock-detail-change num ' + (up ? 'is-gain' : 'is-loss');
        }

        if (els.selectedHigh) {
            els.selectedHigh.textContent = formatINR(
                stock.high || stock.currentPrice * 1.015
            );
        }

        if (els.selectedLow) {
            els.selectedLow.textContent = formatINR(
                stock.low || stock.currentPrice * 0.985
            );
        }

        if (els.selectedSector) {
            els.selectedSector.textContent = stock.sector || 'NSE Equities';
        }

        if (els.selectedUpdated) {
            var updated = stock.lastUpdated
                ? new Date(stock.lastUpdated)
                : new Date();

            els.selectedUpdated.textContent =
                'Last update: ' +
                updated.toLocaleTimeString('en-IN', {
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit'
                });
        }

        loadStockHistory(symbol);
        updateOrderTicket();
    }

    /* ---------- Order Ticket & Estimation (Milestone 3) ---------- */

    function updateOrderTicket() {
        var stock = state.stocks.find(function (item) {
            return item.symbol === state.selectedSymbol;
        });

        var unitPrice = stock ? Number(stock.currentPrice) || 0 : 0;
        var sym = stock ? stock.symbol : state.selectedSymbol;
        var name = stock ? stock.companyName : '';

        if (els.ticketSymbol) els.ticketSymbol.textContent = sym;
        if (els.ticketName) els.ticketName.textContent = name;
        if (els.ticketPrice) els.ticketPrice.textContent = formatINR(unitPrice);
        if (els.ticketUnitPrice) els.ticketUnitPrice.textContent = formatINR(unitPrice);

        var rawQty = els.ticketQty ? Number(els.ticketQty.value) : 1;
        var qty = Math.max(1, Math.min(10000, Math.floor(rawQty) || 1));
        state.orderQuantity = qty;

        var estimatedTotal = unitPrice * qty;

        if (els.ticketEstimatedTotal) {
            els.ticketEstimatedTotal.textContent = formatINR(estimatedTotal);
        }

        if (els.ticketAvailableCash) {
            els.ticketAvailableCash.textContent = formatINR(state.availableCash);
        }

        /* Check cash sufficiency for BUY orders (does not block, displays warning) */
        if (els.ticketInsufficientWarning) {
            if (state.orderSide === 'BUY' && estimatedTotal > state.availableCash) {
                els.ticketInsufficientWarning.hidden = false;
            } else {
                els.ticketInsufficientWarning.hidden = true;
            }
        }

        /* Dynamic button label and action style */
        if (els.ticketSubmitText) {
            els.ticketSubmitText.textContent = state.orderSide === 'BUY'
                ? 'Place Buy Order'
                : 'Place Sell Order';
        }

        if (els.ticketSubmitBtn) {
            els.ticketSubmitBtn.className = 'btn btn-block ' +
                (state.orderSide === 'BUY' ? 'btn-buy' : 'btn-sell');
        }
    }

    function handleTicketTab(side) {
        state.orderSide = side;

        if (els.ticketTabBuy) {
            els.ticketTabBuy.classList.toggle('is-active', side === 'BUY');
            els.ticketTabBuy.setAttribute('aria-selected', String(side === 'BUY'));
        }

        if (els.ticketTabSell) {
            els.ticketTabSell.classList.toggle('is-active', side === 'SELL');
            els.ticketTabSell.setAttribute('aria-selected', String(side === 'SELL'));
        }

        if (els.ticketFeedback) {
            els.ticketFeedback.hidden = true;
        }

        updateOrderTicket();
    }

    function handleQuantityStep(delta) {
        if (!els.ticketQty) return;

        var current = Math.floor(Number(els.ticketQty.value) || 1);
        var next = Math.max(1, Math.min(10000, current + delta));

        els.ticketQty.value = next;
        updateOrderTicket();
    }

    function handleOrderSubmit() {
        if (!els.ticketFeedback) return;

        /* Honest unavailable message: Order execution engine is not connected on this branch.
           Strictly: No fake order requests, no synthetic balance deductions, no mock transactions. */
        els.ticketFeedback.hidden = false;
        if (els.ticketFeedbackMsg) {
            els.ticketFeedbackMsg.textContent =
                'The backend trading engine is not connected on this branch. Orders cannot be submitted or settled at this time.';
        }
    }

    /* ---------- Chart Timeframe ---------- */

    function sliceTimeframe(history, timeframe) {
        if (!Array.isArray(history) || history.length === 0) {
            return [];
        }

        var total = history.length;

        if (timeframe === '1D') {
            return history.slice(Math.max(0, total - 24));
        }

        if (timeframe === '1W') {
            return history.slice(Math.max(0, total - 40));
        }

        if (timeframe === '1M') {
            return history.slice(Math.max(0, total - 56));
        }

        return history.slice();
    }

    /* ---------- Chart Rendering ---------- */

    function clearChart() {
        if (els.chartLine) els.chartLine.setAttribute('d', '');
        if (els.chartArea) els.chartArea.setAttribute('d', '');
        if (els.chartGrid) els.chartGrid.innerHTML = '';
        if (els.chartAxis) els.chartAxis.innerHTML = '';
    }

    function renderChart(history, timeframe) {
        if (!els.chartSvg) return;

        var points = sliceTimeframe(history, timeframe);

        if (points.length < 2) {
            if (els.chartEmpty) els.chartEmpty.hidden = false;
            if (els.chartLoading) els.chartLoading.hidden = true;
            if (els.chartError) els.chartError.hidden = true;

            clearChart();

            if (els.chartPointsCount) {
                els.chartPointsCount.textContent = 'No data points';
            }

            return;
        }

        if (els.chartEmpty) els.chartEmpty.hidden = true;
        if (els.chartLoading) els.chartLoading.hidden = true;
        if (els.chartError) els.chartError.hidden = true;

        var W = 540;
        var H = 220;
        var PAD_T = 16;
        var PAD_B = 24;

        var prices = points.map(function (point) {
            return Number(point.price);
        });

        if (prices.some(function (price) {
            return !Number.isFinite(price);
        })) {
            clearChart();
            if (els.chartEmpty) els.chartEmpty.hidden = false;
            return;
        }

        var sessionOpen = prices[0];
        var lastPrice = prices[prices.length - 1];

        var min = Math.min.apply(null, prices);
        var max = Math.max.apply(null, prices);

        var span = Math.max(max - min, Math.abs(sessionOpen) * 0.005, 0.01);

        min -= span * 0.08;
        max += span * 0.08;

        function getY(value) {
            return PAD_T +
                (1 - (value - min) / (max - min)) *
                (H - PAD_T - PAD_B);
        }

        function getX(index) {
            return (index / (points.length - 1)) * W;
        }

        var path = points.map(function (point, index) {
            var command = index === 0 ? 'M' : 'L';

            return command +
                getX(index).toFixed(1) + ' ' +
                getY(Number(point.price)).toFixed(1);
        }).join(' ');

        var up = lastPrice >= sessionOpen;
        var color = cssVar(up ? '--gain' : '--loss') || (up ? '#16a34a' : '#dc2626');

        if (els.chartLine) {
            els.chartLine.setAttribute('d', path);
            els.chartLine.setAttribute('stroke', color);
        }

        if (els.chartArea) {
            els.chartArea.setAttribute(
                'd',
                path +
                ' L' + W + ' ' + (H - PAD_B) +
                ' L0 ' + (H - PAD_B) + ' Z'
            );

            els.chartArea.setAttribute('fill', color);
        }

        if (els.chartDot) {
            els.chartDot.setAttribute('cx', getX(points.length - 1).toFixed(1));
            els.chartDot.setAttribute('cy', getY(lastPrice).toFixed(1));
            els.chartDot.setAttribute('fill', color);
        }

        if (els.chartOpenLine) {
            var openY = getY(sessionOpen).toFixed(1);

            els.chartOpenLine.setAttribute('y1', openY);
            els.chartOpenLine.setAttribute('y2', openY);
        }

        /* Grid lines and price labels */

        var gridHtml = '';
        var axisHtml = '';
        var steps = 4;

        for (var g = 0; g < steps; g++) {
            var value = min + ((max - min) * (g + 0.5)) / steps;
            var gridY = getY(value).toFixed(1);

            gridHtml +=
                '<line x1="0" x2="' + W +
                '" y1="' + gridY +
                '" y2="' + gridY + '"/>';

            axisHtml +=
                '<text x="' + (W + 6) +
                '" y="' + (Number(gridY) + 4) +
                '">' +
                Math.round(value).toLocaleString('en-IN') +
                '</text>';
        }

        if (els.chartGrid) {
            els.chartGrid.innerHTML = gridHtml;
        }

        if (els.chartAxis) {
            els.chartAxis.innerHTML = axisHtml;
        }

        if (els.chartRangeLabel) {
            els.chartRangeLabel.textContent =
                'Timeframe: ' + timeframe +
                ' (Session open: ' + formatINR(sessionOpen) + ')';
        }

        if (els.chartPointsCount) {
            els.chartPointsCount.textContent =
                points.length + ' data points' +
                (state.historyIsMock ? ' · Simulated' : '');
        }
    }

    /* ---------- Historical Data Loading ---------- */

    async function loadStockHistory(symbol) {
        var requestId = ++state.historyRequestId;

        state.rawHistory = [];

        if (els.chartLoading) els.chartLoading.hidden = false;
        if (els.chartError) els.chartError.hidden = true;
        if (els.chartEmpty) els.chartEmpty.hidden = true;

        clearChart();

        try {
            var api = window.StockSimAPI;

            if (!api || typeof api.getStockHistory !== 'function') {
                throw new Error('Stock history API is unavailable.');
            }

            var response = await api.getStockHistory(symbol, 72);

            if (requestId !== state.historyRequestId) return;
            if (symbol !== state.selectedSymbol) return;

            state.rawHistory = Array.isArray(response.history)
                ? response.history
                : [];

            state.historyIsMock = Boolean(response.isMock);

            if (els.chartLoading) els.chartLoading.hidden = true;

            renderChart(state.rawHistory, state.selectedTimeframe);

        } catch (error) {
            if (requestId !== state.historyRequestId) return;

            if (els.chartLoading) els.chartLoading.hidden = true;

            if (els.chartError) {
                els.chartError.hidden = false;
            }

            if (els.chartErrorMsg) {
                els.chartErrorMsg.textContent =
                    'Failed to load history for ' +
                    symbol + ': ' +
                    (error.message || 'Server error');
            }

            console.error('Stock history error:', error);
        }
    }

    /* ---------- Search ---------- */

    async function performSearch(query, requestId) {
        try {
            var api = window.StockSimAPI;

            if (!api || typeof api.searchStocks !== 'function') {
                throw new Error('Stock search API is unavailable.');
            }

            var response = await api.searchStocks(query);

            /* Ignore outdated search responses. */
            if (requestId !== state.searchRequestId) return;

            state.filteredStocks = Array.isArray(response.stocks)
                ? response.stocks
                : [];

            renderWatchlist(state.filteredStocks);

        } catch (error) {
            if (requestId !== state.searchRequestId) return;

            console.error('Stock search error:', error);
            showNotice('Unable to search stocks. Please try again.');
        }
    }

    function handleSearchInput() {
        if (!els.searchInput) return;

        var query = els.searchInput.value.trim();

        if (els.searchClear) {
            els.searchClear.hidden = query.length === 0;
        }

        clearTimeout(state.searchDebounceTimer);

        /* Every new search invalidates older requests immediately. */
        var requestId = ++state.searchRequestId;

        state.searchDebounceTimer = setTimeout(function () {
            performSearch(query, requestId);
        }, 150);
    }

    function clearSearch() {
        if (!els.searchInput) return;

        clearTimeout(state.searchDebounceTimer);

        /* Prevent an older search response from overwriting the full list. */
        state.searchRequestId++;

        els.searchInput.value = '';

        if (els.searchClear) {
            els.searchClear.hidden = true;
        }

        state.filteredStocks = state.stocks.slice();

        renderWatchlist(state.filteredStocks);
        els.searchInput.focus();
    }

    /* ---------- Event Listeners ---------- */

    function setupEventListeners() {
        if (els.searchInput) {
            els.searchInput.addEventListener('input', handleSearchInput);
        }

        if (els.searchClear) {
            els.searchClear.addEventListener('click', clearSearch);
        }

        els.timeframePills.forEach(function (pill) {
            pill.addEventListener('click', function () {
                var timeframe = pill.dataset.timeframe;

                if (!timeframe) return;

                els.timeframePills.forEach(function (item) {
                    item.classList.remove('is-active');
                    item.setAttribute('aria-selected', 'false');
                });

                pill.classList.add('is-active');
                pill.setAttribute('aria-selected', 'true');

                state.selectedTimeframe = timeframe;

                renderChart(state.rawHistory, state.selectedTimeframe);
            });
        });

        if (els.chartRetryBtn) {
            els.chartRetryBtn.addEventListener('click', function () {
                loadStockHistory(state.selectedSymbol);
            });
        }

        /* Order Ticket event listeners (Milestone 3) */
        if (els.ticketTabBuy) {
            els.ticketTabBuy.addEventListener('click', function () {
                handleTicketTab('BUY');
            });
        }

        if (els.ticketTabSell) {
            els.ticketTabSell.addEventListener('click', function () {
                handleTicketTab('SELL');
            });
        }

        if (els.ticketQtyDec) {
            els.ticketQtyDec.addEventListener('click', function () {
                handleQuantityStep(-1);
            });
        }

        if (els.ticketQtyInc) {
            els.ticketQtyInc.addEventListener('click', function () {
                handleQuantityStep(1);
            });
        }

        if (els.ticketQty) {
            els.ticketQty.addEventListener('input', function () {
                updateOrderTicket();
            });
            els.ticketQty.addEventListener('change', function () {
                var v = Math.max(1, Math.min(10000, Math.floor(Number(els.ticketQty.value) || 1)));
                els.ticketQty.value = v;
                updateOrderTicket();
            });
        }

        if (els.ticketSubmitBtn) {
            els.ticketSubmitBtn.addEventListener('click', handleOrderSubmit);
        }

        document.addEventListener('themechange', function () {
            if (state.rawHistory.length > 0) {
                renderChart(state.rawHistory, state.selectedTimeframe);
            }
        });
    }

    /* ---------- Initialization ---------- */

    async function init() {
        if (!document.getElementById('dashboard-profile')) return;

        setupEventListeners();

        try {
            var api = window.StockSimAPI;

            if (!api) {
                throw new Error('StockSim API client is unavailable.');
            }

            /* Read user profile virtual balance if available */
            try {
                if (typeof api.getUserProfile === 'function') {
                    var profile = await api.getUserProfile();
                    if (profile && profile.virtualBalance != null) {
                        var parsedBalance = Number(profile.virtualBalance);
                        if (Number.isFinite(parsedBalance) && parsedBalance >= 0) {
                            state.availableCash = parsedBalance;
                        }
                        if (els.ticketAvailableCash) {
                            els.ticketAvailableCash.textContent = formatINR(state.availableCash);
                        }
                    }
                }
            } catch (profileErr) {
                /* Defaults / auth.js already managing the balance display */
            }

            var overviewResponse = await api.getMarketOverview();

            if (overviewResponse && overviewResponse.data) {
                renderOverview(overviewResponse.data);
            }

            var stockResponse = await api.getStocks();

            state.stocks = Array.isArray(stockResponse.stocks)
                ? stockResponse.stocks
                : [];

            state.filteredStocks = state.stocks.slice();
            state.isMock = Boolean(stockResponse.isMock);

            if (els.noticeBar) {
                if (state.isMock) {
                    showNotice(
                        stockResponse.notice ||
                        'Illustrative simulated market data — backend market feed unavailable.'
                    );
                } else {
                    els.noticeBar.hidden = true;
                }
            }

            renderWatchlist(state.filteredStocks);

            if (state.stocks.length > 0) {
                var initialStock = state.stocks.find(function (stock) {
                    return stock.symbol === state.selectedSymbol;
                }) || state.stocks[0];

                selectStock(initialStock.symbol);
            } else {
                showNotice('No stocks are currently available.');
                if (els.chartEmpty) els.chartEmpty.hidden = false;
            }

        } catch (error) {
            console.error('Dashboard initialization error:', error);

            showNotice(
                'Failed to load market data: ' +
                (error.message || 'Unknown error')
            );
        }
    }

    init();
});