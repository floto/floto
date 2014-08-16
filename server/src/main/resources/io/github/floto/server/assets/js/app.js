var app;
(function () {
    'use strict';
    app = angular.module('floto', ['ngResource', 'ui.router', 'luegg.directives', 'gd.ui.jsonexplorer', 'angularMoment']);
    app.urlPrefix = '/api/';

    app.config(function ($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise('/containers');

        $stateProvider
            .state('containers', {
                url: "/containers?grouping",
                templateUrl: '/js/containers/containers.html'
            }).state('container', {
                parent: "containers",
                url: "/:containerName",
                templateUrl: '/js/containers/container.html'
            }).state('container.file', {
                url: "/file/:file",
                templateUrl: '/js/util/file.html'
            }).state('hosts', {
                url: "/hosts",
                templateUrl: '/js/hosts/hosts.html'
            }).state('host', {
                parent: "hosts",
                url: "/:hostName",
                templateUrl: '/js/hosts/host.html'
            }).state('host.file', {
                url: "/file/:file",
                templateUrl: '/js/util/file.html'
            }).state('tasks', {
                url: "/tasks",
                templateUrl: '/js/tasks/tasks.html'
            }).state('task', {
                parent: "tasks",
                url: "/:taskId",
                templateUrl: '/js/tasks/task.html'
            }).state('manifest', {
                url: "/manifest",
                templateUrl: '/js/manifest/manifest.html'
            });
    });

    app.run(['$rootScope', '$state', '$stateParams', function ($rootScope, $state, $stateParams) {
        $rootScope.$state = $state;
        $rootScope.$stateParams = $stateParams;
    }]);

    var ws = new WebSocket("ws://localhost:40004/events/");
    ws.onopen = function()
    {
        // Web Socket is connected, send data using send()
        ws.send("Message to send");
        alert("Message is sent...");
    };
    ws.onmessage = function (evt)
    {
        var received_msg = evt.data;
        alert("Message is received... "+evt.data);
    };
    ws.onclose = function()
    {
        // websocket is closed.
        alert("Connection is closed...");
    };
})();