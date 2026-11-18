
/* Centralized REST API Client for StockSim.
   Handles credentials, CSRF token attachment, HTTP error mapping,
   and safe mock-data fallback.
*/

(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.StockSimAPI = factory();
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // Read CSRF token from browser cookie
    function getCsrfToken() {
        var match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    // Centralized HTTP request handler
    async function request(endpoint, options) {
        options = options || {};
        options.credentials = 'same-origin';
        options.headers = options.headers || {};

        var method = (options.method || 'GET').toUpperCase();

        if (method !== 'GET' && method !== 'HEAD') {
            var csrf = getCsrfToken();

            if (csrf && !options.headers['X-XSRF-TOKEN']) {
                options.headers['X-XSRF-TOKEN'] = csrf;
            }
        }

        var response = await fetch(endpoint, options);

        // Handle expired sessions
        if (response.status === 401) {
            if (
                window.location.pathname.indexOf('login.html') === -1 &&
                window.location.pathname.indexOf('register.html') === -1
            ) {
                window.location.href = '/login.html?expired=true';
            }

            var err401 = new Error('Session expired or unauthenticated');
            err401.status = 401;
            throw err401;
        }

        // Handle HTTP errors
        if (!response.ok) {
            var errorBody = null;

            try {
                errorBody = await response.json();
            } catch (e) {
                // Response was not JSON
            }

            var error = new Error(
                (errorBody && (errorBody.message || errorBody.error)) ||
                ('HTTP error ' + response.status)
            );

            error.status = response.status;
            error.data = errorBody;

            throw error;
        }

        // Handle successful responses
        var contentType = response.headers.get('content-type');

        if (contentType && contentType.indexOf('application/json') !== -1) {
            return await response.json();
        }

        return await response.text();
    }

    // Allow mock fallback only for missing endpoints or network failures
    function shouldUseMockFallback(err) {
        return err.status === 404 ||
            (err instanceof TypeError && !err.status);
    }

    // Check whether mock data is available
    function hasMockData() {
        return typeof window !== 'undefined' &&
            !!window.StockSimMockData;
    }

    // Common fallback error handling
    function throwIfFallbackUnavailable(err) {
        if (!shouldUseMockFallback(err) || !hasMockData()) {
            throw err;
        }
    }

    return {
        getCsrfToken: getCsrfToken,
        request: request,

        // User profile and authoritative virtual balance
        getUserProfile: function () {
            return request('/api/user/me');
        },

        // Stock catalog
        getStocks: async function () {
            try {
                var stocks = await request('/api/stocks');

                return {
                    source: 'live',
                    stocks: stocks,
                    isMock: false
                };
            } catch (err) {
                throwIfFallbackUnavailable(err);

                return {
                    source: 'mock',
                    stocks: window.StockSimMockData.getStocks(),
                    isMock: true,
                    notice: window.StockSimMockData.MOCK_NOTICE
                };
            }
        },

        // Search stocks by symbol or company name
        searchStocks: async function (query) {
            if (!query || !query.trim()) {
                return this.getStocks();
            }

            var q = query.trim();

            try {
                var matches = await request(
                    '/api/stocks/search?query=' + encodeURIComponent(q)
                );

                return {
                    source: 'live',
                    stocks: matches,
                    isMock: false
                };
            } catch (err) {
                throwIfFallbackUnavailable(err);

                return {
                    source: 'mock',
                    stocks: window.StockSimMockData.searchStocks(q),
                    isMock: true,
                    notice: window.StockSimMockData.MOCK_NOTICE
                };
            }
        },

        // Get details of an individual stock
        getStockBySymbol: async function (symbol) {
            try {
                var stock = await request(
                    '/api/stocks/' + encodeURIComponent(symbol)
                );

                return {
                    source: 'live',
                    stock: stock,
                    isMock: false
                };
            } catch (err) {
                throwIfFallbackUnavailable(err);

                return {
                    source: 'mock',
                    stock: window.StockSimMockData.getStockBySymbol(symbol),
                    isMock: true,
                    notice: window.StockSimMockData.MOCK_NOTICE
                };
            }
        },

        // Get historical stock prices
        getStockHistory: async function (symbol, limit) {
            var n = Number(limit) || 72;

            try {
                var history = await request(
                    '/api/stocks/' +
                    encodeURIComponent(symbol) +
                    '/history?limit=' +
                    encodeURIComponent(n)
                );

                return {
                    source: 'live',
                    history: history,
                    isMock: false
                };
            } catch (err) {
                throwIfFallbackUnavailable(err);

                return {
                    source: 'mock',
                    history: window.StockSimMockData.getStockHistory(symbol, n),
                    isMock: true,
                    notice: window.StockSimMockData.MOCK_NOTICE
                };
            }
        },

        // Market overview
        getMarketOverview: async function () {
            try {
                var overview = await request('/api/stocks/overview');

                return {
                    source: 'live',
                    data: overview,
                    isMock: false
                };
            } catch (err) {
                throwIfFallbackUnavailable(err);

                return {
                    source: 'mock',
                    data: window.StockSimMockData.getOverview(),
                    isMock: true,
                    notice: window.StockSimMockData.MOCK_NOTICE
                };
            }
        }
    };
}));