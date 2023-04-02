class QueryView extends ViewWidget {

    static selector = '.dashboard-widget__query';

    constructor(element) {
        super(element);
        this.$form = this.$('.dashboard-widget__query-form');
        this.$query = this.$form.find('input[name="query"]');
        this.$result = this.$('.dashboard-widget__query-result');
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
        this.changeQuery($(event.currentTarget).text());
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
