(function (window, document) {

    /* initializes static widgets in some sling console plugins */
    function initStaticWidgets(elem) {
        // hover states on the static widgets - form elements
        var el = elem ? $(elem) : $(document);
        el.find('button, input[type!=checkbox], .dynhover').hover(
            function () {
                $(this).addClass('ui-state-hover');
            },
            function () {
                $(this).removeClass('ui-state-hover');
            }
        ).addClass('ui-state-default ui-corner-all');
        // fix attribute selector in IE
        el.find('input[type=text], input[type=password], input[type=file]').addClass('inputText');

        // make buttones nicer by applying equal width - not working in IE ;(
        el.find('button, input[type=submit], input[type=reset], input[type=button]').not(".ui-dialog-titlebar-close").each(function (i) {
            var txt = $(this).text();
            var apply = txt && txt.length > 1;
            if (apply) $(this).css('min-width', '8em');
        });

        // add default table styling - colors and fonts from the theme
        el.find('table.nicetable').addClass('ui-widget');
        el.find('table.nicetable th').addClass('ui-widget-header');

        // add default styling for table sorter
        el.find("table.tablesorter tbody").addClass("ui-widget-content");

        // add theme styling to the status line
        el.find('.statline').addClass('ui-state-highlight')

        el.find('table.tablesorter').trigger("update").trigger("applyWidgets");
    }

    $(document).ready(function () {
        initStaticWidgets();

        // $('form[method="post"]').attr('method', 'get');
    });

    if ($("div.composum-dashboard__widget-view meta").length > 0 && $("div.composum-dashboard__widget-view div#content").length > 0) {
        // we've got a felix console page with a frame
        // save all children of div.composum-dashboard__widget-view div#content
        // remove all children of div.composum-dashboard__widget-view
        // move all saved children there

        var $content = $("div.composum-dashboard__widget-view div#content");
        var $widgetView = $("div.composum-dashboard__widget-view");
        var $children = $content.children();
        $widgetView.empty();
        $widgetView.append($children);
    }

})(window, document);
