/* Shared page behaviour: mobile nav, server status, landing-page demo terminal. */
var StockSim = window.StockSim || {};

StockSim.inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
StockSim.formatINR = function (n) { return StockSim.inr.format(Number(n) || 0); };
StockSim.signedINR = function (n) {
    var v = Number(n) || 0;
    return (v > 0 ? '+' : v < 0 ? '−' : '') + StockSim.inr.format(Math.abs(v));
};
window.StockSim = StockSim;

document.addEventListener('DOMContentLoaded', function () {

    /* ---------- Mobile nav ---------- */
    var menuBtn = document.querySelector('.menu-btn');
    var nav = document.getElementById('site-nav');
    if (menuBtn && nav) {
        menuBtn.addEventListener('click', function () {
            var open = nav.classList.toggle('open');
            menuBtn.setAttribute('aria-expanded', String(open));
            menuBtn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
        });
        nav.querySelectorAll('a').forEach(function (a) {
            a.addEventListener('click', function () {
                nav.classList.remove('open');
                menuBtn.setAttribute('aria-expanded', 'false');
            });
        });
    }

    /* ---------- Server status ---------- */
    var statusEl = document.getElementById('server-status');
    var dot = document.getElementById('server-dot');
    if (statusEl) {
        fetch('/api/health')
            .then(function (res) { return res.json(); })
            .then(function (data) {
                var up = data && data.status === 'UP';
                statusEl.textContent = up ? 'Server online' : 'Server unavailable';
                dot.className = 'status ' + (up ? 'status-live' : 'status-down');
            })
            .catch(function () {
                statusEl.textContent = 'Server offline';
                dot.className = 'status status-down';
            });
    }

    /* ---------- Demo terminal ---------- */
    var chart = document.getElementById('demo-chart');
    if (!chart) return;

    var W = 560, H = 220, PAD_T = 16, PAD_B = 20, POINTS = 72, START = 1248.5, CASH = 100000;
    var els = {
        price: document.getElementById('demo-price'),
        change: document.getElementById('demo-change'),
        line: document.getElementById('chart-line'),
        area: document.getElementById('chart-area'),
        dot: document.getElementById('chart-dot'),
        open: document.getElementById('chart-open'),
        grid: document.getElementById('chart-grid'),
        axis: document.getElementById('chart-axis'),
        cash: document.getElementById('demo-cash'),
        held: document.getElementById('demo-held'),
        pnl: document.getElementById('demo-pnl'),
        qty: document.getElementById('demo-qty'),
        msg: document.getElementById('demo-msg'),
        form: document.getElementById('demo-ticket')
    };

    function round2(n) { return Math.round(n * 100) / 100; }
    function step(p) {
        var drift = (START - p) * 0.004;              // gentle pull toward start
        var shock = (Math.random() - 0.5) * p * 0.006; // about 0.3% moves
        return Math.max(1, round2(p + drift + shock));
    }

    var series = [START];
    for (var i = 1; i < POINTS; i++) series.push(step(series[i - 1]));
    var sessionOpen = series[0];
    var state = { cash: CASH, shares: 0, avgCost: 0 };

    function cssVar(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }
    function last() { return series[series.length - 1]; }

    function draw() {
        var min = Math.min.apply(null, series.concat([sessionOpen]));
        var max = Math.max.apply(null, series.concat([sessionOpen]));
        var span = Math.max(max - min, START * 0.01);
        min -= span * 0.1; max += span * 0.1;
        var y = function (v) { return PAD_T + (1 - (v - min) / (max - min)) * (H - PAD_T - PAD_B); };
        var x = function (i) { return (i / (POINTS - 1)) * W; };

        var d = series.map(function (v, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); }).join(' ');
        els.line.setAttribute('d', d);
        els.area.setAttribute('d', d + ' L' + W + ' ' + (H - PAD_B) + ' L0 ' + (H - PAD_B) + ' Z');

        var up = last() >= sessionOpen;
        var color = cssVar(up ? '--gain' : '--loss');
        els.line.setAttribute('stroke', color);
        els.area.setAttribute('fill', color);
        els.dot.setAttribute('fill', color);
        els.dot.setAttribute('cx', x(POINTS - 1));
        els.dot.setAttribute('cy', y(last()));
        els.open.setAttribute('y1', y(sessionOpen));
        els.open.setAttribute('y2', y(sessionOpen));

        var grid = '', axis = '';
        for (var g = 0; g < 4; g++) {
            var v = min + ((max - min) * (g + 0.5)) / 4;
            var gy = y(v).toFixed(1);
            grid += '<line x1="0" x2="' + W + '" y1="' + gy + '" y2="' + gy + '"/>';
            axis += '<text x="' + (W + 8) + '" y="' + (Number(gy) + 4) + '">' + Math.round(v).toLocaleString('en-IN') + '</text>';
        }
        els.grid.innerHTML = grid;
        els.axis.innerHTML = axis;

        var diff = last() - sessionOpen;
        var pct = (diff / sessionOpen) * 100;
        els.price.textContent = StockSim.formatINR(last());
        els.change.textContent = StockSim.signedINR(diff) + ' (' + (pct >= 0 ? '+' : '−') + Math.abs(pct).toFixed(2) + '%)';
        els.change.className = 'term-change num ' + (up ? 'is-gain' : 'is-loss');
        renderPosition();
    }

    function qty() {
        var q = parseInt(els.qty.value, 10);
        return isNaN(q) || q < 1 ? 1 : Math.min(q, 9999);
    }

    function renderPosition() {
        els.cash.textContent = StockSim.formatINR(state.cash);
        els.held.textContent = state.shares.toLocaleString('en-IN');
        var pnl = state.shares ? (last() - state.avgCost) * state.shares : 0;
        els.pnl.textContent = StockSim.signedINR(pnl);
        els.pnl.className = pnl > 0 ? 'is-gain' : pnl < 0 ? 'is-loss' : '';
        var cost = document.getElementById('demo-cost');
        if (cost) cost.textContent = StockSim.formatINR(qty() * last());
    }

    function say(text, isError) {
        els.msg.textContent = text;
        els.msg.className = 'ticket-msg' + (isError ? ' is-error' : '');
    }

    var lastSide = 'buy';
    els.form.querySelectorAll('[data-side]').forEach(function (b) {
        b.addEventListener('click', function () { lastSide = b.dataset.side; });
    });
    els.form.addEventListener('submit', function (e) {
        e.preventDefault();
        var side = (e.submitter && e.submitter.dataset.side) || lastSide;
        var q = qty(), p = last(), value = round2(q * p);
        els.qty.value = q;
        if (side === 'buy') {
            if (value > state.cash) {
                var max = Math.floor(state.cash / p);
                return say('Not enough demo cash for ' + q + ' shares. You can afford ' + max + '.', true);
            }
            state.avgCost = (state.avgCost * state.shares + value) / (state.shares + q);
            state.shares += q;
            state.cash = round2(state.cash - value);
            say('Bought ' + q + ' KVRT at ' + StockSim.formatINR(p) + '. Total ' + StockSim.formatINR(value) + '.');
        } else {
            if (q > state.shares) {
                return say(state.shares ? 'You hold only ' + state.shares + ' shares to sell.' : 'You don’t hold any KVRT yet. Buy first, then sell.', true);
            }
            var realised = (p - state.avgCost) * q;
            state.shares -= q;
            state.cash = round2(state.cash + value);
            if (!state.shares) state.avgCost = 0;
            say('Sold ' + q + ' KVRT at ' + StockSim.formatINR(p) + '. Realised ' + StockSim.signedINR(realised) + '.');
        }
        renderPosition();
    });

    els.form.querySelectorAll('[data-step]').forEach(function (b) {
        b.addEventListener('click', function () {
            els.qty.value = Math.max(1, Math.min(9999, qty() + Number(b.dataset.step)));
            renderPosition();
        });
    });
    els.qty.addEventListener('input', renderPosition);

    document.getElementById('demo-reset').addEventListener('click', function () {
        state = { cash: CASH, shares: 0, avgCost: 0 };
        say('Demo reset to ' + StockSim.formatINR(CASH) + '.');
        renderPosition();
    });

    document.addEventListener('themechange', draw);
    draw();

    var timer = setInterval(tick, 2000);
    function tick() {
        series.push(step(last()));
        series.shift();
        draw();
    }
    document.addEventListener('visibilitychange', function () {
        clearInterval(timer);
        if (!document.hidden) timer = setInterval(tick, 2000);
    });
});
