class PathLink extends ViewWidget {

    static selector = '.composum-dashboard__navbar .path-link';

    constructor(element) {
        super(element);
        this.$el.click(function (event) {
            event.preventDefault();
            const browser = Widgets.getView(this.$el.closest(BrowserPage.selector), BrowserPage);
            if (browser) {
                const path = browser.getCurrentPath();
                if (path) {
                    const url = this.$el.data('path-uri')
                        .replaceAll(/\\\$\\{path}/g, path)
                        .replaceAll(/\[([^\]]*)]/g, /\.[^.]+$/.exec(path) ? '' : '$1');
                    window.open(url, this.$el.data('target'));
                }
            }
            return false;
        }.bind(this));
    }
}

CPM.widgets.register(PathLink);

class ToolLink extends ViewWidget {

    static selector = '.composum-dashboard__navbar .tool-link';

    constructor(element) {
        super(element);
        this.$el.click(function (event) {
            event.preventDefault();
            $(document).trigger('tool:toggle', [this.$el.data('tool-uri')]);
            return false;
        }.bind(this));
    }

    static setActive(toolUri) {
        $(ToolLink.selector).removeClass('active');
        if (toolUri) {
            $(ToolLink.selector + '[data-tool-uri="' + toolUri + '"]').addClass('active');
        }
    }
}

CPM.widgets.register(ToolLink);

class StatusLine extends ViewWidget {

    static selector = '.dashboard__status-line';

    constructor(element) {
        super(element);
        $(document).on('path:selected', this.onPathSelected.bind(this));
    }

    onPathSelected(event, path) {
        this.$el.attr('value', path);
    }
}

CPM.widgets.register(StatusLine);

class BrowserTree extends ViewWidget {

    static selector = '.dashboard-browser__tree';

