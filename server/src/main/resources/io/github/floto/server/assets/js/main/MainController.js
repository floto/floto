(function() {
	"use strict";

	app.controller("MainController", function($scope, FlotoService, NotificationService) {
		function notifySuccess(title) {
			return function() {
	                        NotificationService.notify({
	                            title: title,
	                            type: 'success'
	                        });
	                    };
		}

		$scope.reloadManifest = function reloadManifest() {
			FlotoService.reloadManifest().then(notifySuccess("Manifest reloaded"));
		};

	});
})();