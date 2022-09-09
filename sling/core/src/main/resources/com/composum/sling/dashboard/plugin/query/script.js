class QueryView extends ViewWidget {

    static selector = '.dashboard-widget__query';

    constructor(element) {
        super(element);
        this.$form = this.$('.dashboard-widget__query-form');
        this.$result = this.$('.dashboard-widget__query-result');
        this.$form.on('submit', this.onSubmit.bind(this));
        $(document).on('query:change', this.onQueryChange.bind(this));
        this.onContentLoaded();
        const url = new URL(window.location.href);
        this.changeQuery(url.parameters.query);
    }

    changeQuery(query) {
        if (query) {
            this.$form.find('input[name="query"]').val(query);
            this.submitQuery();
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
        this.loadContent(this.$result, this.formGetUrl(this.$form));
    }

    onContentLoaded(event, element) {
        const $element = $(element || this.el);
        $(document).trigger('content:loaded', [$element]);
        $element.find('[data-toggle="popover"]').popover({
            html: true
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
}

CPM.widgets.register(QueryView);
