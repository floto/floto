(function () {
    "use strict";

    app.controller("ManifestController", function ($scope, FlotoService) {
        $scope.$watch(function () {
            return FlotoService.getManifest();
        }, function (manifest) {
            $scope.manifest = manifest;
        });
    });
})();