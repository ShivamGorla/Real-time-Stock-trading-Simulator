/* Account tools on the dashboard: edit name, change password, simulated top-up.
   Self-contained so it does not touch auth.js. Endpoints: PUT /api/user/me, PUT /api/user/me/password,
   POST /api/payments/top-up (Idempotency-Key header). */
document.addEventListener('DOMContentLoaded', function () {
    var tools = document.getElementById('account-tools');
    if (!tools) return;

    var PENDING_TOPUP_STORAGE_KEY = 'stocksim.pendingTopUp';
    var TOPUP_TIMEOUT_MS = 15000;
    var memoryPendingTopUp = null;

    function csrf() {
        var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : '';
    }
    var box = document.getElementById('tools-alert');
    function say(text, ok) {
        box.textContent = text;
        box.className = 'alert ' + (ok ? 'alert-success' : 'alert-error');
        box.hidden = false;
    }
    function errorText(body, fallback) {
        if (body && body.error) return body.error;
        if (body && typeof body === 'object') { var v = Object.values(body); if (v.length) return v.join(' '); }
        return fallback;
    }
    function formatInr(amount) {
        try {
            return '₹' + Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        } catch (e) {
            return '₹' + amount;
        }
    }
    function readPendingTopUp() {
        try {
            var raw = window.sessionStorage ? window.sessionStorage.getItem(PENDING_TOPUP_STORAGE_KEY) : null;
            if (raw) return JSON.parse(raw);
        } catch (e) { /* sessionStorage unavailable */ }
        return memoryPendingTopUp;
    }
    function savePendingTopUp(entry) {
        memoryPendingTopUp = entry;
        try {
            if (window.sessionStorage) {
                window.sessionStorage.setItem(PENDING_TOPUP_STORAGE_KEY, JSON.stringify(entry));
            }
        } catch (e) { /* sessionStorage unavailable */ }
    }
    function clearPendingTopUp() {
        memoryPendingTopUp = null;
        try {
            if (window.sessionStorage) {
                window.sessionStorage.removeItem(PENDING_TOPUP_STORAGE_KEY);
            }
        } catch (e) { /* sessionStorage unavailable */ }
    }
    function generateKey() {
        if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
        return 'topup-' + String(Date.now()) + '-' + Math.random().toString(36).slice(2, 12);
    }
    /**
     * Reuses the saved Idempotency-Key when retrying an ambiguous top-up of the same amount so
     * network timeouts or transient 5xx/409 responses never create a second payment or double-credit.
     */
    function getOrCreateTopUpKey(normalizedAmount) {
        var existing = readPendingTopUp();
        if (existing && existing.amount === normalizedAmount && existing.key) {
            return existing.key;
        }
        var key = generateKey();
        savePendingTopUp({ key: key, amount: normalizedAmount });
        return key;
    }
    function refreshUser() {
        return fetch('/api/user/me').then(function (r) { return r.ok ? r.json() : null; }).then(function (u) {
            if (!u) return;
            var nameInput = document.getElementById('profile-name');
            if (nameInput) nameInput.value = u.name;
            var balEl = document.getElementById('user-balance');
            if (balEl && u.virtualBalance != null) balEl.textContent = formatInr(u.virtualBalance);
        });
    }
    function send(method, url, payload, extraHeaders, timeoutMs) {
        var headers = Object.assign({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() }, extraHeaders || {});
        var opts = { method: method, headers: headers, body: JSON.stringify(payload) };
        var controller = (typeof AbortController !== 'undefined' && timeoutMs) ? new AbortController() : null;
        var timer = null;
        if (controller) {
            opts.signal = controller.signal;
            timer = setTimeout(function () { controller.abort(); }, timeoutMs);
        }
        return fetch(url, opts).then(function (res) {
            if (timer) clearTimeout(timer);
            return res.text().then(function (t) {
                var body = null;
                try { body = t ? JSON.parse(t) : null; } catch (e) { /* not JSON */ }
                return { ok: res.ok, status: res.status, body: body };
            });
        }, function (err) {
            if (timer) clearTimeout(timer);
            throw err;
        });
    }
    // Disables the button while the request runs so double clicks cannot submit twice.
    function wire(formId, handler) {
        var form = document.getElementById(formId);
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var btn = form.querySelector('[type="submit"]');
            btn.disabled = true;
            Promise.resolve(handler(form)).catch(function () {
                say('Can’t reach the server. Try again.', false);
            }).finally(function () { btn.disabled = false; });
        });
    }

    refreshUser();

    wire('profile-form', function () {
        var name = document.getElementById('profile-name').value.trim();
        if (!name) return say('Enter a display name.', false);
        return send('PUT', '/api/user/me', { name: name }).then(function (r) {
            if (!r.ok) return say(errorText(r.body, 'Could not save your name.'), false);
            document.getElementById('user-name').textContent = r.body.name;
            var chip = document.getElementById('user-chip-name');
            if (chip) chip.textContent = r.body.name;
            say('Name updated.', true);
        });
    });

    wire('password-form', function (form) {
        var cur = document.getElementById('pw-current').value;
        var next = document.getElementById('pw-new').value;
        var conf = document.getElementById('pw-confirm').value;
        if (next.length < 8) return say('New password must be at least 8 characters.', false);
        if (next !== conf) return say('New passwords do not match.', false);
        return send('PUT', '/api/user/me/password',
            { currentPassword: cur, newPassword: next, confirmNewPassword: conf }).then(function (r) {
            if (!r.ok) return say(errorText(r.body, 'Could not change your password.'), false);
            form.reset();
            say('Password changed.', true);
        });
    });

    wire('topup-form', function (form) {
        var rawAmount = document.getElementById('topup-amount').value;
        var numericAmount = Number(rawAmount);
        if (!rawAmount || !isFinite(numericAmount) || numericAmount <= 0) return say('Enter an amount.', false);
        var normalizedAmount = numericAmount.toFixed(2);
        var key = getOrCreateTopUpKey(normalizedAmount);

        return send('POST', '/api/payments/top-up', { amount: Number(normalizedAmount) }, { 'Idempotency-Key': key }, TOPUP_TIMEOUT_MS)
            .then(function (r) {
                if (r.ok && r.body && r.body.status === 'SUCCESS') {
                    clearPendingTopUp();
                    form.reset();
                    say('Simulated top-up of ₹' + r.body.amount + ' added.', true);
                    return refreshUser();
                }
                if (r.ok && r.body && r.body.status === 'FAILED') {
                    clearPendingTopUp();
                    say('The simulated payment was declined. Nothing was added.', false);
                    return;
                }
                // Definitive client/auth rejections never created a payment -> safe to clear key
                if (r.status === 400 || r.status === 401 || r.status === 403 || r.status === 422) {
                    clearPendingTopUp();
                    say(errorText(r.body, 'Top-up failed.'), false);
                    return;
                }
                // Ambiguous or retryable server outcomes (409 conflict, 5xx, or PENDING): KEEP key for safe retry
                say(errorText(r.body, 'Top-up status could not be confirmed.') + ' Click Add funds again to retry safely without double-crediting.', false);
            }, function () {
                // Network error or request timeout: KEEP key so retry replays the exact same payment
                say('Top-up request timed out or lost connection. Click Add funds again to retry safely without double-crediting.', false);
            });
    });
});
