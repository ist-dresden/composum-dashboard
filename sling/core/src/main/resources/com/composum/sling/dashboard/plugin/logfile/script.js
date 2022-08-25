class Logger {

    constructor(element) {
        this.el = element;
        this.$el = $(element);
        this.el.view = this;
        this.scrollToEnd();
        setTimeout(this.tail.bind(this), 10000);
    }

    tail() {
        const logger = this;
        $.ajax({
            type: 'GET',
            url: logger.$el.data('tail'),
            success: function (result, msg, xhr) {
                const scrollToEnd = Math.abs(logger.el.scrollTop + logger.el.clientHeight - logger.el.scrollHeight) < 20;
                logger.el.value += result;
                if (scrollToEnd) {
                    logger.scrollToEnd();
                }
            },
            complete: function (xhr, msg) {
                setTimeout(logger.tail.bind(logger), 10000);
            },
            async: true,
            cache: false
        });
    }

    scrollToEnd() {
        this.el.scrollTop = this.el.scrollHeight;
    }
}

document.addEventListener('DOMContentLoaded', function () {
    $('.dashboard-widget__logfile textarea').each(function () {
        new Logger(this)
    });
    $('.dasboard-widget__logfile-view .nav-tabs a[data-toggle="tab"]').on('shown.bs.tab', function (event) {
        const $tab = $(event.target);
        const $pane = $tab.closest('.dasboard-widget__logfile-view')
            .find('.tab-content .tab-pane[aria-labelledby="' + $tab.attr('id') + '"]');
        const $view = $pane.find('.dashboard-widget__logfile textarea');
        const view = $view[0].view;
        if (!view.initialized) {
            view.initialized = true;
            view.scrollToEnd();
        }
    })
});
