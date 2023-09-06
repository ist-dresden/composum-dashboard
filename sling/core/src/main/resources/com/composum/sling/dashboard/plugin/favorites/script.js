class FavoritesToggle extends ViewWidget {

    static selector = '.browser-tool-favorites';

    constructor(element) {
        super(element);
        this.profile = new Profile('favorites');
        this.selection = this.profile.get('selection') || [];
        this.history = this.profile.get('history') || [];
        this.historyMax = 200;
        $(document).on('path:selected', this.onPathSelected.bind(this));
    }

    getViewWidget() {
        if (!this.viewWidget) {
            this.viewWidget = Widgets.getView($(FavoritesView.selector), FavoritesView);
            if (this.viewWidget) {
                this.historyMax = parseInt(this.viewWidget.$el.data('history-max') || '100');
            }
        }
        return this.viewWidget;
    }

    applyToView(action) {
        const viewWidget = this.getViewWidget();
        if (viewWidget) {
            action(viewWidget);
        }
    }

    onPathSelected(event, path) {
        this.currentPath = path;
        this.adjustToggleAction();
        if (this.history.length < 1 || this.history[this.history.length - 1] !== path) {
            this.history.push(path);
            if (this.historyMax && this.history.length > this.historyMax) {
                this.history.splice(0, this.history.length - this.historyMax);
            }
            this.profile.set('history', this.history);
            this.applyToView(viewWidget => {
                if (viewWidget.$currentTab.attr('id') === 'history') {
                    viewWidget.renderFavorites()
                }
            });
        }
    }

    adjustToggleAction() {
        if (this.currentPath) {
            const $toggle = $('.dashboard-browser__favorite-toggle');
            if (this.isFavorite(this.currentPath)) {
                $toggle.addClass('is-favorite');
            } else {
                $toggle.removeClass('is-favorite');
            }
            $toggle.off('click').click(this.toggleFavorite.bind(this));
        }
    }

    toggleFavorite(event) {
        event.preventDefault();
        if (this.currentPath) {
            this.isFavorite(this.currentPath, true);
            this.profile.set('selection', this.selection);
            this.adjustToggleAction();
            this.applyToView(viewWidget => viewWidget.renderFavorites());
        }
    }

    isFavorite(path, toggle) {
        let result = undefined;
        for (let i = 0; i < this.selection.length; i++) {
            if (path === this.selection[i]) {
                if (toggle) {
                    this.selection.splice(i, 1);
                    result = false;
                } else {
                    result = true;
                }
                break;
            }
        }
        if (result === undefined) {
            if (toggle) {
                this.selection.push(path);
                this.selection.sort();
                return true;
            }
            return false;
        }
        return result;
    }
}

CPM.widgets.register(FavoritesToggle);

class FavoritesView extends ViewWidget {

    static selector = '.dashboard-widget__favorites';

    constructor(element) {
        super(element);
        this.toggleWidget = Widgets.getView($(FavoritesToggle.selector), FavoritesToggle);
        this.profile = this.toggleWidget.profile;
        this.selection = this.toggleWidget.selection;
        this.history = this.toggleWidget.history;
        this.$content = this.$el.find('.dashboard-widget__favorites-content');
        this.showTab(this.profile.get('currentTab'), true);
        this.$el.find('.dashboard-widget__favorites-clear').click(this.clearFavorites.bind(this));
        this.$el.find('.dashboard-widget__favorites-groups a[data-toggle="tab"]').click(this.selectTab.bind(this));
    }

    selectTab(event) {
        this.showTab($(event.currentTarget).attr('id'));
    }

    showTab(tabId, force) {
        this.$currentTab = this.$el.find('.dashboard-widget__favorites-groups .nav-link[id="' + tabId + '"]');
        if (this.$currentTab.length < 1 && force) {
            this.$currentTab = this.$el.find('.dashboard-widget__favorites-groups .nav-link').first();
        }
        if (this.$currentTab.length > 0) {
            this.$currentTab.tab('show');
            tabId = this.$currentTab.attr('id');
            this.profile.set('currentTab', tabId);
            this.renderFavorites();
        }
    }

    renderFavorites() {
        let content = '';
        if (this.$currentTab.attr('id') === 'history') {
            for (let i = this.history.length; --i >= 0;) {
                content += '<tr><td class="path"><a href="#" data-path="'
                    + this.sanitizeAttr(this.history[i]) + '">'
                    + this.sanitizeHtml(this.history[i]) + '</a></td></tr>';
            }
        } else {
            this.applySelectionPattern(function (item, index) {
                content += '<tr><td class="path"><a href="#" data-path="'
                    + this.sanitizeAttr(item) + '">'
                    + this.sanitizeHtml(item) + '</a></td></tr>';
                return index + 1;
            }.bind(this));
        }
        this.$content.html(content);
        this.$content.find('a').click(this.selectFavorite.bind(this));
    }

    selectFavorite(event) {
        event.preventDefault();
        $(document).trigger('path:select', [$(event.currentTarget).data('path')]);
    }

    clearFavorites(event) {
        event.preventDefault();
        if (this.$currentTab.attr('id') === 'history') {
            this.history = this.toggleWidget.history = [];
            this.profile.set('history', this.history);
        } else {
            this.applySelectionPattern((item, index) => {
                this.selection.splice(index, 1);
                return index;
            });
            this.profile.set('selection', this.selection);
        }
        this.toggleWidget.adjustToggleAction();
        this.renderFavorites();
    }

    applySelectionPattern(action) {
        const pattern = this.$currentTab && this.$currentTab.length > 0
            ? new RegExp(this.$currentTab.data('pattern')) : undefined;
        for (let i = 0; i < this.selection.length;) {
            if (!pattern || pattern.exec(this.selection[i]) !== null) {
                i = action(this.selection[i], i);
            } else {
                i++
            }
        }
    }
}

CPM.widgets.register(FavoritesView);
