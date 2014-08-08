var app;
(function () {
	'use strict';
	app = angular.module('floto', ['ngResource', 'ui.router', 'luegg.directives']);
	app.urlPrefix = '/api/';

	app.config(function ($stateProvider, $urlRouterProvider) {
		$urlRouterProvider.otherwise('/containers')

		$stateProvider.state('containers', {
			url: "/containers",
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
			});
	});
})();