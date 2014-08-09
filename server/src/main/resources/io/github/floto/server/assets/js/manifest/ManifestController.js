(function () {
    "use strict";

    app.controller("ManifestController", function ($scope, FlotoService) {
        $scope.manifest = FlotoService.getManifest();
    });
})();