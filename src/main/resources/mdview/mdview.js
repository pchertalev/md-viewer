/*
  Поведение страницы: тема, масштаб, содержание, подсветка кода, кнопки панели.

  Всё, что требует доступа к диску или к приложению (открыть файл, перечитать,
  открыть в редакторе), уходит в приложение через мост mdviewQuery — сама страница
  ничего не открывает. Если моста нет (страницу открыли в обычном браузере),
  кнопки просто неактивны.
*/
(function () {
    'use strict';

    var root = document.documentElement;
    var body = document.body;
    var source = body.getAttribute('data-source') || '';

    var SCALE_STEP = 0.1;
    var SCALE_MIN = 0.6;
    var SCALE_MAX = 2.4;

    /* ---------- мост в приложение ---------- */

    function bridge(request) {
        if (!window.mdviewQuery) {
            return Promise.reject(new Error('мост недоступен'));
        }
        return new Promise(function (resolve, reject) {
            window.mdviewQuery({
                request: request,
                onSuccess: resolve,
                onFailure: reject
            });
        });
    }

    /* ---------- тема ---------- */

    var darkQuery = window.matchMedia('(prefers-color-scheme: dark)');

    function currentTheme() {
        return root.getAttribute('data-theme') || 'auto';
    }

    function effectiveTheme() {
        var theme = currentTheme();
        if (theme === 'auto') {
            return darkQuery.matches ? 'dark' : 'light';
        }
        return theme;
    }

    function applyTheme() {
        var effective = effectiveTheme();
        root.setAttribute('data-effective-theme', effective);

        // подсветка кода идёт двумя готовыми темами highlight.js — включаем ту, что подходит
        var light = document.getElementById('hljs-light');
        var dark = document.getElementById('hljs-dark');
        if (light && dark) {
            light.disabled = effective === 'dark';
            dark.disabled = effective !== 'dark';
        }
    }

    darkQuery.addEventListener('change', function () {
        if (currentTheme() === 'auto') {
            applyTheme();
        }
    });

    function cycleTheme() {
        var next = effectiveTheme() === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-theme', next);
        applyTheme();
        bridge('theme:' + next).catch(function () {});
    }

    /* ---------- масштаб шрифта ---------- */

    function scale() {
        var value = parseFloat(getComputedStyle(root).getPropertyValue('--font-scale'));
        return isNaN(value) ? 1 : value;
    }

    function setScale(value) {
        var clamped = Math.min(SCALE_MAX, Math.max(SCALE_MIN, Math.round(value * 100) / 100));
        root.style.setProperty('--font-scale', String(clamped));
        bridge('scale:' + clamped).catch(function () {});
    }

    /* ---------- содержание ---------- */

    var sidebar = document.getElementById('sidebar');
    var layout = document.querySelector('.md-layout');

    function tocVisible() {
        return !layout.classList.contains('md-no-toc');
    }

    function toggleToc() {
        layout.classList.toggle('md-no-toc');
        bridge('toc:' + (tocVisible() ? 'on' : 'off')).catch(function () {});
    }

    /* активный пункт содержания: идём по заголовкам сверху, последний пройденный и есть текущий */
    var tocLinks = Array.prototype.slice.call(document.querySelectorAll('.md-toc-item a'));
    var headingsByAnchor = {};
    var ACTIVE_TOC_OFFSET = 96;

    tocLinks.forEach(function (link) {
        var anchor = link.getAttribute('href').slice(1);
        var heading = document.getElementById(anchor);
        if (heading) {
            headingsByAnchor[anchor] = heading;
        }
    });

    function updateActiveToc() {
        var activeAnchor = null;
        var activeTop = Number.NEGATIVE_INFINITY;

        Object.keys(headingsByAnchor).forEach(function (anchor) {
            var top = headingsByAnchor[anchor].getBoundingClientRect().top;
            if (top <= ACTIVE_TOC_OFFSET && top > activeTop) {
                activeTop = top;
                activeAnchor = anchor;
            }
        });

        tocLinks.forEach(function (link, index) {
            var item = link.parentElement;
            // пока ни один заголовок не пройден, активным считаем самый верхний
            var isActive = activeAnchor === null ? index === 0 : link.getAttribute('href') === '#' + activeAnchor;
            item.classList.toggle('is-active', isActive);
        });
    }

    var scrollScheduled = false;
    window.addEventListener('scroll', function () {
        if (scrollScheduled) {
            return;
        }
        scrollScheduled = true;
        requestAnimationFrame(function () {
            scrollScheduled = false;
            updateActiveToc();
        });
    }, { passive: true });

    /* ---------- код ---------- */

    function highlightCode() {
        if (!window.hljs) {
            return;
        }
        document.querySelectorAll('pre code').forEach(function (block) {
            if (block.dataset.highlighted) {
                return;
            }
            try {
                window.hljs.highlightElement(block);
            } catch (error) {
                /* подсветка не критична: текст и без неё читается */
            }
        });
    }

    function addCopyButtons() {
        document.querySelectorAll('pre').forEach(function (pre) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'md-btn md-code-copy';
            button.textContent = 'копировать';
            button.addEventListener('click', function () {
                var code = pre.querySelector('code');
                var text = (code || pre).innerText;
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(text);
                } else {
                    bridge('copy:' + encodeURIComponent(text)).catch(function () {});
                }
                button.textContent = 'готово';
                setTimeout(function () {
                    button.textContent = 'копировать';
                }, 1200);
            });
            pre.appendChild(button);
        });
    }

    /* ---------- ссылки ---------- */

    function wireLinks() {
        document.addEventListener('click', function (event) {
            var link = event.target.closest('a');
            if (!link) {
                return;
            }

            var href = link.getAttribute('href') || '';

            if (link.classList.contains('md-missing')) {
                event.preventDefault();
                return;
            }

            if (href.charAt(0) === '#') {
                return; /* переход по якорю — дело браузера */
            }

            /* соседний markdown-файл открывает приложение, а не браузер */
            if (link.classList.contains('md-doc-link')) {
                event.preventDefault();
                bridge('open:' + decodeURIComponent(href.replace(/^file:\/\/\/?/, ''))).catch(function () {});
            }
        });
    }

    /* ---------- панель и клавиши ---------- */

    var actions = {
        toc: toggleToc,
        theme: cycleTheme,
        'font-minus': function () { setScale(scale() - SCALE_STEP); },
        'font-plus': function () { setScale(scale() + SCALE_STEP); },
        'font-reset': function () { setScale(1); },
        reload: function () { bridge('reload').catch(function () {}); },
        edit: function () { bridge('edit:' + source).catch(function () {}); },
        browser: function () { bridge('browser').catch(function () {}); }
    };

    document.querySelectorAll('.md-btn[data-action]').forEach(function (button) {
        button.addEventListener('click', function () {
            var action = actions[button.getAttribute('data-action')];
            if (action) {
                action();
            }
        });
    });

    document.addEventListener('keydown', function (event) {
        if (event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }

        switch (event.key.toLowerCase()) {
            case 't':
                toggleToc();
                break;
            case 'd':
                cycleTheme();
                break;
            case 'r':
                bridge('reload').catch(function () {});
                break;
            case 'e':
                bridge('edit:' + source).catch(function () {});
                break;
            case 'b':
                bridge('browser').catch(function () {});
                break;
            case '+':
            case '=':
                setScale(scale() + SCALE_STEP);
                break;
            case '-':
                setScale(scale() - SCALE_STEP);
                break;
            case '0':
                setScale(1);
                break;
        }
    });

    /* ---------- старт ---------- */

    // окно вызывает те же действия из меню — реализация остаётся одна
    window.mdview = {
        invoke: function (action) {
            var handler = actions[action];
            if (handler) {
                handler();
            }
        }
    };

    applyTheme();
    highlightCode();
    addCopyButtons();
    wireLinks();
    updateActiveToc();
})();
