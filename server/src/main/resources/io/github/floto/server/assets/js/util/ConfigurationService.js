(function () {
	"use strict";
	var configurationFunctions = [];
	window.floto = window.floto || {};

	window.floto.configure = function configure(confFn) {
		configurationFunctions.push(confFn);
	};
	var defaultConfiguration = {
		canRedeploy: true,
        defaultDeploymentMode: "fromBaseImage",
        armed: false
	};
	app.factory('configuration', function () {
        configurationFunctions.forEach(function (configurationFunction) {
            configurationFunction(defaultConfiguration)
        });
        return defaultConfiguration;
	});

	app.run(function($rootScope, $state, configuration) {
		$rootScope.configuration = configuration;
        $rootScope.armed = configuration.armed;

        // Initialize deployment modes
        $rootScope.configuration.deploymentModes = [
            {
                name: "fromRootImage",
                caption: "From Root Image"
            },
            {
                name: "fromBaseImage",
                caption: "From Base Image"
            },
            {
                name: "containerRebuild",
                caption: "Recreate container"
            }
        ];

        // Mark default deployment mode
        $rootScope.configuration.deploymentModes.forEach(function(mode) {
            if(mode.name === $rootScope.configuration.defaultDeploymentMode) {
                mode.default = true;
                $rootScope.configuration.defaultDeploymentModeCaption = mode.caption;
            }
        })
	});
})();