(function () {
	"use strict";

	app.controller("MainController", function ($scope, FlotoService, NotificationService) {
		function notifySuccess(title) {
			return function () {
				NotificationService.notify({
					title: title,
					type: 'success'
				});
			};
		}

		$scope.compileManifest = function compileManifest() {
			FlotoService.compileManifest().then(notifySuccess("Manifest compiled"));
		};

	});
})();