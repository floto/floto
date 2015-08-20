var app;
(function () {
    'use strict';
    app = angular.module('floto', ['ngResource', 'ui.router', 'luegg.directives', 'gd.ui.jsonexplorer', 'angularMoment', 'toggle-switch']);
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
			}).state('container.log', {
				url: "/log",
				templateUrl: '/js/containers/container-log.html'
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
            }).state('patches', {
                url: "/patches",
                templateUrl: '/js/patches/patches.html'
            });
    });

    app.run(['$rootScope', '$state', '$stateParams', function ($rootScope, $state, $stateParams) {
        $rootScope.$state = $state;
        $rootScope.$stateParams = $stateParams;
    }]);

    app.run(function($rootScope, $http) {
        $http.get(app.urlPrefix+"info").then(function (result) {
            $rootScope.flotoRevision = result.data.flotoRevision;
            $rootScope.flotoVersion = result.data.flotoVersion;
        });
    });

})();