(function () {
	"use strict";
	var configurationFunction = function() {
	};
	window.floto = window.floto || {};
	window.floto.configure = function configure(confFn) {
		configurationFunction = confFn;
	};
	var defaultConfiguration = {
		canRedeploy: true
	};
	app.factory('configuration', function () {
		configurationFunction(defaultConfiguration);
		return defaultConfiguration;
	});

	app.run(function($rootScope, $state, configuration) {
		$rootScope.configuration = configuration;
	});
})();