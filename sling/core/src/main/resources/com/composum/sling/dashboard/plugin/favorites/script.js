class FavoritesView extends ViewWidget {

    static selector = '.dashboard-widget__favorites';

    constructor(element) {
        super(element);
        this.$content = this.$el.find('.dashboard-widget__favorites-content');
        this.profile = new Profile('favorites');
        this.favorites = this.profile.get('items') || [];
        this.showTab(this.profile.get('currentTab'), true);
        this.$el.find('.dashboard-widget__favorites-clear').click(this.clearFavorites.bind(this));
        this.$el.find('.dashboard-widget__favorites-groups a[data-toggle="tab"]').click(this.onTabSelected.bind(this));
        $(document).on('path:selected', this.onPathSelected.bind(this));
    }

    onTabSelected(event) {
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
        let pattern = this.$currentTab && this.$currentTab.length > 0
            ? new RegExp(this.$currentTab.data('pattern')) : undefined;
        let content = '';
        for (let i = 0; i < this.favorites.length; i++) {
            if (!pattern || pattern.exec(this.favorites[i]) !== null) {
                content += '<tr><td class="path"><a href="#" data-path="'
                    + this.favorites[i] + '">' + this.favorites[i] + '</a></td></tr>';
            }
        }
        this.$content.html(content);
        this.$content.find('a').click(this.selectFavorite.bind(this));
    }

    selectFavorite(event) {
        event.preventDefault();
        $(document).trigger('path:select', [$(event.currentTarget).data('path')]);
    }

    onPathSelected(event, path) {
        this.currentPath = path;
        const $toggle = $('.dashboard-browser__favorite-toggle');
        if (this.isFavorite(path)) {
            $toggle.addClass('is-favorite');
        } else {
            $toggle.removeClass('is-favorite');
        }
        $toggle.off('click').click(this.toggleFavorite.bind(this));
    }

    toggleFavorite(event) {
        event.preventDefault();
        if (this.currentPath) {
            this.isFavorite(this.currentPath, true);
            this.profile.set('items', this.favorites);
            this.renderFavorites();
            this.onPathSelected(undefined, this.currentPath);
        }
    }

    isFavorite(path, toggle) {
        let result = undefined;
        for (let i = 0; i < this.favorites.length; i++) {
            if (path === this.favorites[i]) {
                if (toggle) {
                    this.favorites.splice(i, 1);
                    result = false;
                } else {
                    result = true;
                }
                break;
            }
        }
        if (result === undefined) {
            if (toggle) {
                this.favorites.push(path);
                this.favorites.sort();
                return true;
            }
            return false;
        }
        return result;
    }

    clearFavorites(event) {
        event.preventDefault();
        this.favorites = [];
        this.profile.set('items', this.favorites);
        this.renderFavorites();
    }
}

CPM.widgets.register(FavoritesView);
