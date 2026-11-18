/* Account tools on the dashboard: edit name, change password, simulated top-up.
   Self-contained so it does not touch auth.js. Endpoints: PUT /api/user/me, PUT /api/user/me/password,
   POST /api/payments/top-up (Idempotency-Key header). */
document.addEventListener('DOMContentLoaded', function () {
    var tools = document.getElementById('account-tools');
    if (!tools) return;

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
    function refreshUser() {
        return fetch('/api/user/me').then(function (r) { return r.ok ? r.json() : null; }).then(function (u) {
            if (!u) return;
            var nameInput = document.getElementById('profile-name');
            if (nameInput) nameInput.value = u.name;
            var balEl = document.getElementById('user-balance');
            if (balEl && u.virtualBalance != null) balEl.textContent = formatInr(u.virtualBalance);
        });
    }
    function send(method, url, payload, extraHeaders) {
        var headers = Object.assign({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() }, extraHeaders || {});
        return fetch(url, { method: method, headers: headers, body: JSON.stringify(payload) }).then(function (res) {
            return res.text().then(function (t) {
                var body = null;
                try { body = t ? JSON.parse(t) : null; } catch (e) { /* not JSON */ }
                return { ok: res.ok, status: res.status, body: body };
            });
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
        var amount = document.getElementById('topup-amount').value;
        if (!amount || Number(amount) <= 0) return say('Enter an amount.', false);
        // A fresh key per submission; the server returns the original result if the same key is replayed.
        var key = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(36).slice(2);
        return send('POST', '/api/payments/top-up', { amount: Number(amount) }, { 'Idempotency-Key': key }).then(function (r) {
            if (!r.ok) return say(errorText(r.body, 'Top-up failed.'), false);
            if (r.body.status === 'SUCCESS') {
                form.reset();
                say('Simulated top-up of ₹' + r.body.amount + ' added.', true);
                return refreshUser();
            } else {
                say('The simulated payment was declined. Nothing was added.', false);
            }
        });
    });
});
