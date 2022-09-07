class Profile {

    constructor(aspect) {
        this.aspect = aspect;
        this.load();
    }

    load() {
        this.profile = JSON.parse(localStorage.getItem('composum-dashboard') || '{}');
        if (!this.profile[this.aspect]) {
            this.profile[this.aspect] = {};
        }
    }

    get(key) {
        return this.profile[this.aspect][key];
    }

    set(key, value) {
        this.load();
        this.profile[this.aspect][key] = value;
        localStorage.setItem('composum-dashboard', JSON.stringify(this.profile));
    }
}

class URL {

    constructor(url) {
        const parts = /^((https?:\/\/[^/]+)?((\/[^/?]+)*\/([^/?]*)))(\?([^?]*))?$/i.exec(url);
        this.uri = parts[1];
        this.server = parts[2] || '';
        this.path = parts[3] || '/';
        this.name = parts[5];
        this.query = parts[7] || '';
        this.parameters = URL.parameters(this.query);
    }

    static parameters(query) {
        const params = {};
        if (query) {
            query.split('&').forEach(function (param) {
                const nv = param.split('=');
                params[decodeURIComponent(nv[0])] = nv.length > 0 ? decodeURIComponent(nv[1]) : '';
            });
        }
        return params;
    }
}

class History {

    constructor() {
        window.onpopstate = function (event) {
            const state = /^(\/[^.]+\.html)?(\/[^?]*)(\?(.*))?$/.exec(event.state);
            if (state) {
                $(document).trigger('path:select', [state[2]]);
                if (state[4]) {
                    $(document).trigger('query:change', [URL.parameters(state[4])]);
                }
            }
        };
    }

    pushUri(uri) {
        if (history.pushState) {
            const current = new URL(window.location.href);
            const next = new URL(uri);
            if (next.path !== current.path) {
                const state = next.path + (current.query ? ('?' + current.query) : '');
                history.pushState(state, next.name, state);
            }
        }
    }

    pushQuery(parameters) {
        if (history.pushState) {
            const current = new URL(window.location.href);
            const query = Object.getOwnPropertyNames(parameters)
                .map(n => encodeURIComponent(n) + '=' + encodeURIComponent(parameters[n])).join('&');
            if (current.query !== query) {
                const state = current.path + (query ? ('?' + query) : '');
                history.pushState(state, current.name, state);
            }
        }
    }
}

(window.CPM = window.CPM || {}).history = new History();

class Widgets {

    constructor() {
        this.registry = {};
    }

    register(widgetClass, selector) {
        if (widgetClass) {
            this.registry[selector || widgetClass.selector] = widgetClass;
        }
    }

    initialize(element) {
        const $element = $(element || document);
        const registry = this.registry;
        Object.getOwnPropertyNames(registry).forEach(function (selector) {
            $element.find(selector).each(function () {
                if (!this.view || !this.view[registry[selector]]) {
                    if (!this.view) {
                        this.view = {};
                    }
                    this.view[registry[selector].name] = new registry[selector](this);
                }
            });
        });
    }

    static getView(element, widgetClass) {
        const el = $(element)[0];
        return el.view ? (widgetClass ? el.view[widgetClass.name] : Widgets.getFirstView(element)) : undefined;
    }

    static getFirstView(element) {
        const el = $(element)[0];
        if (el.view) {
            const keys = Object.getOwnPropertyNames(el.view);
            return keys.length > 0 ? el.view[keys[0]] : undefined;
        }
        return undefined;
    }
}

(window.CPM = window.CPM || {}).widgets = new Widgets();

class ViewWidget {

    constructor(element) {
        this.$el = $(element);
        this.el = this.$el[0];
        this.$ = function (selector) {
            return this.$el.find(selector);
        }.bind(this);
    }

    onContentLoaded(element) {
        CPM.widgets.initialize(element || this.el);
    }

    loadContent($element, url, callback) {
        $.ajax({
            type: 'GET',
            url: url,
            success: function (content) {
                $element.html(content);
                if (callback) {
                    callback($element);
                } else {
                    this.onContentLoaded($element);
                }
            }.bind(this),
            async: true,
            cache: false
        });
    }

    formData(form) {
        const formData = new FormData($(form)[0]);
        if (!formData.get('_charset_')) {
            formData.set('_charset_', 'UTF-8');
        }
        return formData;
    }

    formGetUrl(form) {
        return $(form).attr("action") + '?' + ([...this.formData(form).entries()]
            .map(x => encodeURIComponent(x[0]) + '=' + encodeURIComponent(x[1])).join('&'));
    }
}

class ResumingTabs extends ViewWidget {

    static selector = '.resuming-tabs';

    static css = {
        nav: ResumingTabs.selector + '_nav',
        link: ResumingTabs.selector + '_nav .nav-link',
        pane: ResumingTabs.selector + '_pane'
    };

    constructor(element, identifier, onShownCallback) {
        super(element)
        this.onShownCallback = onShownCallback;
        this.profile = new Profile(identifier || this.$el.data('tabs-id'));
        this.$(ResumingTabs.css.nav + ' a[data-toggle="tab"]').on('shown.bs.tab', this.onTabShown.bind(this));
        this.showTab(this.profile.get('currentTab'), true);
    }

    activeTabId() {
        const active = this.$(ResumingTabs.css.link + '.active').attr('aria-controls');
        return active || this.$(ResumingTabs.css.link).first().attr('aria-controls')
    }

    $tabPane(tabId) {
        return tabId ? this.$(ResumingTabs.css.pane + '[id="' + tabId + '"]') : undefined;
    }

    onTabShown(event) {
        const tabId = $(event.target).attr('aria-controls');
        this.profile.set('currentTab', tabId);
        if (this.onShownCallback) {
            this.onShownCallback(event, tabId, this.$tabPane(tabId));
        }
    }

    showTab(tabId, force) {
        const $tab = this.$(ResumingTabs.css.link + '[aria-controls="' + tabId + '"]');
        if ($tab.length > 0) {
            $tab.tab('show');
        } else if (force) {
            this.$(ResumingTabs.css.link).first().tab('show');
        }
    }
}

CPM.widgets.register(ResumingTabs);
