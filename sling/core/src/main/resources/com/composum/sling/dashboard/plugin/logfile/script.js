class LogfileView extends ViewWidget {

    static selector = '.dashboard-widget__logfile textarea';

    constructor(element) {
        super(element);
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

CPM.widgets.register(LogfileView);

class LogfileTabs extends ResumingTabs {

    static selector = '.logfile-tabs';

    constructor(element) {
        super(element, undefined, function (event, tabId, $tab) {
            const $view = $tab.find('.dashboard-widget__logfile textarea');
            const view = Widgets.getView($view, LogfileView);
            if (view && !view.initialized) {
                view.initialized = true;
                view.scrollToEnd();
            }
        });
    }
}

CPM.widgets.register(LogfileTabs);
