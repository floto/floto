(function () {
	"use strict";
	var configurationFunction = function() {
	};
	window.floto = window.floto || {};
	window.floto.configure = function configure(confFn) {
		configurationFunction = confFn;
	};
	var defaultConfiguration = {
		canRedeploy: true,
        defaultDeploymentMode: "fromBaseImage"
	};
	app.factory('configuration', function () {
		configurationFunction(defaultConfiguration);
		return defaultConfiguration;
	});

	app.run(function($rootScope, $state, configuration) {
		$rootScope.configuration = configuration;

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