    constructor(element) {
        super(element)
        const treeOptions = {
            'plugins': [
                'types',
                'unique',
                'wholerow'
            ],
            'core': {
                'animation': false,
                'data': this.nodeData.bind(this),
                'cache': false,
                'load_open': true,
                'multiple': false,
                'force_text': true,
                'themes': {
                    'name': 'proton'
                }
            },
            'types': {
                'default': {'icon': 'fa fa-cube text-muted'},
                'synthetic': {'icon': 'fa fa-circle-thin text-muted'},
                'summary': {'icon': 'fa fa-hand-o-right text-info'},
                'root': {'icon': 'fa fa-sitemap text-muted'},
                'system': {'icon': 'fa fa-cogs text-muted'},
                'activities': {'icon': 'fa fa-signal text-muted'},
                'nodetypes': {'icon': 'fa fa-tags text-muted'},
                'nodetype': {'icon': 'fa fa-tag text-muted'},
                'versionstorage': {'icon': 'fa fa-history text-muted'},
                'folder': {'icon': 'fa fa-folder-o'},
                'resource-folder': {'icon': 'fa fa-folder-o'},
                'orderedfolder': {'icon': 'fa fa-folder text-muted'},
                'registry': {'icon': 'fa fa-database text-muted'},
                'package': {'icon': 'fa fa-file-archive-o'},
                'resource-package': {'icon': 'fa fa-file-archive-o'},
                'tenant': {'icon': 'fa fa-university text-info'},
                'component': {'icon': 'fa fa-puzzle-piece text-info'},
                'container': {'icon': 'fa fa-cubes text-info'},
                'element': {'icon': 'fa fa-cube text-info'},
                'site': {'icon': 'fa fa-sitemap text-info'},
                'siteconfiguration': {'icon': 'fa fa-ellipsis-h text-muted'},
                'page': {'icon': 'fa fa-globe text-info'},
                'pagecontent': {'icon': 'fa fa-ellipsis-h text-muted'},
                'page-designer': {'icon': 'fa fa-cube text-info'},
                'resource-designer': {'icon': 'fa fa-file-code-o text-muted'},
                'resource-redirect': {'icon': 'fa fa-share text-info'},
                'resource-parsys': {'icon': 'fa fa-ellipsis-v text-muted'},
                'resource-console': {'icon': 'fa fa-laptop text-muted'},
                'resource-pckgmgr': {'icon': 'fa fa-laptop text-muted'},
                'resource-path': {'icon': 'fa fa-bookmark-o text-muted'},
                'resource-resources': {'icon': 'fa fa-filter text-muted'},
                'resource-strings': {'icon': 'fa fa-filter text-muted'},
                'resource-felix': {'icon': 'fa fa-cog text-muted'},
                'resource-guide': {'icon': 'fa fa-book text-muted'},
                'resource-servlet': {'icon': 'fa fa-cog text-muted'},
                'acl': {'icon': 'fa fa-key text-muted'},
                'authorizablefolder': {'icon': 'fa fa-diamond text-muted'},
                'group': {'icon': 'fa fa-group text-muted'},
                'service': {'icon': 'fa fa-cog text-info'},
                'user': {'icon': 'fa fa-user'},
                'linkedfile': {'icon': 'fa fa-link text-muted'},
                'file': {'icon': 'fa fa-file-o'},
                'resource': {'icon': 'fa fa-file-o'},
                'resource-file': {'icon': 'fa fa-file-o text-muted'},
                'file-image': {'icon': 'fa fa-file-image-o text-info'},
                'resource-image': {'icon': 'fa fa-file-image-o text-muted'},
                'file-video': {'icon': 'fa fa-file-video-o text-info'},
                'resource-video': {'icon': 'fa fa-file-video-o text-muted'},
                'file-text': {'icon': 'fa fa-file-text-o text-info'},
                'resource-text': {'icon': 'fa fa-file-text-o text-muted'},
                'file-text-plain': {'icon': 'fa fa-file-text-o text-info'},
                'file-text-x-log': {'icon': 'fa fa-file-text-o text-info'},
                'resource-text-plain': {'icon': 'fa fa-file-code-o text-muted'},
                'file-text-html': {'icon': 'fa fa-globe text-info'},
                'resource-text-html': {'icon': 'fa fa-file-code-o text-muted'},
                'file-text-css': {'icon': 'fa fa-file-code-o text-info'},
                'resource-text-css': {'icon': 'fa fa-file-code-o text-muted'},
                'file-javascript': {'icon': 'fa fa-file-code-o text-info'},
                'resource-javascript': {'icon': 'fa fa-file-code-o text-muted'},
                'file-text-javascript': {'icon': 'fa fa-file-code-o text-info'},
                'resource-text-javascript': {'icon': 'fa fa-file-code-o text-muted'},
                'file-text-x-java-properties': {'icon': 'fa fa-file-code-o text-info'},
                'file-text-x-java-source': {'icon': 'fa fa-file-code-o text-info'},
                'resource-text-x-java-source': {'icon': 'fa fa-file-code-o text-muted'},
                'file-octet-stream': {'icon': 'fa fa-file-code-o text-info'},
                'resource-octet-stream': {'icon': 'fa fa-file-code-o text-muted'},
                'file-pdf': {'icon': 'fa fa-file-pdf-o text-info'},
                'resource-pdf': {'icon': 'fa fa-file-pdf-o text-muted'},
                'file-zip': {'icon': 'fa fa-file-archive-o text-info'},
                'resource-zip': {'icon': 'fa fa-file-archive-o text-muted'},
                'file-java-archive': {'icon': 'fa fa-file-archive-o text-info'},
                'resource-java-archive': {'icon': 'fa fa-file-archive-o text-muted'},
                'asset': {'icon': 'fa fa-picture-o text-info'},
                'assetcontent': {'icon': 'fa fa-picture-o text-muted'},
                'file-binary': {'icon': 'fa fa-file-o text-info'},
                'resource-binary': {'icon': 'fa fa-file-o text-muted'},
                'resource-syntheticresourceproviderresource': {'icon': 'fa fa-code text-muted'},
                'clientlibraryfolder': {'icon': 'fa fa-share-square-o'}
            }
        };
        this.$jstree = this.$el.jstree(treeOptions);
        this.jstree = this.$el.jstree(true);
        this.$jstree
            .on('select_node.jstree', this.onNodeSelected.bind(this));
        $(document)
            .on('page:changed', this.onPageChanged.bind(this))
            .on('path:select', this.doSelectPath.bind(this));
        const path = this.$el.data('path');
        if (path) {
            setTimeout(function () {
                this.openNode(path, function (path) {
                    $(document).trigger('path:selected', [path]);
                }.bind(this), true);
            }.bind(this), 200);
        }
    }

    browserUrl(path) {
        return this.$el.data('page-url') + path;
    }

    doSelectPath(event, path) {
        const selected = this.getSelectedNode();
        if (!selected || path !== selected.original.path) {
            this.openNode(path);
        }
    }

    triggerPathSelected(path) {
        $(document).trigger('path:selected', [path]);
        CPM.history.pushUri(this.browserUrl(path));
    }

