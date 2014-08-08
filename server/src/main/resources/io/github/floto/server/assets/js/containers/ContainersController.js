(function() {
	"use strict";

	app.controller("ContainersController", function($scope, FlotoService, NotificationService) {
		function update() {
			$scope.manifest = FlotoService.getManifest();
			$scope.containerStates = FlotoService.getContainerStates();
		}

		update();

		function merge() {
			var states = $scope.containerStates.states;
			if(!$scope.manifest.containers) {
				return;
			}
			$scope.manifest.containers.forEach(function(container) {
				container.state = "unknown";
				if(!states || !states[container.name]) {
					return;
				}
				container.state = states[container.name];
			});
		}
		$scope.$watch("manifest.containers", merge);
		$scope.$watch("containerStates.states", merge);

		function notifySuccess(title) {
			return function() {
	                        NotificationService.notify({
	                            title: title,
	                            type: 'success'
	                        });
	                    };
		}

		$scope.reloadManifest = function reloadManifest() {
			FlotoService.reloadManifest().then(notifySuccess("Manifest reloaded")).then(update);
		};

		$scope.redeployContainers = function redeployContainers(request) {
			FlotoService.redeployContainers(request).then(notifySuccess("Containers redeployed")).then(update);
		};

		$scope.startContainers = function startContainers(request) {
			FlotoService.startContainers(request).then(notifySuccess("Containers started")).then(update);
		};

		$scope.stopContainers = function stopContainers(request) {
			FlotoService.stopContainers(request).then(notifySuccess("Containers stopped")).then(update);
		};

		$scope.purgeContainerData = function purgeContainerData(request) {
			FlotoService.purgeContainerData(request).then(notifySuccess("Container data purged"));
		};

		$scope.refresh = update;
	});
})();