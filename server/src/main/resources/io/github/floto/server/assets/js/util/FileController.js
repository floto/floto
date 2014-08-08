(function () {
	"use strict";

	app.controller("FileController", function ($scope, FlotoService, $stateParams) {
		var filename = decodeURIComponent($stateParams["file"]);
		var containerName = $stateParams["containerName"];
		var hostName = $stateParams["hostName"];
		var url;
		if (containerName) {
			url = "containers/" + containerName + "/" + filename;
		} else if (hostName) {
			url = "hosts/" + hostName + "/" + filename;
		} else {
			throw "Unknown file: " + filename;
		}
		$scope.file = FlotoService.getFilePreview(url);
	});
})();