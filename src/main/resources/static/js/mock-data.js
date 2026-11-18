
/* Isolated development-only mock market data for StockSim.
   Strictly read-only: Used when backend market endpoints are offline.
   Never used for order settlement or wallet balance manipulation.
*/

(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.StockSimMockData = factory();
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var MOCK_NOTICE =
        'Illustrative simulated market data — backend market feed unavailable.';

    var DEFAULT_STOCKS = [
        {
            symbol: 'RELIANCE',
            companyName: 'Reliance Industries Ltd',
            currentPrice: 2950,
            previousPrice: 2928,
            change: 22,
            changePercent: 0.75,
            high: 2965,
            low: 2915,
            volume: 3840210,
            sector: 'Energy / Conglomerate',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'TCS',
            companyName: 'Tata Consultancy Services Ltd',
            currentPrice: 3850,
            previousPrice: 3885,
            change: -35,
            changePercent: -0.90,
            high: 3890,
            low: 3830,
            volume: 1920400,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'HDFCBANK',
            companyName: 'HDFC Bank Ltd',
            currentPrice: 1620,
            previousPrice: 1612,
            change: 8,
            changePercent: 0.50,
            high: 1632,
            low: 1608,
            volume: 5120800,
            sector: 'Banking / Financial Services',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'INFY',
            companyName: 'Infosys Ltd',
            currentPrice: 1540,
            previousPrice: 1555,
            change: -15,
            changePercent: -0.96,
            high: 1560,
            low: 1532,
            volume: 4210300,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'ICICIBANK',
            companyName: 'ICICI Bank Ltd',
            currentPrice: 1110,
            previousPrice: 1098,
            change: 12,
            changePercent: 1.09,
            high: 1118,
            low: 1095,
            volume: 3410100,
            sector: 'Banking / Financial Services',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'TATAMOTORS',
            companyName: 'Tata Motors Ltd',
            currentPrice: 980,
            previousPrice: 962,
            change: 18,
            changePercent: 1.87,
            high: 988,
            low: 958,
            volume: 6850200,
            sector: 'Automotive',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'ITC',
            companyName: 'ITC Ltd',
            currentPrice: 435,
            previousPrice: 436.50,
            change: -1.50,
            changePercent: -0.34,
            high: 438,
            low: 433,
            volume: 7240900,
            sector: 'FMCG / Diversified',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'SBIN',
            companyName: 'State Bank of India',
            currentPrice: 790,
            previousPrice: 782,
            change: 8,
            changePercent: 1.02,
            high: 795,
            low: 780,
            volume: 4930100,
            sector: 'Public Sector Banking',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'BHARTIARTL',
            companyName: 'Bharti Airtel Ltd',
            currentPrice: 1320,
            previousPrice: 1308,
            change: 12,
            changePercent: 0.92,
            high: 1328,
            low: 1305,
            volume: 2780600,
            sector: 'Telecommunications',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'WIPRO',
            companyName: 'Wipro Ltd',
            currentPrice: 490,
            previousPrice: 496,
            change: -6,
            changePercent: -1.21,
            high: 498,
            low: 488,
            volume: 2150800,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        }
    ];

    function clone(obj) {
        return JSON.parse(JSON.stringify(obj));
    }

    // Deterministic pseudo-random number generator
    function seededRandom(seed) {
        var x = Math.sin(seed) * 10000;
        return x - Math.floor(x);
    }

    // Return matching stock or null
    function getStock(sym) {
        sym = String(sym || '').trim().toUpperCase();

        for (var i = 0; i < DEFAULT_STOCKS.length; i++) {
            if (DEFAULT_STOCKS[i].symbol === sym) {
                return DEFAULT_STOCKS[i];
            }
        }

        return null;
    }

    // Generate deterministic historical price data
    function generateHistory(stock, limit) {
        if (!stock) {
            return [];
        }

        var count = Math.min(
            Math.max(Math.floor(Number(limit) || 72), 2),
            100
        );

        var seedBase = 0;

        for (var c = 0; c < stock.symbol.length; c++) {
            seedBase =
                (seedBase * 31 + stock.symbol.charCodeAt(c)) & 0xFFFFFF;
        }

        var startPrice = stock.previousPrice;
        var endPrice = stock.currentPrice;
        var history = [];

        var now = Date.now();
        var intervalMs = 5 * 60 * 1000;

        for (var i = 0; i < count; i++) {
            var progress = i / (count - 1);

            var baseline =
                startPrice + (endPrice - startPrice) * progress;

            var noise =
                (seededRandom(seedBase + i * 17) - 0.5) *
                (startPrice * 0.012) *
                Math.sin(Math.PI * progress);

            var price;

            if (i === 0) {
                price = startPrice;
            } else if (i === count - 1) {
                price = endPrice;
            } else {
                price = Math.max(
                    0.01,
                    Math.round((baseline + noise) * 100) / 100
                );
            }

            history.push({
                price: price,
                recordedAt: new Date(
                    now - (count - 1 - i) * intervalMs
                ).toISOString(),
                simulated: true
            });
        }

        return history;
    }

    return {
        MOCK_NOTICE: MOCK_NOTICE,

        getStocks: function () {
            return clone(DEFAULT_STOCKS);
        },

        getStockBySymbol: function (sym) {
            var stock = getStock(sym);
            return stock ? clone(stock) : null;
        },

        searchStocks: function (query) {
            if (!query || typeof query !== 'string' || !query.trim()) {
                return clone(DEFAULT_STOCKS);
            }

            var q = query.trim().toUpperCase();

            return DEFAULT_STOCKS.filter(function (stock) {
                return stock.symbol.indexOf(q) !== -1 ||
                    stock.companyName.toUpperCase().indexOf(q) !== -1;
            }).map(clone);
        },

        getStockHistory: function (sym, limit) {
            return generateHistory(getStock(sym), limit);
        },

        getOverview: function () {
            var stocks = clone(DEFAULT_STOCKS);

            var sortedGainers = stocks.slice().sort(function (a, b) {
                return b.changePercent - a.changePercent;
            });

            var topGainer = sortedGainers[0];
            var topLoser = sortedGainers[sortedGainers.length - 1];

            var mostActive = stocks.slice().sort(function (a, b) {
                return b.volume - a.volume;
            })[0];

            return {
                benchmark: {
                    name: 'Simulated NIFTY 50',
                    value: 24185.60,
                    change: 112.40,
                    changePercent: 0.47,
                    simulated: true
                },
                topGainer: topGainer,
                topLoser: topLoser,
                mostActive: mostActive,
                activeCount: stocks.length,
                simulated: true
            };
        }
    };
}));