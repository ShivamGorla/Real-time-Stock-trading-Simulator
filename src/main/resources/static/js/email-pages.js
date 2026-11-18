/* forgot-password.html, reset-password.html, verify-email.html */
document.addEventListener('DOMContentLoaded', function () {
    function csrf() {
        var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : '';
    }
    var out = document.getElementById('result');
    function say(text, ok) { out.textContent = text; out.className = 'alert ' + (ok ? 'alert-success' : 'alert-error'); out.hidden = false; }
    function msg(body, fallback) {
        if (body && body.error) return body.error;
        if (body && typeof body === 'object') { var v = Object.values(body); if (v.length) return v.join(' '); }
        return fallback;
    }
    function call(method, url, payload) {
        var opts = { method: method, headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() } };
        if (payload) opts.body = JSON.stringify(payload);
        return fetch(url, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) { return { ok: res.ok, body: b }; });
        });
    }
    var token = new URLSearchParams(window.location.search).get('token');

    var verify = document.getElementById('verify-status');
    if (verify) {
        if (!token) return say('This link is missing its token.', false);
        call('GET', '/api/auth/verify-email?token=' + encodeURIComponent(token)).then(function (r) {
            say(r.ok ? r.body.message : msg(r.body, 'Could not verify your email.'), r.ok);
        }).catch(function () { say('Can’t reach the server.', false); });
    }

    // forgot-password.html: two buttons share one email field
    var emailForm = document.getElementById('email-form');
    if (emailForm) {
        emailForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var url = e.submitter && e.submitter.dataset.url ? e.submitter.dataset.url : '/api/auth/forgot-password';
            var email = document.getElementById('email').value.trim();
            if (!email) return say('Enter your email address.', false);
            var btns = emailForm.querySelectorAll('button'); btns.forEach(function (b) { b.disabled = true; });
            call('POST', url, { email: email }).then(function (r) {
                say(r.ok ? r.body.message : msg(r.body, 'Something went wrong.'), r.ok);
            }).catch(function () { say('Can’t reach the server.', false); })
              .finally(function () { btns.forEach(function (b) { b.disabled = false; }); });
        });
    }

    var resetForm = document.getElementById('reset-form');
    if (resetForm) {
        if (!token) say('This link is missing its token.', false);
        resetForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var pw = document.getElementById('new-password').value;
            var cf = document.getElementById('confirm-password').value;
            if (pw.length < 8) return say('Password must be at least 8 characters.', false);
            if (pw !== cf) return say('Passwords do not match.', false);
            var btn = resetForm.querySelector('[type="submit"]'); btn.disabled = true;
            call('POST', '/api/auth/reset-password', { token: token, newPassword: pw, confirmPassword: cf }).then(function (r) {
                say(r.ok ? r.body.message : msg(r.body, 'Could not reset your password.'), r.ok);
                if (r.ok) resetForm.reset();
            }).catch(function () { say('Can’t reach the server.', false); })
              .finally(function () { btn.disabled = false; });
        });
    }
});
