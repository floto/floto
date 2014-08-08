(function() {
	"use strict";

	app.controller("HostsController", function($scope, FlotoService, NotificationService) {
		function update() {
			$scope.manifest = FlotoService.getManifest();
			$scope.hostStates = FlotoService.getHostStates();
		}

		update();

		function merge() {
			var states = $scope.hostStates.states;
			if(!$scope.manifest.hosts) {
				return;
			}
			$scope.manifest.hosts.forEach(function(host) {
				host.state = "unknown";
				if(!states || !states[host.name]) {
					return;
				}
				host.state = states[host.name];
			});
		}
		$scope.$watch("manifest.hosts", merge);
		$scope.$watch("hostStates.states", merge);

		function notifySuccess(title) {
			return function() {
	                        NotificationService.notify({
	                            title: title,
	                            type: 'success'
	                        });
	                    };
		}

		$scope.refresh = update;

		$scope.reloadManifest = function reloadManifest() {
			FlotoService.reloadManifest().then(notifySuccess("Manifest reloaded")).then(update);
		};

		$scope.redeployHosts = function redeployHosts(request) {
			notifySuccess("Hosts redeploy started");
			FlotoService.redeployHosts(request).then(notifySuccess("Hosts redeployed")).then(update);
		};

		$scope.startHosts = function startHosts(request) {
			FlotoService.startHosts(request).then(notifySuccess("Hosts started")).then(update);
		};

		$scope.stopHosts = function stopHosts(request) {
			FlotoService.stopHosts(request).then(notifySuccess("Hosts stopped")).then(update);
		};

		$scope.deleteHosts = function destroyHosts(request) {
			notifySuccess("Hosts delete started");
			FlotoService.deleteHosts(request).then(notifySuccess("Hosts deleted")).then(update);
		};


	});
})();