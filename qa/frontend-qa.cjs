/* Browser fixtures test frontend behavior only; live-qa.cjs tests the real server/MySQL. */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const http = require('node:http');
const os = require('node:os');
const modules = process.env.QA_NODE_MODULES || path.join(os.tmpdir(), 'stocksim-qa-tools/node_modules');
const { chromium, firefox } = require(path.join(modules, 'playwright'));
const { default: AxeBuilder } = require(path.join(modules, '@axe-core/playwright'));
const root = path.resolve(__dirname, '..');
const results = [];
const errors = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function main() {
    const server = http.createServer(async (req, res) => {
        try {
            const pathname = new URL(req.url, 'http://localhost').pathname;
            const file = path.resolve(root, 'src/main/resources/static', '.' + pathname);
            assert(file.startsWith(path.join(root, 'src/main/resources/static') + path.sep));
            res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.svg') ? 'image/svg+xml' : 'text/html');
            res.end(await fs.readFile(file));
        } catch { res.statusCode = 404; res.end(); }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    server.unref(); // A failed browser launch must not leave the fixture server running.
    const baseURL = `http://127.0.0.1:${server.address().port}`;
    const engine = process.argv.includes('--firefox') ? 'firefox' : process.argv.includes('--opera') ? 'opera' : (process.env.QA_BROWSER_ENGINE || 'edge');
    const executablePath = process.env.QA_BROWSER || (engine === 'opera' ? path.join(os.homedir(), 'AppData/Local/Programs/Opera GX/opera.exe') : 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe');
    const browser = engine === 'firefox' ? await firefox.launch({ headless: true, timeout: 15000 }) : await chromium.launch({ executablePath, headless: true, timeout: 15000 });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    await page.clock.install();
    page.on('pageerror', e => errors.push(e.message));
    const consoleErrors = [];
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    let user = { id: 900001, publicUserId: 900001, username: 'qa_fixture', name: 'QA Fixture', email: 'qa@example.invalid', virtualBalance: 100000, createdAt: '2026-11-18', emailVerified: true };
    const stock = (symbol, currentPrice) => ({ symbol, companyName: symbol === 'INFY' ? 'Infosys Limited' : 'Tata Consultancy Services', currentPrice, change: 10, changePercent: .67, lastUpdated: new Date().toISOString() });
    let stocks = [stock('INFY', 1500), stock('TCS', 3000)];
    let portfolio = { walletBalance: 100000, totalInvestedValue: 0, totalPnl: 0, totalAccountEquity: 100000, totalRealizedPnl: 0, totalUnrealizedPnl: 0, holdings: [] };
    let orders = [], calls = [], orderFailure = null, topupFailure = false, assistantFailure = false, historyDelay = false, failedLogout = false, offline = false, slowPortfolio = false, tradeUpdatesPortfolio = false;
    await page.route('https://**', route => route.abort()); // deterministic offline fonts
    await page.route('**/api/**', async route => {
        const request = route.request(), pathname = new URL(request.url()).pathname;
        const body = request.postDataJSON();
        calls.push({ pathname, method: request.method(), body, key: request.headers()['idempotency-key'] });
        if (offline) return route.abort();
        if (pathname === '/api/user/me') return route.fulfill({ json: user });
        if (pathname === '/api/health') return route.fulfill({ json: { status: 'UP' } });
        if (pathname === '/api/stocks') return route.fulfill({ json: stocks });
        if (pathname === '/api/portfolio') {
            const snapshot = structuredClone(portfolio);
            if (slowPortfolio) await delay(400);
            return route.fulfill({ json: snapshot });
        }
        if (pathname === '/api/orders') return route.fulfill({ json: orders });
        if (pathname.endsWith('/history')) {
            const infy = pathname.includes('/INFY/');
            if (historyDelay && infy) await delay(250);
            return route.fulfill({ json: [{ price: infy ? 1500 : 3000, recordedAt: new Date().toISOString() }] }).catch(() => {});
        }
        if (pathname.startsWith('/api/orders/')) {
            await delay(100);
            if (orderFailure === 'network') return route.abort();
            if (orderFailure === '422') return route.fulfill({ status: 422, json: { error: 'Insufficient funds' } });
            const order = { id: orders.length + 1, ...body, side: pathname.endsWith('buy') ? 'BUY' : 'SELL', status: 'EXECUTED', executionPrice: 1500, createdAt: new Date().toISOString() };
            orders.push(order);
            if (tradeUpdatesPortfolio) portfolio = { ...portfolio, walletBalance: 95000 };
            return route.fulfill({ status: 201, json: order });
        }
        if (pathname === '/api/payments/top-up') {
            await delay(100);
            if (topupFailure) return route.abort();
            portfolio = { ...portfolio, walletBalance: portfolio.walletBalance + body.amount };
            user = { ...user, virtualBalance: portfolio.walletBalance };
            return route.fulfill({ json: { status: 'SUCCESS', amount: body.amount } });
        }
        if (pathname === '/api/assistant/chat') {
            if (assistantFailure) return route.fulfill({ status: 429, json: { error: 'LLM_RATE_LIMIT_ERROR', message: 'Try again shortly.' } });
            return route.fulfill({ json: { success: true, conversationId: 'fixture-conversation', message: '<img src=x onerror=alert(1)>', toolsUsed: ['get_portfolio'] } });
        }
        if (pathname === '/api/auth/logout') return route.fulfill({ status: failedLogout ? 500 : 200, body: '' });
        return route.fulfill({ status: 500, json: { error: 'Fixture does not support this endpoint' } });
    });
    await page.addInitScript(() => {
        window.qaMetrics = { formatters: 0, mutations: 0 };
        const Native = Intl.NumberFormat;
        Intl.NumberFormat = new Proxy(Native, { construct(target, args) { window.qaMetrics.formatters++; return Reflect.construct(target, args); } });
        document.addEventListener('DOMContentLoaded', () => {
            new MutationObserver(records => window.qaMetrics.mutations += records.length).observe(document.getElementById('market-workspace'), { subtree: true, childList: true, attributes: true, characterData: true });
        });
    });
    async function ready() {
        await page.waitForFunction(() => document.getElementById('market-selected-symbol').textContent === 'INFY' && !document.getElementById('trade-order-submit').disabled && !document.getElementById('trading-refresh').disabled);
    }
    async function test(name, fn) {
        try { await fn(); results.push({ name, status: 'PASS' }); }
        catch (error) { results.push({ name, status: 'FAIL', error: error.message }); }
    }
    await page.goto(baseURL + '/dashboard.html'); await ready();
    const initial = await page.evaluate(() => window.qaMetrics);
    await test('Single profile fetch; genuine empty portfolio; no initial focus theft', async () => {
        assert.equal(calls.filter(c => c.pathname === '/api/user/me').length, 1);
        assert(await page.locator('#live-holdings-empty').isVisible());
        assert.notEqual(await page.evaluate(() => document.activeElement.id), 'assistant-input');
    });
    await test('Five refreshes finish without formatter recreation', async () => {
        for (let i = 0; i < 5; i++) { await page.locator('#trading-refresh').click(); await ready(); }
        assert.equal((await page.evaluate(() => window.qaMetrics)).formatters, initial.formatters);
    });
    const afterFiveRefreshes = await page.evaluate(() => window.qaMetrics);
    await test('Confirmed top-up refreshes portfolio promptly; programmatic double submit sends once', async () => {
        await page.locator('#topup-amount').fill('1000');
        const before = calls.filter(c => c.pathname === '/api/portfolio').length;
        await page.locator('#topup-form').evaluate(form => { form.dispatchEvent(new Event('submit', { cancelable: true })); form.dispatchEvent(new Event('submit', { cancelable: true })); });
        await page.waitForFunction(() => document.getElementById('user-balance').textContent.includes('1,01,000'));
        assert(calls.filter(c => c.pathname === '/api/portfolio').length > before);
        assert.equal(calls.filter(c => c.pathname === '/api/payments/top-up').length, 1);
    });
    await test('BUY cancellation sends nothing; confirmation sends one UUID and no client price', async () => {
        page.once('dialog', dialog => dialog.dismiss()); await page.locator('#trade-order-submit').click();
        assert.equal(calls.filter(c => c.pathname === '/api/orders/buy').length, 0);
        page.once('dialog', dialog => dialog.accept());
        await page.locator('#trade-order-form').evaluate(form => { form.dispatchEvent(new Event('submit', { cancelable: true })); form.dispatchEvent(new Event('submit', { cancelable: true })); });
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.includes('EXECUTED'));
        const posts = calls.filter(c => c.pathname === '/api/orders/buy'); assert.equal(posts.length, 1);
        assert.match(posts[0].body.clientOrderId, /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i);
        assert.deepEqual(Object.keys(posts[0].body).sort(), ['clientOrderId', 'quantity', 'symbol']);
    });
    await test('422 preserves pending UUID over reload and reconciles without resubmission', async () => {
        await ready(); orderFailure = '422'; page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.locator('#trade-order-reconcile').waitFor({ state: 'visible' });
        const pending = await page.evaluate(() => JSON.parse(sessionStorage.getItem('stocksim.pendingOrder.900001')));
        assert(pending.id); await page.reload(); await ready();
        assert(await page.locator('#trade-order-reconcile').isVisible());
        const count = calls.filter(c => c.pathname === '/api/orders/buy').length;
        orders.push({ id: 2, clientOrderId: pending.id, side: 'BUY', symbol: 'INFY', quantity: 1, status: 'PENDING' });
        await page.locator('#trade-order-reconcile').click();
        await page.locator('#trade-order-reconcile').waitFor({ state: 'hidden' });
        assert.equal(calls.filter(c => c.pathname === '/api/orders/buy').length, count);
        assert.equal(await page.evaluate(() => sessionStorage.getItem('stocksim.pendingOrder.900001')), null);
        orderFailure = null;
    });
    await test('Ambiguous network failure reuses UUID; changed order cannot discard it', async () => {
        orderFailure = 'network'; page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.locator('#trade-order-reconcile').waitFor({ state: 'visible' });
        const original = calls.filter(c => c.pathname === '/api/orders/buy').at(-1).body.clientOrderId;
        await page.locator('#trade-order-quantity').fill('2'); page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.includes('unresolved'));
        await page.locator('#trade-order-quantity').fill('1'); orderFailure = null;
        page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.includes('EXECUTED'));
        assert.equal(calls.filter(c => c.pathname === '/api/orders/buy').at(-1).body.clientOrderId, original);
    });
    await test('Ambiguous top-up cannot change amount; same-amount retry preserves key', async () => {
        topupFailure = true; await page.locator('#topup-amount').fill('200'); await page.locator('#topup-form button').click();
        await page.waitForFunction(() => document.getElementById('tools-alert').textContent.includes('lost connection'));
        const original = calls.filter(c => c.pathname === '/api/payments/top-up').at(-1).key;
        const count = calls.filter(c => c.pathname === '/api/payments/top-up').length;
        await page.locator('#topup-amount').fill('300'); await page.locator('#topup-form button').click();
        await page.waitForFunction(() => document.getElementById('tools-alert').textContent.includes('unresolved'));
        assert.equal(calls.filter(c => c.pathname === '/api/payments/top-up').length, count);
        topupFailure = false; await page.locator('#topup-amount').fill('200'); await page.locator('#topup-form button').click();
        await page.waitForFunction(() => document.getElementById('tools-alert').textContent.includes('added'));
        assert.equal(calls.filter(c => c.pathname === '/api/payments/top-up').at(-1).key, original);
    });
    await test('Fast stock selection cannot display an older chart response', async () => {
        historyDelay = true; await page.locator('#trading-refresh').click(); await page.locator('[data-symbol=TCS]').click();
        await page.waitForFunction(() => document.getElementById('market-chart-low').textContent.includes('3,000'));
        await delay(300); assert.equal(await page.locator('#market-selected-symbol').textContent(), 'TCS');
        assert((await page.locator('#market-chart-low').textContent()).includes('3,000'));
        historyDelay = false;
    });
    await test('AI output is text; rate-limit feedback recovers; chat never submits a trade', async () => {
        const before = calls.filter(c => c.pathname.startsWith('/api/orders/') && c.method === 'POST').length;
        await page.locator('#assistant-input').fill('Preview buying 5 INFY'); await page.locator('#assistant-send').click();
        await page.waitForFunction(() => document.querySelectorAll('.chat-bubble-assistant').length > 1);
        assert.equal(await page.locator('#assistant-messages img').count(), 0);
        assistantFailure = true; await page.locator('#assistant-input').fill('Wallet'); await page.locator('#assistant-send').click();
        await page.locator('#assistant-error').waitFor({ state: 'visible' }); assert.equal(await page.locator('#assistant-send').isEnabled(), true);
        assert.equal(calls.filter(c => c.pathname.startsWith('/api/orders/') && c.method === 'POST').length, before);
    });
    await test('Failed logout keeps dashboard open and reports failure', async () => {
        failedLogout = true; await page.locator('#logout-btn').click();
        await page.waitForFunction(() => document.getElementById('tools-alert').textContent.includes('Sign out failed'));
        assert(page.url().includes('/dashboard.html'));
    });
    await test('New account cannot inherit another user pending top-up or order', async () => {
        await page.evaluate(() => {
            sessionStorage.setItem('stocksim.pendingOrder.900001', JSON.stringify({ id: crypto.randomUUID(), fingerprint: 'BUY|INFY|1' }));
            sessionStorage.setItem('stocksim.pendingTopUp.900001', JSON.stringify({ key: crypto.randomUUID(), amount: '200.00' }));
        });
        user = { ...user, id: 900002 }; await page.reload(); await ready();
        assert.equal(await page.locator('#trade-order-reconcile').isVisible(), false);
        await page.locator('#topup-amount').fill('400'); await page.locator('#topup-form button').click();
        await page.waitForFunction(() => document.getElementById('tools-alert').textContent.includes('added'));
    });
    await test('Invalid trading quantities never issue a POST', async () => {
        const count = calls.filter(c => c.pathname.startsWith('/api/orders/') && c.method === 'POST').length;
        for (const value of ['0', '-1', '1.5', '100001']) {
            await page.locator('#trade-order-quantity').fill(value);
            await page.locator('#trade-order-form').evaluate(form => form.dispatchEvent(new Event('submit', { cancelable: true })));
        }
        assert.equal(calls.filter(c => c.pathname.startsWith('/api/orders/') && c.method === 'POST').length, count);
        await page.locator('#trade-order-quantity').fill('1');
    });
    await test('SELL cancellation sends nothing; confirmed SELL sends once', async () => {
        await page.locator('[data-market-side=SELL]').click();
        const count = calls.filter(c => c.pathname === '/api/orders/sell').length;
        page.once('dialog', dialog => dialog.dismiss()); await page.locator('#trade-order-submit').click();
        assert.equal(calls.filter(c => c.pathname === '/api/orders/sell').length, count);
        page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.startsWith('SELL 1 INFY: EXECUTED'));
        assert.equal(calls.filter(c => c.pathname === '/api/orders/sell').length, count + 1);
    });
    await test('Trade completion refreshes again after an older portfolio poll finishes', async () => {
        await ready(); slowPortfolio = true; tradeUpdatesPortfolio = true;
        const before = calls.filter(c => c.pathname === '/api/portfolio').length;
        await page.locator('#trading-refresh').click();
        await page.waitForTimeout(40); // ensure the pre-trade snapshot is in flight
        page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-balance').textContent.includes('95,000'));
        assert(calls.filter(c => c.pathname === '/api/portfolio').length >= before + 2);
        slowPortfolio = false; tradeUpdatesPortfolio = false;
    });
    await test('Duplicate refresh and timer tick cannot overlap a slow refresh', async () => {
        await ready(); historyDelay = true;
        const before = calls.filter(c => c.pathname === '/api/stocks').length;
        await page.locator('#trading-refresh').evaluate(button => { button.click(); button.dispatchEvent(new Event('click')); });
        await page.clock.fastForward(16000); await ready();
        assert.equal(calls.filter(c => c.pathname === '/api/stocks').length, before + 1);
        historyDelay = false;
    });
    await test('Hidden tab polling pauses; visible tab and BFCache restore polling', async () => {
        await page.evaluate(() => {
            window.qaHidden = true;
            Object.defineProperty(document, 'hidden', { configurable: true, get: () => window.qaHidden });
            document.dispatchEvent(new Event('visibilitychange'));
        });
        const before = calls.filter(c => c.pathname === '/api/stocks').length;
        await page.clock.fastForward(45000);
        assert.equal(calls.filter(c => c.pathname === '/api/stocks').length, before);
        await page.evaluate(() => { window.qaHidden = false; document.dispatchEvent(new Event('visibilitychange')); }); await ready();
        assert.equal(calls.filter(c => c.pathname === '/api/stocks').length, before + 1);
        await page.evaluate(() => { window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })); window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })); }); await ready();
        const restored = calls.filter(c => c.pathname === '/api/stocks').length;
        await page.clock.fastForward(16000); await ready();
        assert.equal(calls.filter(c => c.pathname === '/api/stocks').length, restored + 1);
    });
    await test('Disconnected APIs show errors and recover after reconnect', async () => {
        offline = true; await page.locator('#trading-refresh').click(); await ready();
        assert(await page.locator('#portfolio-loading-error').isVisible()); assert(await page.locator('#market-banner').isVisible());
        offline = false; await page.locator('#trading-refresh').click(); await ready();
        assert.equal(await page.locator('#portfolio-loading-error').isVisible(), false);
    });
    stocks = stocks.map(s => ({ ...s, companyName: 'LongCompanyName'.repeat(8), currentPrice: 9999999999.99 }));
    portfolio = { ...portfolio, walletBalance: 999999999999.99, totalInvestedValue: 99999999999.99, totalAccountEquity: 999999999999.99,
        holdings: [{ symbol: 'INFY', companyName: stocks[0].companyName, quantity: 100000, averageCost: 9999999999.99, unrealizedPnl: -9999999999.99 }] };
    await page.locator('#trading-refresh').click(); await ready();
    const layouts = [], accessibility = [];
    for (const theme of ['light', 'dark']) {
        for (const width of [1440, 1280, 768, 390, 360]) {
            await page.setViewportSize({ width, height: 1000 });
            await page.evaluate(theme => { document.documentElement.dataset.theme = theme; document.dispatchEvent(new Event('themechange')); }, theme);
            const measurement = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth }));
            layouts.push({ theme, ...measurement });
            await test(`${theme} ${width}px: no page overflow`, async () => assert(measurement.scrollWidth <= width));
            if ([1440, 360].includes(width)) await page.screenshot({ path: path.join(__dirname, `screenshots/fixture-${engine}-${theme}-${width}.png`), fullPage: true });
        }
        const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
        accessibility.push({ theme, violations: axe.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.map(n => n.target) })) });
        await test(`${theme}: axe WCAG A/AA has no violations`, async () => assert.deepEqual(axe.violations.map(v => v.id), []));
    }
    await test('Mobile menu Escape restores focus; theme toggle persists over reload', async () => {
        await page.locator('.menu-btn').click();
        await page.locator('#site-nav a').first().focus(); await page.keyboard.press('Escape');
        assert.equal(await page.locator('.menu-btn').getAttribute('aria-expanded'), 'false');
        assert.equal(await page.evaluate(() => document.activeElement.classList.contains('menu-btn')), true);
        await page.locator('[data-theme-toggle]').click();
        const theme = await page.evaluate(() => document.documentElement.dataset.theme);
        await page.reload(); await ready();
        assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), theme);
    });
    await test('No uncaught JavaScript exceptions', async () => assert.deepEqual(errors, []));
    const report = { engine, browser: await browser.version(), fixtureOnly: true, initial, afterFiveRefreshes, results, layouts, accessibility, uncaughtExceptions: errors, consoleErrors };
    await fs.writeFile(path.join(__dirname, `frontend-${engine}-results.json`), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
    await browser.close(); await new Promise(resolve => server.close(resolve));
    if (results.some(r => r.status === 'FAIL')) process.exitCode = 1;
}
main().catch(error => { console.error(error); process.exitCode = 1; });
