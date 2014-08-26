(function () {
	"use strict";

	app.controller("HostsController", function ($scope, FlotoService) {
		function update() {
			$scope.manifest = FlotoService.getManifest();
			$scope.hostStates = FlotoService.getHostStates();
		}

		function merge() {
			if (!$scope.manifest || !$scope.manifest.hosts) {
				return;
			}
			if (!$scope.hostStates) {
				return;
			}
			var states = $scope.hostStates.states;
			$scope.manifest.hosts.forEach(function (host) {
				host.state = "unknown";
				if (!states || !states[host.name]) {
					return;
				}
				host.state = states[host.name];
			});
		}

		$scope.$watch("manifest.hosts", merge);
		$scope.$watch("hostStates.states", merge);

		$scope.$watch(function () {
			return FlotoService.getManifest();
		}, update);

		$scope.refresh = update;

		$scope.redeployHosts = function redeployHosts(request) {
			FlotoService.redeployHosts(request).then(update);
		};

		$scope.startHosts = function startHosts(request) {
			FlotoService.startHosts(request).then(update);
		};

		$scope.stopHosts = function stopHosts(request) {
			FlotoService.stopHosts(request).then(update);
		};

		$scope.deleteHosts = function destroyHosts(request) {
			FlotoService.deleteHosts(request).then(update);
		};

		$scope.exportHosts = function exportHosts(request) {
			FlotoService.exportHosts(request).then(update);
		};

		});
	})();