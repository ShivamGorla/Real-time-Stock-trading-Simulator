/* Run only against the authorized disposable schema. Never resets or migrates data. */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const modules = process.env.QA_NODE_MODULES || path.join(os.tmpdir(), 'stocksim-qa-tools/node_modules');
const { chromium } = require(path.join(modules, 'playwright'));
const baseURL = process.env.QA_BASE_URL || 'http://127.0.0.1:18080';
const db = new URL((process.env.DB_URL || '').replace(/^jdbc:/, ''));
assert.equal(db.pathname, '/stocksim_smoke_test', 'Only the authorized disposable schema is allowed');
const username = 'qa_' + Date.now() + '_' + crypto.randomBytes(3).toString('hex');
const password = 'Qa9!' + crypto.randomBytes(16).toString('hex');
const results = [], httpResponses = [], exceptions = [], consoleErrors = [];
const money = value => Math.round(Number(value) * 100);
function sql(query) {
    return new Promise((resolve, reject) => {
        const child = spawn(process.env.QA_MYSQL || 'C:/Program Files/MySQL/MySQL Server 26.7/bin/mysql.exe', [
            '--host=' + db.hostname, '--port=' + (db.port || '3306'), '--user=' + process.env.DB_USERNAME,
            '--database=stocksim_smoke_test', '--batch', '--skip-column-names', '-e', query
        ], { windowsHide: true, env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD } });
        let out = '', err = '';
        child.stdout.on('data', data => out += data); child.stderr.on('data', data => err += data);
        child.on('exit', code => code === 0 ? resolve(out.trim()) : reject(new Error('MySQL check failed: ' + err)));
    });
}
async function csrf(context) {
    return decodeURIComponent((await context.cookies(baseURL)).find(cookie => cookie.name === 'XSRF-TOKEN')?.value || '');
}
async function post(context, endpoint, data, extraHeaders = {}) {
    return context.request.post(baseURL + endpoint, { data, headers: { 'X-XSRF-TOKEN': await csrf(context), ...extraHeaders } });
}
async function main() {
    const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
    const page = await context.newPage();
    page.on('pageerror', error => exceptions.push(error.message));
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    page.on('response', response => { const url = new URL(response.url()); if (url.pathname.startsWith('/api/')) httpResponses.push({ method: response.request().method(), path: url.pathname, status: response.status() }); });
    async function test(name, fn) {
        try { const evidence = await fn(); results.push({ name, status: 'PASS', ...(evidence ? { evidence } : {}) }); }
        catch (error) { results.push({ name, status: 'FAIL', error: error.message }); throw error; }
    }
    let userId, buy, sell;
    const report = { baseURL, database: db.pathname.slice(1), results, httpResponses, exceptions, consoleErrors };
    try {
        await test('Real browser registration, login, session and initial wallet', async () => {
            await page.goto(baseURL + '/register.html');
            await page.locator('#name').fill('StockSim Disposable QA'); await page.locator('#username').fill(username);
            await page.locator('#email').fill(username + '@example.invalid'); await page.locator('#password').fill(password); await page.locator('#confirmPassword').fill(password);
            await page.locator('#register-form button[type=submit]').click(); await page.waitForURL('**/login.html?registered=true');
            await page.locator('#username').fill(username); await page.locator('#password').fill(password); await page.locator('#login-form button[type=submit]').click();
            await page.waitForURL('**/dashboard.html'); await page.waitForFunction(() => document.getElementById('user-balance').textContent.includes('1,00,000'));
            const me = await (await context.request.get(baseURL + '/api/user/me')).json(); userId = me.id;
            assert.equal(money(me.virtualBalance), 10000000);
            assert.equal(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`), '100000.00');
            assert.equal(await sql(`SELECT COUNT(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=${userId} AND category='INITIAL_BALANCE'`), '1');
            return { disposableTestAccount: true, initialBalance: 100000, initialLedgerRows: 1 };
        });
        await test('Stock search, live chart, genuine empty portfolio and keyboard access', async () => {
            await page.locator('#market-search').fill('INFY');
            await page.waitForFunction(() => document.querySelectorAll('.market-stock-row').length === 1);
            await page.locator('[data-symbol=INFY]').waitFor();
            assert.equal(await page.locator('.market-stock-row').count(), 1);
            await page.locator('[data-symbol=INFY]').click();
            await page.waitForFunction(() => document.getElementById('market-selected-symbol').textContent === 'INFY');
            assert(await page.locator('#live-holdings-empty').isVisible());
            await page.locator('#market-search').focus(); await page.keyboard.press('Tab');
            assert.equal(await page.evaluate(() => document.activeElement.dataset.symbol), 'INFY');
            await page.screenshot({ path: path.join(__dirname, 'screenshots/live-empty-desktop.png'), fullPage: true });
        });
        await test('Browser top-up credits wallet and exactly one ledger/payment row', async () => {
            await page.locator('#topup-amount').fill('1000'); await page.locator('#topup-form button[type=submit]').click();
            await page.waitForFunction(() => document.getElementById('trade-order-balance').textContent.includes('1,01,000'));
            assert.equal(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`), '101000.00');
            assert.equal(await sql(`SELECT COUNT(*) FROM payment_transactions WHERE user_id=${userId} AND status='SUCCESS'`), '1');
            assert.equal(await sql(`SELECT COUNT(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=${userId} AND category='TOP_UP'`), '1');
        });
        await test('BUY cancellation and confirmed BUY INFY; holdings/WAC/equity reconcile with SQL', async () => {
            await page.locator('#trade-order-quantity').fill('2'); page.once('dialog', dialog => dialog.dismiss()); await page.locator('#trade-order-submit').click();
            assert.equal(await sql(`SELECT COUNT(*) FROM orders WHERE user_id=${userId}`), '0');
            page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
            await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.includes('EXECUTED'));
            const orders = await (await context.request.get(baseURL + '/api/orders')).json(); buy = orders.find(order => order.side === 'BUY');
            assert.equal(buy.status, 'EXECUTED');
            const portfolio = await (await context.request.get(baseURL + '/api/portfolio')).json();
            assert.equal(portfolio.holdings[0].quantity, 2); assert.equal(money(portfolio.holdings[0].averageCost), money(buy.executionPrice));
            assert.equal(money(portfolio.walletBalance), 10100000 - 2 * money(buy.executionPrice));
            assert.equal(money(portfolio.totalAccountEquity), money(portfolio.walletBalance) + money(portfolio.totalCurrentMarketValue));
            assert.equal(money(portfolio.totalUnrealizedPnl), money(portfolio.totalCurrentMarketValue) - money(portfolio.totalInvestedValue));
            assert.equal(await sql(`SELECT COUNT(*) FROM trades WHERE user_id=${userId}`), '1');
            assert.equal(await sql(`SELECT quantity FROM holdings WHERE user_id=${userId}`), '2');
            return { executionPrice: buy.executionPrice, walletBalance: portfolio.walletBalance, quantity: 2 };
        });
        await test('Confirmed SELL INFY updates realized/unrealized P&L and SQL balance', async () => {
            await page.locator('[data-market-side=SELL]').click(); await page.locator('#trade-order-quantity').fill('1');
            page.once('dialog', dialog => dialog.accept()); await page.locator('#trade-order-submit').click();
            await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.startsWith('SELL 1 INFY: EXECUTED'));
            sell = (await (await context.request.get(baseURL + '/api/orders')).json()).find(order => order.side === 'SELL');
            const portfolio = await (await context.request.get(baseURL + '/api/portfolio')).json();
            assert.equal(portfolio.holdings[0].quantity, 1);
            assert.equal(money(portfolio.totalRealizedPnl), money(sell.executionPrice) - money(buy.executionPrice));
            assert.equal(money(portfolio.walletBalance), 10100000 - 2 * money(buy.executionPrice) + money(sell.executionPrice));
            assert.equal(money(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`)), money(portfolio.walletBalance));
            assert.equal(money(await sql(`SELECT total_realized_pnl FROM holdings WHERE user_id=${userId}`)), money(portfolio.totalRealizedPnl));
            assert.equal(await page.locator('#live-orders-body tr').count(), 2);
            await page.screenshot({ path: path.join(__dirname, 'screenshots/live-holdings-desktop.png'), fullPage: true });
            await page.setViewportSize({ width: 360, height: 900 });
            await page.locator('[data-theme-toggle]').click();
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
            await page.screenshot({ path: path.join(__dirname, 'screenshots/live-holdings-mobile.png'), fullPage: true });
            await page.locator('[data-theme-toggle]').click(); await page.setViewportSize({ width: 1440, height: 1000 });
            return { executionPrice: sell.executionPrice, walletBalance: portfolio.walletBalance, realizedPnl: portfolio.totalRealizedPnl, unrealizedPnl: portfolio.totalUnrealizedPnl };
        });
        await test('All eight report downloads have correct content, headers and browser delivery', async () => {
            const evidence = [];
            for (const report of ['profile', 'transactions', 'trading-history', 'portfolio']) for (const format of ['csv', 'pdf']) {
                const endpoint = `/api/reports/${report}?format=${format}`;
                const response = await context.request.get(baseURL + endpoint); assert.equal(response.status(), 200);
                const body = await response.body(); assert(body.length > 20); assert(response.headers()['content-disposition'].includes('attachment'));
                if (format === 'pdf') assert.equal(body.subarray(0, 5).toString(), '%PDF-'); else assert(response.headers()['content-type'].includes('text/csv'));
                const downloadPromise = page.waitForEvent('download'); await page.locator(`a[href="${endpoint}"]`).click(); const download = await downloadPromise;
                assert.equal(await download.failure(), null); assert(download.suggestedFilename().endsWith('.' + format));
                evidence.push({ report, format, bytes: body.length, status: response.status() });
            }
            return evidence;
        });
        await test('Real HTTP CSRF, duplicates, invalid quantity, rollback, idempotent top-up and pagination', async () => {
            const beforeTrades = await sql(`SELECT COUNT(*) FROM trades WHERE user_id=${userId}`);
            assert.equal((await context.request.post(baseURL + '/api/orders/buy', { data: { clientOrderId: crypto.randomUUID(), symbol: 'INFY', quantity: 1 } })).status(), 403);
            assert.equal((await post(context, '/api/orders/buy', { clientOrderId: buy.clientOrderId, symbol: 'INFY', quantity: 2 })).status(), 409);
            assert.equal((await post(context, '/api/orders/buy', { clientOrderId: crypto.randomUUID(), symbol: 'INFY', quantity: 0 })).status(), 400);
            const beforeBalance = await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`);
            assert.equal((await post(context, '/api/orders/buy', { clientOrderId: crypto.randomUUID(), symbol: 'INFY', quantity: 100000 })).status(), 422);
            assert.equal((await post(context, '/api/orders/sell', { clientOrderId: crypto.randomUUID(), symbol: 'INFY', quantity: 2 })).status(), 422);
            assert.equal(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`), beforeBalance);
            assert.equal(await sql(`SELECT COUNT(*) FROM trades WHERE user_id=${userId}`), beforeTrades);
            const key = crypto.randomUUID();
            const top1 = await (await post(context, '/api/payments/top-up', { amount: 100 }, { 'Idempotency-Key': key })).json();
            const top2 = await (await post(context, '/api/payments/top-up', { amount: 100 }, { 'Idempotency-Key': key })).json();
            assert.equal(top1.id, top2.id);
            assert.equal(money(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`)), money(beforeBalance) + 10000);
            assert.equal(await sql(`SELECT COUNT(*) FROM wallet_transactions WHERE reference_id='TOPUP-${top1.id}'`), '1');
            const trades = await (await context.request.get(baseURL + '/api/trades?page=0&size=1')).json(); assert.equal(trades.content.length, 1); assert.equal(trades.totalElements, 2);
        });
        await test('Authenticated cross-user isolation and unauthenticated report denial', async () => {
            const other = await browser.newContext(); await other.request.get(baseURL + '/login.html');
            for (const endpoint of ['/api/user/me', '/api/wallet', '/api/portfolio', '/api/orders', '/api/reports/profile?format=pdf']) assert.equal((await other.request.get(baseURL + endpoint)).status(), 401);
            const otherName = username + '_b';
            assert.equal((await post(other, '/api/auth/register', { name: 'Second Disposable QA', username: otherName, email: otherName + '@example.invalid', password, confirmPassword: password })).status(), 201);
            const login = await other.request.post(baseURL + '/api/auth/login', { form: { username: otherName, password }, headers: { 'X-XSRF-TOKEN': await csrf(other) } }); assert.equal(login.status(), 200);
            await other.request.get(baseURL + '/dashboard.html');
            assert.equal((await other.request.get(baseURL + '/api/orders/' + buy.id)).status(), 404);
            assert.deepEqual(await (await other.request.get(baseURL + '/api/orders')).json(), []);
            assert.equal(money((await (await other.request.get(baseURL + '/api/wallet')).json()).balance), 10000000);
            await other.close();
        });
        await test('Live MySQL concurrent same-key and distinct-key top-ups credit exactly once', async () => {
            const starting = money(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`));
            const sameKey = crypto.randomUUID();
            const outcomes = await Promise.all([post(context, '/api/payments/top-up', { amount: 150 }, { 'Idempotency-Key': sameKey }), post(context, '/api/payments/top-up', { amount: 150 }, { 'Idempotency-Key': sameKey })]);
            for (const response of outcomes) assert([200, 409, 503].includes(response.status()));
            assert.equal((await post(context, '/api/payments/top-up', { amount: 150 }, { 'Idempotency-Key': sameKey })).status(), 200);
            const keys = [crypto.randomUUID(), crypto.randomUUID()];
            const concurrent = await Promise.all(keys.map(key => post(context, '/api/payments/top-up', { amount: 100 }, { 'Idempotency-Key': key })));
            for (let i = 0; i < concurrent.length; i++) {
                assert([200, 409, 503].includes(concurrent[i].status()));
                assert.equal((await post(context, '/api/payments/top-up', { amount: 100 }, { 'Idempotency-Key': keys[i] })).status(), 200);
            }
            assert.equal(money(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`)), starting + 35000);
            const declined = await (await post(context, '/api/payments/top-up', { amount: 666.66 }, { 'Idempotency-Key': crypto.randomUUID() })).json();
            assert.equal(declined.status, 'FAILED');
            assert.equal(money(await sql(`SELECT balance FROM wallets WHERE user_id=${userId}`)), starting + 35000);
            assert.equal(await sql(`SELECT COUNT(*) FROM (SELECT wt.reference_id FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=${userId} GROUP BY wt.reference_id HAVING COUNT(*)>1) duplicates`), '0');
            return { concurrentCredits: 350, sameKeyReplay: 'one credit', duplicateLedgerReferences: 0, statuses: [...outcomes, ...concurrent].map(r => r.status()) };
        });
        const snapshot = () => sql(`SELECT w.balance,w.version,(SELECT COUNT(*) FROM orders WHERE user_id=${userId}),(SELECT COUNT(*) FROM trades WHERE user_id=${userId}),(SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=w.id),(SELECT COUNT(*) FROM payment_transactions WHERE user_id=${userId}),(SELECT SUM(quantity) FROM holdings WHERE user_id=${userId}) FROM wallets w WHERE w.user_id=${userId}`);
        const beforeAI = await snapshot();
        await page.locator('#assistant-input').fill('Check my wallet, holdings, portfolio, INFY stock information, recent orders, and preview buying one INFY share. Do not execute any trade.');
        const aiResponsePromise = page.waitForResponse(response => new URL(response.url()).pathname === '/api/assistant/chat', { timeout: 90000 });
        await page.locator('#assistant-send').click();
        const aiResponse = await aiResponsePromise; const ai = await aiResponse.json();
        assert.equal(await snapshot(), beforeAI, 'AI must not mutate financial data');
        report.ai = { status: ai.success ? 'PASS' : 'BLOCKED', httpStatus: aiResponse.status(), errorCode: ai.errorCode || ai.error, toolsUsed: ai.toolsUsed, financialStateUnchanged: true };
        if (!ai.success) report.ai.reason = 'Live provider did not return a successful answer; no live AI success claimed.';
        await test('Profile update and password change use authenticated session; relogin succeeds', async () => {
            const update = await context.request.put(baseURL + '/api/user/me', { data: { name: 'Disposable QA Updated' }, headers: { 'X-XSRF-TOKEN': await csrf(context) } }); assert.equal(update.status(), 200);
            const newPassword = 'Qa8!' + crypto.randomBytes(16).toString('hex');
            const change = await context.request.put(baseURL + '/api/user/me/password', { data: { currentPassword: password, newPassword, confirmNewPassword: newPassword }, headers: { 'X-XSRF-TOKEN': await csrf(context) } }); assert.equal(change.status(), 204);
            await page.locator('#logout-btn').click(); await page.waitForURL(baseURL + '/');
            assert.equal((await context.request.get(baseURL + '/api/user/me')).status(), 401);
            await page.goto(baseURL + '/login.html'); await page.locator('#username').fill(username); await page.locator('#password').fill(newPassword); await page.locator('#login-form button[type=submit]').click(); await page.waitForURL('**/dashboard.html');
            await page.locator('#logout-btn').click(); await page.waitForURL(baseURL + '/');
        });
        assert.deepEqual(exceptions, []);
        report.sqlFinal = await sql(`SELECT w.balance,h.quantity,h.average_cost,h.total_realized_pnl,(SELECT COUNT(*) FROM orders WHERE user_id=${userId}),(SELECT COUNT(*) FROM trades WHERE user_id=${userId}),(SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=w.id) FROM wallets w JOIN holdings h ON h.user_id=w.user_id WHERE w.user_id=${userId}`);
    } finally {
        await fs.writeFile(path.join(__dirname, 'live-results.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2)); await browser.close();
    }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
