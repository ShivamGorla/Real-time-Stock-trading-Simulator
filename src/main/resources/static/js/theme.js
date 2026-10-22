/* Loaded in <head> so the saved theme applies before first paint. */
(function () {
    var KEY = 'stocksim-theme';
    var root = document.documentElement;
    try {
        var saved = localStorage.getItem(KEY);
        if (saved === 'light' || saved === 'dark') root.setAttribute('data-theme', saved);
    } catch (e) { /* storage unavailable: follow system setting */ }

    function current() {
        var set = root.getAttribute('data-theme');
        if (set) return set;
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
            var label = function () {
                btn.setAttribute('aria-label', current() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme');
            };
            label();
            btn.addEventListener('click', function () {
                var next = current() === 'dark' ? 'light' : 'dark';
                root.setAttribute('data-theme', next);
                try { localStorage.setItem(KEY, next); } catch (e) { /* ignore */ }
                label();
                document.dispatchEvent(new CustomEvent('themechange'));
            });
        });
    });
})();