    onPageChanged(event, url) {
        const tree = this;
        $.ajax({
            type: 'GET',
            url: tree.$el.data('tree-url'),
            data: {
                url: url
            },
            success: function (result, msg, xhr) {
                const selected = tree.getSelectedNode();
                let selectedPath = (selected ? selected.original.path : '')
                    .replace(/\/jcr:content$/, '');
                if (selectedPath !== result.path) {
                    tree.openNode(result.path);
                }
            },
            async: true,
            cache: false
        });
    }

    onNodeSelected(event, data) {
        const node = this.jstree.get_node(data.node.id);
        if (node) {
            this.jstree.open_node(node, function () {
                if (!this.suppressEvent) {
                    this.triggerPathSelected(node.original.path);
                }
            }.bind(this));
        }
    }

    getSelectedPath() {
        const node = this.getSelectedNode();
        return node && node.original && node.original.path ? node.original.path : undefined;
    }

    getSelectedNode() {
        const selectedIds = this.jstree.get_selected();
        if (selectedIds.length > 0) {
            return this.jstree.get_node(selectedIds[0]);
        }
        return undefined;
    }

    dataUrl(node) {
        var path = node.original && node.original.path ? node.original.path : '/';
        return this.$el.data('tree-url') + path;
    }

    nodeData(node, callback) {
        const tree = this;
        $.ajax({
            type: 'GET',
            url: tree.dataUrl(node),
            success: function (result, msg, xhr) {
                result.id = tree.nodeId(result.path);
                if (result.children) {
                    for (var i = 0; i < result.children.length; i++) {
                        result.children[i].id = tree.nodeId(result.children[i].path);
                    }
                }
                callback.call(tree.$jstree, result);
            },
            async: true,
            cache: false
        });
    }

    nodeId(id) {
        if (id && (typeof id !== 'string' || id.indexOf('CBT_') !== 0)) {
            if (Array.isArray(id)) id = id.join('/');
            id = ('CBT_' + btoa(encodeURIComponent(id))).replace(/=/g, '-').replace(/[/]/g, '_');
        }
        return id;
    }

    openNode(path, callback, suppressEvent) {
        this.jstree.deselect_all();
        const names = $.isArray(path) ? path : path.split('/');
        let index = 1;
        const tree = this;
        const drilldown = function (path) {
            const id = tree.nodeId(path);
            const $node = tree.$el.find('#' + id);
            tree.jstree.open_node($node, function (node, wasNotOpened) {
                if (index < names.length) {
                    drilldown(path + (path === '/' ? '' : '/') + names[index++]);
                } else {
                    tree.suppressEvent = suppressEvent;
                    try {
                        tree.jstree.select_node($node);
                        setTimeout(function () {
                            tree.scrollIntoView($node);
                            if (callback) {
                                callback(path, $node[0])
                            }
                        }.bind(this), 100);
                    } finally {
                        delete tree.suppressEvent;
                    }
                }
            });
        };
        drilldown('/');
    }

    scrollIntoView($node) {
        var $panel = this.$jstree.closest('.dashboard-browser__tree-panel ');
        var nodePos = $node.position();
        if (nodePos) {
            var nodeTop = nodePos.top;
            var scrollTop = $panel.scrollTop();
            var scrollHeight = $panel.height();
            if (nodeTop < scrollTop + scrollHeight / 5) {
                $panel.scrollTop(nodeTop - scrollHeight / 4);
            } else if (nodeTop > scrollTop + scrollHeight - scrollHeight / 5) {
                $panel.scrollTop(nodeTop - scrollHeight + scrollHeight / 4);
            }
        }
    }
}

CPM.widgets.register(BrowserTree);

class BrowserPanel extends ViewWidget {

    constructor(element) {
        super(element);
        this.profile = new Profile('browser');
        $(document).on('content:loaded', this.attachLinkHandler.bind(this));
    }

    attachLinkHandler(event, element) {
        super.attachLinkHandler(event, element)
        $(element || this.el).find('a.path').click(function (event) {
            event.preventDefault();
            let path = $(event.currentTarget).data('path');
            if (path) {
                path = path.replaceAll(/\/_jcr_/g, '/jcr:');
                $(document).trigger('path:select', [path]);
            }
            return false;
        }.bind(this));
    }
}

class BrowserTool extends BrowserPanel {

    static selector = '.dashboard-browser__tool';

