/* StockSim Dashboard Controller — Milestone 1: Dashboard Layout & Market Overview */
document.addEventListener('DOMContentLoaded', function () {
    var inr = (window.StockSim && window.StockSim.inr) || new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
    var formatINR = (window.StockSim && window.StockSim.formatINR) || function (n) { return inr.format(Number(n) || 0); };

    var state = {
        stocks: [],
        selectedSymbol: 'RELIANCE',
        isMock: false
    };

    var els = {
        noticeBar: document.getElementById('market-feed-notice'),
        overviewGrid: document.getElementById('market-overview-grid'),
        watchlist: document.getElementById('watchlist-items'),
        selectedSymbol: document.getElementById('selected-stock-symbol'),
        selectedName: document.getElementById('selected-stock-name'),
        selectedPrice: document.getElementById('selected-stock-price'),
        selectedChange: document.getElementById('selected-stock-change'),
        selectedHigh: document.getElementById('selected-stock-high'),
        selectedLow: document.getElementById('selected-stock-low'),
        selectedSector: document.getElementById('selected-stock-sector'),
        selectedUpdated: document.getElementById('selected-stock-updated')
    };

    function renderOverview(overview) {
        if (!els.overviewGrid || !overview) return;

        var b = overview.benchmark;
        var tg = overview.topGainer;
        var tl = overview.topLoser;
        var ma = overview.mostActive;

        var bUp = b.change >= 0;
        var tgUp = tg.change >= 0;
        var tlUp = tl.change >= 0;
        var maUp = ma.change >= 0;

        els.overviewGrid.innerHTML = [
            '<div class="panel overview-card">',
                '<div class="overview-head"><span class="overview-title">' + b.name + '</span><span class="tag-sim">Simulated</span></div>',
                '<div class="overview-val num">' + b.value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '</div>',
                '<div class="overview-sub num ' + (bUp ? 'is-gain' : 'is-loss') + '">' + (bUp ? '+' : '−') + Math.abs(b.change).toFixed(2) + ' (' + (bUp ? '+' : '−') + Math.abs(b.changePercent).toFixed(2) + '%)</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head"><span class="overview-title">Top Gainer</span><span class="tag-sim">Simulated</span></div>',
                '<div class="overview-split">',
                    '<div><strong class="overview-sym">' + tg.symbol + '</strong><span class="overview-comp">' + tg.companyName + '</span></div>',
                    '<div class="r"><div class="overview-val-sm num">' + formatINR(tg.currentPrice) + '</div><div class="overview-sub num is-gain">+' + Math.abs(tg.changePercent).toFixed(2) + '%</div></div>',
                '</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head"><span class="overview-title">Top Loser</span><span class="tag-sim">Simulated</span></div>',
                '<div class="overview-split">',
                    '<div><strong class="overview-sym">' + tl.symbol + '</strong><span class="overview-comp">' + tl.companyName + '</span></div>',
                    '<div class="r"><div class="overview-val-sm num">' + formatINR(tl.currentPrice) + '</div><div class="overview-sub num is-loss">−' + Math.abs(tl.changePercent).toFixed(2) + '%</div></div>',
                '</div>',
            '</div>',

            '<div class="panel overview-card">',
                '<div class="overview-head"><span class="overview-title">Most Active</span><span class="tag-sim">Simulated</span></div>',
                '<div class="overview-split">',
                    '<div><strong class="overview-sym">' + ma.symbol + '</strong><span class="overview-comp">' + ma.companyName + '</span></div>',
                    '<div class="r"><div class="overview-val-sm num">' + formatINR(ma.currentPrice) + '</div><div class="overview-sub num ' + (maUp ? 'is-gain' : 'is-loss') + '">' + (maUp ? '+' : '−') + Math.abs(ma.changePercent).toFixed(2) + '%</div></div>',
                '</div>',
            '</div>'
        ].join('');
    }

    function renderWatchlist() {
        if (!els.watchlist) return;
        if (!state.stocks || state.stocks.length === 0) {
            els.watchlist.innerHTML = '<div class="empty" style="padding:20px;"><strong>No stocks available</strong><p>Unable to retrieve stock catalog.</p></div>';
            return;
        }

        var html = '';
        state.stocks.forEach(function (stock) {
            var up = stock.change >= 0;
            var isSelected = stock.symbol === state.selectedSymbol;
            html += [
                '<div class="stock-item' + (isSelected ? ' is-active' : '') + '" data-symbol="' + stock.symbol + '" tabindex="0" role="button" aria-pressed="' + isSelected + '">',
                    '<div class="stock-item-info">',
                        '<strong class="stock-item-sym">' + stock.symbol + '</strong>',
                        '<span class="stock-item-name">' + stock.companyName + '</span>',
                    '</div>',
                    '<div class="stock-item-quote r">',
                        '<span class="stock-item-price num">' + formatINR(stock.currentPrice) + '</span>',
                        '<span class="stock-item-change num ' + (up ? 'is-gain' : 'is-loss') + '">' +
                            (up ? '+' : '−') + Math.abs(stock.changePercent).toFixed(2) + '%</span>',
                    '</div>',
                '</div>'
            ].join('');
        });
        els.watchlist.innerHTML = html;

        els.watchlist.querySelectorAll('.stock-item').forEach(function (item) {
            item.addEventListener('click', function () {
                selectStock(item.dataset.symbol);
            });
            item.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectStock(item.dataset.symbol);
                }
            });
        });
    }

    function selectStock(symbol) {
        state.selectedSymbol = symbol;
        renderWatchlist();

        var stock = null;
        for (var i = 0; i < state.stocks.length; i++) {
            if (state.stocks[i].symbol === symbol) {
                stock = state.stocks[i];
                break;
            }
        }
        if (!stock && state.stocks.length > 0) stock = state.stocks[0];
        if (!stock) return;

        var up = stock.change >= 0;
        if (els.selectedSymbol) els.selectedSymbol.textContent = stock.symbol;
        if (els.selectedName) els.selectedName.textContent = stock.companyName;
        if (els.selectedPrice) els.selectedPrice.textContent = formatINR(stock.currentPrice);
        if (els.selectedChange) {
            els.selectedChange.textContent = (up ? '+' : '−') + formatINR(Math.abs(stock.change)) + ' (' + (up ? '+' : '−') + Math.abs(stock.changePercent).toFixed(2) + '%)';
            els.selectedChange.className = 'stock-detail-change num ' + (up ? 'is-gain' : 'is-loss');
        }
        if (els.selectedHigh) els.selectedHigh.textContent = formatINR(stock.high || (stock.currentPrice * 1.015));
        if (els.selectedLow) els.selectedLow.textContent = formatINR(stock.low || (stock.currentPrice * 0.985));
        if (els.selectedSector) els.selectedSector.textContent = stock.sector || 'NSE Equities';
        if (els.selectedUpdated) {
            var now = new Date();
            els.selectedUpdated.textContent = 'Last simulated update: ' + now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        }
    }

    async function init() {
        if (!document.getElementById('dashboard-profile')) return;

        try {
            var api = window.StockSimAPI;
            if (api) {
                var overviewRes = await api.getMarketOverview();
                renderOverview(overviewRes.data);

                var stockRes = await api.getStocks();
                state.stocks = stockRes.stocks || [];
                state.isMock = stockRes.isMock;

                if (els.noticeBar) {
                    if (stockRes.isMock) {
                        els.noticeBar.hidden = false;
                        els.noticeBar.innerHTML = '<span class="status status-build" aria-hidden="true"></span> ' + (stockRes.notice || 'Illustrative simulated market data — backend market feed unavailable.');
                    } else {
                        els.noticeBar.hidden = true;
                    }
                }

                renderWatchlist();
                selectStock(state.selectedSymbol);
            }
        } catch (e) {
            console.error('Error initializing dashboard market view:', e);
            if (els.noticeBar) {
                els.noticeBar.hidden = false;
                els.noticeBar.textContent = 'Failed to load market data: ' + e.message;
            }
        }
    }

    init();
});
