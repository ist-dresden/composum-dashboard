class QueryView extends ViewWidget {

    static selector = '.dashboard-widget__query';

    constructor(element) {
        super(element);
        this.profile = new Profile('query');
        this.$form = this.$('.dashboard-widget__query-form');
        this.$query = this.$form.find('input[name="query"]');
        this.$result = this.$('.dashboard-widget__query-result');
        this.$spinner = this.$('.dashboard-widget__query-spinner');
        this.$history = this.$('.query-history .dropdown-menu');
        this.history = this.profile.get('history') || [];
        this.historyMax = parseInt(this.$el.data('history-max') || '15');
        this.adjustHistory();
        this.$form.find('.query-templates .dropdown-item').click(this.applyTemplate.bind(this));
        this.$query.on('keyup', this.scanQueryField.bind(this));
        this.$form.on('submit', this.onSubmit.bind(this));
        $(document).on('query:change', this.onQueryChange.bind(this));
        this.onContentLoaded();
        const url = new URL(window.location.href);
        this.changeQuery(url.parameters.query);
    }

    applyTemplate(event) {
        event.preventDefault();
        this.changeQuery($(event.currentTarget).data('query'));
    }

    scanQueryField() {
        const value = this.$query.val();
        this.adjustArgumentStatus(value, 1);
        this.adjustArgumentStatus(value, 2);
        this.adjustArgumentStatus(value, 3);
    }

    adjustArgumentStatus(value, index) {
        if (new RegExp('[$]' + index).exec(value)) {
            this.$form.find('input[name="arg' + index + '"]').removeClass('hidden')
        } else {
            this.$form.find('input[name="arg' + index + '"]').addClass('hidden')
        }
    }

    changeQuery(query) {
        if (query) {
            this.$query.val(query);
            this.scanQueryField();
        }
    }

    onQueryChange(event, parameters) {
        this.changeQuery(parameters.query);
    }

    onSubmit(event) {
        event.preventDefault();
        this.submitQuery();
        return false;
    }

    submitQuery() {
        this.$spinner.addClass('shown');
        this.loadContent(this.$result, this.formGetUrl(this.$form), function ($element) {
            this.onContentLoaded(undefined, $element);
            this.traceQuery(this.$query.val());
            this.$spinner.removeClass('shown');
        }.bind(this));
    }

    onContentLoaded(event, element) {
        const $element = $(element || this.el);
        $(document).trigger('content:loaded', [$element]);
        $element.find('[data-toggle="popover"]').popover({
            html: true,
            boundary: 'viewport'
        }).on('inserted.bs.popover', function (event) {
            const $trigger = $(event.currentTarget);
            const path = $trigger.closest('tr').find('a.path').data('path');
            if (path) {
                this.loadContent($('.popover .popover-body pre'),
                    this.$el.data('popover-uri') + path, function () {
                        $trigger.popover('update');
                    });
            }
        }.bind(this));
        const query = this.formData(this.$form).get('query');
        if (query) {
            CPM.history.pushQuery({'query': query});
        }
    }

    applyHistoryItem(event) {
        event.preventDefault();
        this.changeQuery($(event.currentTarget).data('query'));
    }

    adjustHistory() {
        let content = '';
        for (let i = this.history.length; --i >= 0;) {
            content += '<a class="dropdown-item" href="#" data-query="'
                + this.sanitizeAttr(this.history[i]) + '">'
                + this.sanitizeHtml(this.history[i]) + '</a>';
        }
        this.$history.html(content);
        this.$history.find('.dropdown-item').click(this.applyHistoryItem.bind(this));
    }

    traceQuery(query) {
        if (query && this.history.length < 1 || this.history[this.history.length - 1] !== query) {
            for (let i = 0; i < this.history.length;) {
                if (query === this.history[i]) {
                    this.history.splice(i, 1);
                } else {
                    i++;
                }
            }
            this.history.push(query);
            if (this.history.length > this.historyMax) {
                this.history.splice(0, this.history.length - this.historyMax);
            }
            this.profile.set('history', this.history);
            this.adjustHistory();
        }
    }
}

CPM.widgets.register(QueryView);
