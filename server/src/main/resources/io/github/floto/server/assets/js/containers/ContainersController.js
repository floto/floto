(function() {
	"use strict";

	app.controller("ContainersController", function($scope, FlotoService, NotificationService, $state, $stateParams) {
        $scope.groupings = {
            host: {},
            image: {}
        };
		function update() {
			$scope.manifest = FlotoService.getManifest();
			$scope.containerStates = FlotoService.getContainerStates();

		}

        function updateGroups() {
            $scope.groups = $scope.groupings[$stateParams.grouping];
        }

        $scope.$state = $state;
        $scope.$watch(function() {
            return $stateParams.grouping
        }, function(grouping) {
            $scope.grouping = grouping;
            updateGroups();
        });

		function merge() {
            if(!$scope.manifest || !$scope.manifest.containers) {
                return;
            }
            if(!$scope.containerStates) {
                return;
            }
			var states = $scope.containerStates.states;
			$scope.manifest.containers.forEach(function(container) {
				container.state = "unknown";
				if(!states || !states[container.name]) {
					return;
				}
				container.state = states[container.name];
			});

            $scope.groupings = {
                host: {},
                image: {}
            };
            $scope.manifest.containers.forEach(function(container) {
                var hostGroup = $scope.groupings.host[container.host] || {title: container.host, containers: []};
                $scope.groupings.host[container.host] = hostGroup;
                hostGroup.containers.push(container);

                var imageGroup = $scope.groupings.image[container.image] || {title: container.image, containers: []};
                $scope.groupings.image[container.image] = imageGroup;
                imageGroup.containers.push(container);
            });
            $scope.containers = $scope.manifest.containers;
            updateGroups();

		}
		$scope.$watch("manifest.containers", merge);
		$scope.$watch("containerStates.states", merge);

        $scope.$watch(function() {
            return FlotoService.getManifest()
        }, update);

		function notifySuccess(title) {
			return function() {
	                        NotificationService.notify({
	                            title: title,
	                            type: 'success'
	                        });
	                    };
		}

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