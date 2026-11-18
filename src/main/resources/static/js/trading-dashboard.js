/* StockSim real-data trading workspace.
 * No mock prices, no assistant trade execution, no credentials stored here.
 * Requires existing StockController, OrderController, PortfolioController and CookieCsrfTokenRepository.
 */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        const workspace = document.getElementById('market-workspace');
        if (!workspace) return;

        const $ = (id) => document.getElementById(id);
        const fmt = StockSim.formatINR;
        const num = (value) => Number(value) || 0;
        const state = {
            stocks: [], symbol: null, side: 'BUY', range: '1D',
            history: [], portfolio: null, orders: [], pending: null,
            submitting: false, activeHistoryRequest: 0, alive: true, identityReady: false
        };
        const ids = {
            search: $('market-search'), list: $('market-stock-list'), marketCount: $('market-count'),
            banner: $('market-banner'), status: $('trading-status'),
            symbol: $('market-selected-symbol'), company: $('market-selected-company'),
            price: $('market-selected-price'), change: $('market-selected-change'),
            asOf: $('market-last-updated'), range: $('market-range-label'),
            chart: $('market-chart'), chartPath: $('market-chart-line'),
            chartArea: $('market-chart-area'), chartDot: $('market-chart-dot'),
            chartEmpty: $('market-chart-empty'), chartMin: $('market-chart-low'),
            chartMax: $('market-chart-high'),
            orderForm: $('trade-order-form'), orderSymbol: $('trade-order-symbol'),
            orderSide: $('trade-order-side'), orderQty: $('trade-order-quantity'),
            orderEstimated: $('trade-order-estimated'), orderBalance: $('trade-order-balance'),
            orderHeld: $('trade-order-held'), orderSubmit: $('trade-order-submit'),
            orderFeedback: $('trade-order-feedback'), orderRetry: $('trade-order-reconcile'),
            holdingsBody: $('live-holdings-body'), holdingsEmpty: $('live-holdings-empty'),
            ordersBody: $('live-orders-body'), ordersEmpty: $('live-orders-empty'),
            portfolioError: $('portfolio-loading-error'),
            refresh: $('trading-refresh'),
            invested: $('live-invested'), pnl: $('live-pnl'), positions: $('live-positions'),
            equity: $('live-equity'), realized: $('live-realized'), unrealized: $('live-unrealized'),
            cash: $('user-balance')
        };
        const POLL_MS = 15000;
        let refreshInProgress = false;
        let accountsRequest = null;
        let historyController = null;
        let pendingStorageKey = null;
        let listSignature = '', portfolioSignature = '', ordersSignature = '';
        let searchTimer;

        function savePending(pending) {
            state.pending = pending;
            ids.orderRetry.hidden = !pending;
            try {
                if (pending) sessionStorage.setItem(pendingStorageKey, JSON.stringify(pending));
                else sessionStorage.removeItem(pendingStorageKey);
            } catch (_) { /* In-page idempotency remains available if storage is blocked. */ }
        }

        function el(tag, className, text) {
            const node = document.createElement(tag);
            if (className) node.className = className;
            if (text !== undefined) node.textContent = String(text);
            return node;
        }
        function feedback(message, good) {
            if (!ids.orderFeedback) return;
            ids.orderFeedback.textContent = message;
            ids.orderFeedback.className = 'market-feedback ' + (good ? 'is-good' : 'is-bad');
            ids.orderFeedback.hidden = false;
        }
        function marketNotice(message, error) {
            ids.banner.textContent = message;
            ids.banner.className = 'market-banner' + (error ? ' is-bad' : '');
            ids.banner.hidden = false;
        }
        function clearMarketNotice() { ids.banner.hidden = true; }
        function csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
            return match ? decodeURIComponent(match[1]) : '';
        }
        async function request(url, options) {
            options = options || {};
            const method = (options.method || 'GET').toUpperCase();
            const headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
            if (method !== 'GET' && method !== 'HEAD') {
                headers['Content-Type'] = 'application/json';
                headers['X-XSRF-TOKEN'] = csrfToken();
            }
            const controller = new AbortController();
            const timeout = window.setTimeout(() => controller.abort(), 15000);
            const signal = options.signal || controller.signal;
            try {
            const response = await fetch(url, Object.assign({ credentials: 'same-origin' }, options, { headers, signal }));
            let body;
            const text = await response.text();
            try { body = text ? JSON.parse(text) : null; } catch (_) { body = null; }
            if (!response.ok) {
                const error = new Error(typeof body?.message === 'string' ? body.message :
                    (typeof body?.error === 'string' ? body.error : `Request failed (HTTP ${response.status})`));
                error.status = response.status;
                if (response.status === 401) error.message = 'Session expired. Please sign in again.';
                if (response.status === 403) error.message = 'Request denied. Refresh the page and try again.';
                throw error;
            }
            if (text && body === null) throw new Error('Unexpected server response. Check order status before retrying.');
            return body;
            } finally { window.clearTimeout(timeout); }
        }
        function selectedStock() { return state.stocks.find(s => s.symbol === state.symbol) || null; }
        function selectedHolding() {
            return (state.portfolio?.holdings || []).find(h => h.symbol === state.symbol && num(h.quantity) > 0);
        }
        function safeDate(value) {
            if (!value) return 'Not available';
            const dt = new Date(value);
            return Number.isNaN(dt.getTime()) ? 'Not available' : dt.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
        }
        function priceChange(stock) {
            const delta = num(stock.change);
            const percent = num(stock.changePercent);
            const sign = delta >= 0 ? '+' : '−';
            return `${sign}${fmt(Math.abs(delta))} (${sign}${Math.abs(percent).toFixed(2)}%)`;
        }
        function setText(node, value) { if (node && node.textContent !== value) node.textContent = value; }
        function renderMarketList() {
            const query = (ids.search.value || '').trim().toLowerCase();
            const signature = JSON.stringify([query, state.symbol, state.stocks]);
            if (signature === listSignature) return;
            listSignature = signature;
            const focusedSymbol = ids.list.contains(document.activeElement) ? document.activeElement.dataset.symbol : null;
            const list = state.stocks.filter(s =>
                String(s.symbol || '').toLowerCase().includes(query) ||
                String(s.companyName || '').toLowerCase().includes(query)
            );
            ids.list.replaceChildren();
            setText(ids.marketCount, `${list.length} / ${state.stocks.length} stocks`);
            if (!list.length) {
                ids.list.appendChild(el('p', 'market-muted market-empty', state.stocks.length ?
                    'No matching stocks.' : 'No stocks returned by the server.'));
                return;
            }
            list.forEach(stock => {
                const btn = el('button', 'market-stock-row' + (state.symbol === stock.symbol ? ' is-selected' : ''));
                btn.type = 'button';
                btn.dataset.symbol = stock.symbol;
                btn.setAttribute('aria-pressed', String(state.symbol === stock.symbol));
                const top = el('span', 'market-stock-top');
                top.append(el('strong', '', stock.symbol), el('span', '', fmt(stock.currentPrice)));
                const bottom = el('span', 'market-stock-bottom');
                bottom.append(el('span', 'market-clamp', stock.companyName || stock.symbol));
                const change = el('span', num(stock.change) >= 0 ? 'is-gain' : 'is-loss',
                    `${num(stock.changePercent) >= 0 ? '+' : ''}${num(stock.changePercent).toFixed(2)}%`);
                bottom.appendChild(change);
                btn.append(top, bottom);
                btn.addEventListener('click', () => selectStock(stock.symbol));
                ids.list.appendChild(btn);
            });
            if (focusedSymbol) Array.from(ids.list.children).find(node => node.dataset.symbol === focusedSymbol)?.focus({ preventScroll: true });
        }
        function renderSelection() {
            const stock = selectedStock();
            if (!stock) {
                setText(ids.symbol, 'Select a stock');
                setText(ids.company, 'No stock selected');
                setText(ids.price, '—');
                setText(ids.change, '—');
                setText(ids.asOf, '—');
                ids.orderSymbol.value = '';
                updateEstimate();
                return;
            }
            setText(ids.symbol, stock.symbol);
            setText(ids.company, stock.companyName || stock.symbol);
            setText(ids.price, fmt(stock.currentPrice));
            setText(ids.change, priceChange(stock));
            ids.change.className = num(stock.change) >= 0 ? 'is-gain' : 'is-loss';
            setText(ids.asOf, 'Simulated tick: ' + safeDate(stock.lastUpdated));
            ids.orderSymbol.value = stock.symbol;
            updateEstimate();
        }
        function selectStock(symbol) {
            if (!state.stocks.some(s => s.symbol === symbol)) return;
            if (state.symbol === symbol) return;
            state.symbol = symbol;
            state.history = [];
            renderMarketList();
            renderSelection();
            renderChart();
            loadHistory();
        }
        function rangeData() {
            const valid = state.history.filter(h => Number.isFinite(Number(h.price)) &&
                Number(h.price) > 0 && Number.isFinite(new Date(h.recordedAt).getTime()));
            if (state.range === 'ALL') return valid;
            const now = Date.now();
            const span = state.range === '1D' ? 86400000 : state.range === '1W' ? 604800000 : 2592000000;
            return valid.filter(h => new Date(h.recordedAt).getTime() >= now - span);
        }
        function renderChart() {
            const series = rangeData();
            setText(ids.range, `${state.range} · ${series.length} simulated updates`);
            ids.chartEmpty.hidden = series.length !== 0;
            ids.chart.hidden = series.length === 0;
            if (!series.length) {
                ids.chartPath.setAttribute('d', '');
                ids.chartArea.setAttribute('d', '');
                ids.chartDot.setAttribute('r', '0');
                setText(ids.chartMin, ''); setText(ids.chartMax, '');
                return;
            }
            const prices = series.map(x => Number(x.price));
            const minimum = Math.min(...prices), maximum = Math.max(...prices);
            const pad = Math.max((maximum - minimum) * .12, Math.max(maximum, 1) * .003);
            const lo = minimum - pad, hi = maximum + pad;
            const W = 720, H = 240, top = 16, bottom = 221;
            const x = i => 16 + (series.length < 2 ? .5 : i / (series.length - 1)) * (W - 32);
            const y = value => top + (hi - value) / (hi - lo) * (bottom - top);
            const pairs = prices.map((p, i) => [x(i), y(p)]);
            const path = pairs.map(([px, py], i) => `${i ? 'L' : 'M'}${px.toFixed(2)} ${py.toFixed(2)}`).join(' ');
            const last = pairs[pairs.length - 1];
            ids.chartPath.setAttribute('d', path);
            ids.chartArea.setAttribute('d', `${path} L${last[0].toFixed(2)} ${bottom} L${pairs[0][0].toFixed(2)} ${bottom} Z`);
            ids.chartDot.setAttribute('cx', last[0]); ids.chartDot.setAttribute('cy', last[1]);
            ids.chartDot.setAttribute('r', '4');
            const positive = prices[prices.length - 1] >= prices[0];
            const color = getComputedStyle(document.documentElement).getPropertyValue(positive ? '--gain' : '--loss').trim();
            ids.chartPath.setAttribute('stroke', color);
            ids.chartArea.setAttribute('fill', color);
            ids.chartDot.setAttribute('fill', color);
            setText(ids.chartMin, `Low ${fmt(minimum)}`);
            setText(ids.chartMax, `High ${fmt(maximum)}`);
        }
        async function loadHistory() {
            const requestNo = ++state.activeHistoryRequest;
            const symbol = state.symbol;
            if (historyController) historyController.abort();
            if (!symbol) return;
            const controller = new AbortController();
            historyController = controller;
            const timeout = window.setTimeout(() => controller.abort(), 15000);
            ids.chartEmpty.textContent = 'Loading price history…';
            ids.chartEmpty.hidden = false;
            try {
                // Backend returns at most its configured bound; use live simulated history only.
                const data = await request(`/api/stocks/${encodeURIComponent(symbol)}/history?limit=200`, { signal: controller.signal });
                if (requestNo !== state.activeHistoryRequest) return;
                state.history = Array.isArray(data) ? data : [];
                ids.chartEmpty.textContent = 'No simulated price history in this timeframe. Try All.';
                renderChart();
            } catch (err) {
                if (requestNo !== state.activeHistoryRequest) return;
                state.history = [];
                renderChart();
                ids.chartEmpty.textContent = 'Could not load simulated price history.';
                marketNotice(err.message || 'Chart unavailable.', true);
            } finally { window.clearTimeout(timeout); }
        }
        function renderPortfolio() {
            const p = state.portfolio;
            if (!p) return;
            const { generatedAt, ...snapshot } = p;
            const signature = JSON.stringify(snapshot);
            if (signature === portfolioSignature) return;
            portfolioSignature = signature;
            setText(ids.cash, fmt(p.walletBalance));
            setText(ids.invested, fmt(p.totalInvestedValue));
            setText(ids.pnl, fmt(p.totalPnl));
            setText(ids.equity, fmt(p.totalAccountEquity));
            setText(ids.realized, fmt(p.totalRealizedPnl));
            setText(ids.unrealized, fmt(p.totalUnrealizedPnl));
            ids.pnl.className = num(p.totalPnl) >= 0 ? 'is-gain' : 'is-loss';
            const open = (p.holdings || []).filter(h => num(h.quantity) > 0);
            setText(ids.positions, String(open.length));
            ids.holdingsBody.replaceChildren();
            ids.holdingsEmpty.hidden = open.length !== 0;
            open.forEach(h => {
                const tr = el('tr');
                const symbolCell = el('td');
                const pick = el('button', 'market-holding-link', h.symbol);
                pick.type = 'button';
                pick.setAttribute('aria-label', 'Select stock ' + h.symbol);
                pick.addEventListener('click', () => selectStock(h.symbol));
                symbolCell.append(pick, el('small', 'market-cell-note', h.companyName || ''));
                const qty = el('td', 'r', String(h.quantity));
                const avg = el('td', 'r', fmt(h.averageCost));
                const pnl = el('td', 'r ' + (num(h.unrealizedPnl) >= 0 ? 'is-gain' : 'is-loss'), fmt(h.unrealizedPnl));
                tr.append(symbolCell, qty, avg, pnl);
                ids.holdingsBody.appendChild(tr);
            });
            updateEstimate();
        }
        function renderOrders() {
            const signature = JSON.stringify(state.orders);
            if (signature === ordersSignature) return;
            ordersSignature = signature;
            ids.ordersBody.replaceChildren();
            const orders = state.orders.slice().sort((a, b) => {
                const timeDiff = new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
                return timeDiff || num(b.id) - num(a.id);
            }).slice(0, 8);
            ids.ordersEmpty.hidden = orders.length !== 0;
            orders.forEach(o => {
                const tr = el('tr');
                const stock = el('td');
                stock.append(el('strong', '', o.symbol), el('small', 'market-cell-note', safeDate(o.createdAt)));
                tr.append(stock,
                    el('td', o.side === 'BUY' ? 'is-gain' : 'is-loss', o.side),
                    el('td', 'r', String(o.quantity)),
                    el('td', 'r', fmt(o.executionPrice ?? o.quotedPrice)),
                    el('td', 'r', o.status));
                ids.ordersBody.appendChild(tr);
            });
        }
        function setSide(side) {
            if (side !== 'BUY' && side !== 'SELL') return;
            state.side = side;
            ids.orderSide.value = side;
            document.querySelectorAll('[data-market-side]').forEach(btn => {
                const active = btn.dataset.marketSide === side;
                btn.classList.toggle('is-active', active);
                btn.setAttribute('aria-pressed', String(active));
            });
            ids.orderSubmit.textContent = side === 'BUY' ? 'Place BUY order' : 'Place SELL order';
            ids.orderSubmit.classList.toggle('btn-buy', side === 'BUY');
            ids.orderSubmit.classList.toggle('btn-sell', side === 'SELL');
            updateEstimate();
        }
        function orderFormData() {
            const quantity = Number(ids.orderQty.value);
            if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 100000) throw new Error('Enter 1–100,000 whole shares.');
            if (!state.symbol || !selectedStock()) throw new Error('Select a stock first.');
            return { symbol: state.symbol, side: state.side, quantity };
        }
        function updateEstimate() {
            const stock = selectedStock();
            const quantity = Number(ids.orderQty.value);
            const valid = stock && Number.isSafeInteger(quantity) && quantity > 0 && quantity <= 100000;
            const estimate = valid ? Number(stock.currentPrice) * quantity : null;
            setText(ids.orderEstimated, valid ? fmt(estimate) : '—');
            setText(ids.orderBalance, state.portfolio ? fmt(state.portfolio.walletBalance) : '—');
            setText(ids.orderHeld, String(selectedHolding()?.quantity || 0));
            ids.orderSubmit.disabled = !valid || state.submitting || !state.identityReady;
        }
        function generateUUID() {
            if (globalThis.crypto?.randomUUID) return crypto.randomUUID();
            if (!globalThis.crypto?.getRandomValues) throw new Error('Secure browser context required to generate an order ID.');
            const bytes = crypto.getRandomValues(new Uint8Array(16));
            bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
            return [...bytes].map((byte, i) => [4, 6, 8, 10].includes(i) ? '-' + byte.toString(16).padStart(2, '0') : byte.toString(16).padStart(2, '0')).join('');
        }
        async function reconcilePending() {
            if (!state.pending) return false;
            const pendingId = state.pending.id;
            const orders = await request('/api/orders');
            if (Array.isArray(orders)) {
                state.orders = orders;
                renderOrders();
                const found = orders.find(o => o.clientOrderId === pendingId);
                if (found) {
                    feedback(`${found.side} ${found.quantity} ${found.symbol}: order ${found.status}. No duplicate submitted.`, found.status === 'EXECUTED');
                    if (state.pending?.id === pendingId) savePending(null);
                    await refreshSettledAccounts();
                    return true;
                }
            }
            return false;
        }
        async function submitOrder(event) {
            event.preventDefault();
            if (state.submitting || !state.identityReady) return;
            let details;
            try { details = orderFormData(); } catch (err) { feedback(err.message, false); return; }
            const stock = selectedStock();
            const display = `${details.side} ${details.quantity} ${details.symbol} at an estimated ${fmt(num(stock.currentPrice) * details.quantity)}`;
            if (!window.confirm(`${display}?\n\nFinal price is determined by the server. This is a virtual order only.`)) return;
            state.submitting = true;
            updateEstimate();
            ids.orderRetry.hidden = true;
            feedback('Submitting your simulated order…', true);
            try {
                const fingerprint = `${details.side}|${details.symbol}|${details.quantity}`;
                if (state.pending && state.pending.fingerprint !== fingerprint) {
                    // Never silently abandon a possibly submitted order when the user changed input.
                    if (await reconcilePending()) {
                        feedback('Previous order was reconciled. Review the new order and submit again.', true);
                        return;
                    }
                    throw new Error('A previous order is unresolved. Restore its details and retry before changing your order.');
                }
                if (state.pending && await reconcilePending()) return;
                if (!state.pending) savePending({ id: generateUUID(), fingerprint });
                const result = await request(details.side === 'BUY' ? '/api/orders/buy' : '/api/orders/sell', {
                    method: 'POST',
                    body: JSON.stringify({ clientOrderId: state.pending.id, symbol: details.symbol, quantity: details.quantity })
                });
                if (!result || !result.status) throw new Error('Order status was not returned. Check previous order before retrying.');
                savePending(null);
                feedback(`${details.side} ${details.quantity} ${details.symbol}: ${result.status}. ${result.status === 'EXECUTED' ? 'Portfolio updated.' : 'Order created; check status before assuming execution.'}`, true);
                await Promise.allSettled([refreshSettledAccounts(), loadStocks(true)]);
            } catch (err) {
                // 422 can leave a committed PENDING order after settlement rollback. Reconcile it too.
                if ([400, 401, 403, 404].includes(err.status)) savePending(null);
                feedback(err.message || 'Could not confirm the order. Review recent orders before trying again.', false);
                ids.orderRetry.hidden = !state.pending;
            } finally {
                state.submitting = false;
                updateEstimate();
            }
        }
        function refreshAccounts() {
            if (accountsRequest) return accountsRequest;
            accountsRequest = loadAccounts().finally(() => { accountsRequest = null; });
            return accountsRequest;
        }
        async function refreshSettledAccounts() {
            // An in-flight poll may contain a snapshot from before the financial mutation.
            if (accountsRequest) await accountsRequest;
            await refreshAccounts();
        }
        async function loadAccounts() {
            const responses = await Promise.allSettled([request('/api/portfolio'), request('/api/orders')]);
            if (responses[0].status === 'fulfilled') {
                state.portfolio = responses[0].value;
                renderPortfolio();
                ids.portfolioError.hidden = true;
            } else {
                ids.portfolioError.textContent = 'Could not refresh portfolio: ' + responses[0].reason.message;
                ids.portfolioError.hidden = false;
            }
            if (responses[1].status === 'fulfilled' && Array.isArray(responses[1].value)) {
                state.orders = responses[1].value;
                renderOrders();
            } else if (responses[1].status === 'rejected') {
                marketNotice('Recent orders could not be loaded.', true);
            }
        }
        async function loadStocks(preserveSelection) {
            const data = await request('/api/stocks');
            if (!Array.isArray(data)) throw new Error('The server returned an unexpected stock catalog.');
            state.stocks = data.filter(s => s && typeof s.symbol === 'string');
            const previous = state.symbol;
            if (state.stocks.length) {
                const preferred = preserveSelection && state.stocks.some(s => s.symbol === previous) ? previous :
                    state.stocks.some(s => s.symbol === 'INFY') ? 'INFY' : state.stocks[0].symbol;
                if (state.symbol === preferred) {
                    renderMarketList(); renderSelection();
                    await loadHistory();
                } else {
                    selectStock(preferred);
                }
            } else {
                state.symbol = null; state.history = []; ++state.activeHistoryRequest;
                if (historyController) historyController.abort();
                renderMarketList(); renderSelection(); renderChart();
            }
            setText(ids.status, `Simulated stock catalog · ${state.stocks.length} symbols`);
            if (state.stocks.length) clearMarketNotice();
        }
        async function refreshAll() {
            if (refreshInProgress || state.submitting || !state.alive) return;
            refreshInProgress = true;
            ids.refresh.disabled = true;
            try {
                const results = await Promise.allSettled([loadStocks(true), refreshAccounts()]);
                if (results[0].status === 'rejected') {
                    setText(ids.status, 'Market connection unavailable');
                    marketNotice('Could not load simulated stocks: ' + results[0].reason.message, true);
                }
            } finally {
                refreshInProgress = false;
                ids.refresh.disabled = false;
            }
        }
        ids.search.addEventListener('input', () => {
            window.clearTimeout(searchTimer);
            searchTimer = window.setTimeout(renderMarketList, 120);
        });
        ids.orderQty.addEventListener('input', updateEstimate);
        ids.orderForm.addEventListener('submit', submitOrder);
        ids.orderRetry.addEventListener('click', async () => {
            try {
                if (await reconcilePending()) return;
                feedback('No matching order found yet. Retry the exact same order details to reuse its order ID.', false);
            } catch (err) { feedback('Could not verify previous order: ' + err.message, false); }
        });
        document.querySelectorAll('[data-market-side]').forEach(btn => {
            btn.addEventListener('click', () => setSide(btn.dataset.marketSide));
        });
        document.querySelectorAll('[data-market-range]').forEach(btn => {
            btn.addEventListener('click', () => {
                state.range = btn.dataset.marketRange;
                document.querySelectorAll('[data-market-range]').forEach(b => {
                    const active = b.dataset.marketRange === state.range;
                    b.classList.toggle('is-active', active);
                    b.setAttribute('aria-pressed', String(active));
                });
                renderChart();
            });
        });
        ids.refresh.addEventListener('click', refreshAll);
        document.addEventListener('walletchange', refreshSettledAccounts);
        // Profile.js updates wallet after top-up; refresh totals when the user returns or tops up.
        document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshAll(); });
        window.addEventListener('focus', () => { if (!document.hidden) refreshAll(); });
        document.addEventListener('themechange', renderChart);
        setSide('BUY');
        updateEstimate();
        StockSim.currentUser.then(user => {
            if (!user) return;
            pendingStorageKey = 'stocksim.pendingOrder.' + user.id;
            try {
                const pending = JSON.parse(sessionStorage.getItem(pendingStorageKey));
                if (pending && typeof pending.id === 'string' && typeof pending.fingerprint === 'string') savePending(pending);
            } catch (_) { /* No usable pending order. */ }
            state.identityReady = true;
            updateEstimate();
            if (state.pending) feedback('A previous order needs verification. Check previous order before placing another.', false);
            refreshAll();
        });
        let pollId = window.setInterval(() => { if (!document.hidden) refreshAll(); }, POLL_MS);
        window.addEventListener('pagehide', () => { state.alive = false; window.clearInterval(pollId); });
        window.addEventListener('pageshow', event => {
            if (!event.persisted) return;
            state.alive = true;
            pollId = window.setInterval(() => { if (!document.hidden) refreshAll(); }, POLL_MS);
            refreshAll();
        });
    });
}());
