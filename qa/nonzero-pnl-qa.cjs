/* Optional real SELL check: disposable schema only; prices change naturally. */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const modules = process.env.QA_NODE_MODULES || path.join(os.tmpdir(), 'stocksim-qa-tools/node_modules');
const { chromium } = require(path.join(modules, 'playwright'));
const baseURL = process.env.QA_BASE_URL || 'http://127.0.0.1:18080';
const cents = value => Math.round(Number(value) * 100);
async function main() {
    const db = new URL((process.env.DB_URL || '').replace(/^jdbc:/, ''));
    assert.equal(db.pathname, '/stocksim_smoke_test');
    const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
    const result = { status: 'BLOCKED', database: 'stocksim_smoke_test', naturalPrices: true, tradingCalculationsChanged: false };
    try {
        const context = await browser.newContext();
        const page = await context.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const username = 'qa_pnl_' + crypto.randomBytes(8).toString('hex');
        const password = 'Qa9!' + crypto.randomBytes(16).toString('hex');
        await page.goto(baseURL + '/register.html');
        await page.locator('#name').fill('Disposable PnL QA');
        await page.locator('#username').fill(username);
        await page.locator('#email').fill(username + '@example.invalid');
        await page.locator('#password').fill(password);
        await page.locator('#confirmPassword').fill(password);
        await page.locator('#register-form button[type=submit]').click();
        await page.waitForURL('**/login.html?registered=true');
        await page.locator('#username').fill(username);
        await page.locator('#password').fill(password);
        await page.locator('#login-form button[type=submit]').click();
        await page.waitForURL('**/dashboard.html');
        await page.waitForFunction(() => !document.getElementById('trade-order-submit').disabled);
        page.once('dialog', dialog => dialog.accept());
        await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.includes('EXECUTED'));
        const orders = await (await context.request.get(baseURL + '/api/orders')).json();
        const buy = orders.find(order => order.side === 'BUY');
        assert.equal(buy.symbol, 'INFY');
        // Wait for the existing simulated price engine. Never write prices or accounting data.
        let observedPrice = buy.executionPrice;
        const deadline = Date.now() + 60000;
        while (cents(observedPrice) === cents(buy.executionPrice) && Date.now() < deadline) {
            await page.waitForTimeout(1000);
            const response = await context.request.get(baseURL + '/api/stocks/INFY');
            assert.equal(response.status(), 200);
            observedPrice = (await response.json()).currentPrice;
        }
        assert.notEqual(cents(observedPrice), cents(buy.executionPrice), 'No natural price change observed within 60 seconds');
        await page.locator('[data-market-side=SELL]').click();
        page.once('dialog', dialog => dialog.accept());
        await page.locator('#trade-order-submit').click();
        await page.waitForFunction(() => document.getElementById('trade-order-feedback').textContent.startsWith('SELL 1 INFY: EXECUTED'));
        const finalOrders = await (await context.request.get(baseURL + '/api/orders')).json();
        const sell = finalOrders.find(order => order.side === 'SELL');
        Object.assign(result, { buyPrice: buy.executionPrice, observedPriceBeforeSell: observedPrice, sellPrice: sell.executionPrice });
        const expectedCents = cents(sell.executionPrice) - cents(buy.executionPrice);
        assert.notEqual(expectedCents, 0, 'Price returned to the original execution price; nonzero check not established');
        const portfolio = await (await context.request.get(baseURL + '/api/portfolio')).json();
        assert.equal(cents(portfolio.totalRealizedPnl), expectedCents);
        const expectedDisplay = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(expectedCents / 100);
        await page.waitForFunction(text => document.getElementById('live-realized').textContent === text, expectedDisplay);
        const user = await (await context.request.get(baseURL + '/api/user/me')).json();
        assert(Number.isSafeInteger(user.id) && user.id > 0);
        const sql = `SELECT h.total_realized_pnl,w.balance FROM holdings h JOIN wallets w ON w.user_id=h.user_id WHERE h.user_id=${user.id}`;
        const sqlResult = await new Promise((resolve, reject) => {
            const child = spawn(process.env.QA_MYSQL || 'C:/Program Files/MySQL/MySQL Server 26.7/bin/mysql.exe', ['--host=' + db.hostname, '--port=' + (db.port || '3306'), '--user=' + process.env.DB_USERNAME, '--database=stocksim_smoke_test', '--batch', '--skip-column-names', '-e', sql], { windowsHide: true, env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD } });
            let output = '';
            child.stdout.on('data', data => output += data);
            child.on('error', () => reject(new Error('MySQL executable unavailable')));
            child.on('exit', code => code === 0 ? resolve(output.trim().split('\t')) : reject(new Error('SQL validation failed')));
        });
        assert.equal(cents(sqlResult[0]), expectedCents);
        assert.equal(cents(sqlResult[1]), 10000000 + expectedCents);
        assert.deepEqual(errors, []);
        Object.assign(result, { status: 'PASS', buyPrice: buy.executionPrice, sellPrice: sell.executionPrice, quantitySold: 1, expectedRealizedPnl: expectedCents / 100, portfolioRealizedPnl: portfolio.totalRealizedPnl, displayedRealizedPnl: await page.locator('#live-realized').textContent(), sqlRealizedPnl: Number(sqlResult[0]), sqlWalletBalance: Number(sqlResult[1]), uncaughtExceptions: errors });
        await page.locator('#logout-btn').click();
        await page.waitForURL(baseURL + '/');
    } catch (error) {
        result.reason = error.message;
        process.exitCode = 1;
    } finally {
        await fs.writeFile(path.join(__dirname, 'nonzero-pnl-results.json'), JSON.stringify(result, null, 2) + '\n');
        console.log(JSON.stringify(result));
        await browser.close();
    }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
