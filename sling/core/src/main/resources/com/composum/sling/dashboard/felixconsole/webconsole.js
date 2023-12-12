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
        // turn all form method="post" into form method="get" to hopefully avoid any changes

        $('form[method="post"]').attr('method', 'get');
    });

})(window, document);