    constructor(element) {
        super(element);
        this.$parent = this.$el.closest('.dashboard-browser__right-panel');
        this.showTool(this.profile.get('currentTool'));
        $(document).on('tool:toggle', function (event, toolUri) {
            this.toggleTool(toolUri);
        }.bind(this));
    }

    setCurrentTool(toolUri) {
        this.currentTool = toolUri;
        this.profile.set('currentTool', toolUri || '');
    }

    toggleTool(toolUri) {
        this.showTool(this.currentTool === toolUri ? undefined : toolUri);
    }

    showTool(toolUri) {
        ToolLink.setActive();
        if (toolUri) {
            $.ajax({
                type: 'GET',
                url: toolUri,
                success: function (content) {
                    this.setCurrentTool(toolUri);
                    this.$el.html(content);
                    this.$parent.addClass('tool-visible');
                    ToolLink.setActive(toolUri);
                    this.onContentLoaded(this.$el);
                }.bind(this),
                async: true,
                cache: false
            });
        } else {
            this.setCurrentTool();
            this.$parent.removeClass('tool-visible');
            this.$el.html('');
        }
    }
}

CPM.widgets.register(BrowserTool);

class BrowserView extends BrowserPanel {

    static selector = '.dashboard-browser__view';

    constructor(element) {
        super(element);
        this.loadContent(undefined, function () {
            this.$el.find('.dashboard-browser__action-reload').click(this.reload.bind(this));
            this.$el.find('.dashboard-browser__tabs a[data-toggle="tab"]').on('shown.bs.tab', this.onTabShown.bind(this));
            this.showTab(this.profile.get('currentTab'), true);
        }.bind(this))
        $(document).on('path:selected', this.onPathSelected.bind(this));
    }

    reload(event) {
        this.onPathSelected(event, this.currentPath, true);
    }

    onPathSelected(event, path, force) {
        if (force || path !== this.currentPath) {
            this.currentPath = path;
            const $tab = this.$el.find('.dashboard-browser__tabs-content .tab-pane').data('loaded', 'false');
            this.loadContent(this.activeTabId());
        }
    }

    activeTabId() {
        const active = this.$el.find('.dashboard-browser__tabs .nav-link.active').attr('aria-controls');
        return active || this.$el.find('.dashboard-browser__tabs .nav-link').first().attr('aria-controls')
    }

    $tabPane(tabId) {
        return tabId ? this.$el.find('.dashboard-browser__tabs-content .tab-pane[id="' + tabId + '"]') : undefined;
    }

    onTabShown(event) {
        const tabId = $(event.target).attr('aria-controls');
        this.profile.set('currentTab', tabId);
        const $tab = this.$tabPane(tabId);
        if ($tab.data('loaded') !== 'true') {
            this.loadContent(tabId);
        }
    }

    showTab(tabId, force) {
        const $tab = this.$el.find('.dashboard-browser__tabs .nav-link[aria-controls="' + tabId + '"]');
        if ($tab.length > 0) {
            $tab.tab('show');
        } else if (force) {
            this.$el.find('.dashboard-browser__tabs .nav-link').first().tab('show');
        }
    }

    dataUrl(tabId, path) {
        return (tabId ? this.$el.data('view-tab').replaceAll('#id#', tabId) : this.$el.data('view-url'))
            + (path ? path : '');
    }

    loadContent(tabId, callback) {
        if (!tabId || this.currentPath) {
            $.ajax({
                type: 'GET',
                url: this.dataUrl(tabId, this.currentPath),
                success: function (content) {
                    if (tabId) {
                        const $tab = this.$tabPane(tabId);
                        $tab.html(content);
                        $tab.data('loaded', 'true');
                        this.onContentLoaded($tab);
                    } else {
                        this.$el.html(content);
                        this.onContentLoaded();
                    }
                    if (callback) {
                        callback();
                    }
                }.bind(this),
                async: true,
                cache: false
            });
        }
    }

    onContentLoaded(event, element) {
        super.onContentLoaded(element);
        $(element || this.el).find('.preview iframe').on('load.preview', function (event) {
            var url = event.currentTarget.contentDocument.URL;
            //$(document).trigger('page:changed', [url]); // FIXME...
        }.bind(this));
    }
}

CPM.widgets.register(BrowserView);

class BrowserPage extends ViewWidget {

    static selector = '.dashboard-browser__page-body';

    constructor(element) {
        super(element);
        this.tree = Widgets.getView(this.$(BrowserTree.selector), BrowserTree);
    }

    getCurrentPath() {
        return this.tree.getSelectedPath();
    }
}

CPM.widgets.register(BrowserPage);
