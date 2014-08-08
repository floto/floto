(function () {
	"use strict";

	var lastFile = null;

	app.run(function($rootScope) {
		$rootScope.$on("$stateChangeStart", function(event, toState, toParams, fromState, fromParams) {
			if(fromState.name === "container.file") {
				lastFile = fromParams.file;

			}
		});
	});

	app.controller("ContainerController", function ($scope, $stateParams, FlotoService, $state) {
		var containerName = $stateParams["containerName"];
		$scope.container = {
			name: containerName
		};
		$scope.fileTargets = [
			{name: "Log", file: "log"},
			{name: "Buildlog", file: "buildlog"},
			{name: "Image", file: "dockerfile%2Fimage"},
			{name: "Container", file: "dockerfile%2Fcontainer"}
		];

		FlotoService.getContainerTemplates(containerName).then(function (templates) {
			templates.forEach(function (template) {
				$scope.fileTargets.push({
					name: template.name,
					file: encodeURIComponent("template/"+template.destination),
					destination: template.destination
				});
			});
			$scope.fileTargets.forEach(function(target) {
				if(target.file === lastFile) {
					$state.go("container.file", {file: lastFile});
				}
			});
		});

	});
})();