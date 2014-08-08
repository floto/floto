(function () {
	"use strict";

	var lastFile = null;

	app.run(function($rootScope) {
		$rootScope.$on("$stateChangeStart", function(event, toState, toParams, fromState, fromParams) {
			if(fromState.name === "host.file") {
				lastFile = fromParams.file;

			}
		});
	});

	app.controller("HostController", function ($scope, $stateParams, FlotoService, $state) {
		var hostName = $stateParams["hostName"];
		$scope.host = {
			name: hostName
		};
		$scope.fileTargets = [
//			{name: "Log", file: "log"},
//			{name: "Buildlog", file: "buildlog"},
//			{name: "Image", file: "dockerfile%2Fimage"},
			//{name: "Container", file: "dockerfile%2Fcontainer"}
		];

		FlotoService.getHostTemplates(hostName).then(function (templates) {
			templates.forEach(function (template) {
				$scope.fileTargets.push({
					name: template.name,
					file: encodeURIComponent("template/"+template.destination),
					destination: template.destination
				});
			});
			$scope.fileTargets.forEach(function(target) {
				if(target.file === lastFile) {
					$state.go("host.file", {file: lastFile});
				}
			});
		});

	});
})();