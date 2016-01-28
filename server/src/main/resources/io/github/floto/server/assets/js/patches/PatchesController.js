(function () {

    app.controller("PatchesController", function ($scope, FlotoService, TaskService, NotificationService) {
        $scope.refresh = function () {
            $scope.patchInfos = FlotoService.getPatches();
        };

        $scope.refresh();

        $scope.createInitialPatch = function () {
            FlotoService.createInitialPatch();
        };

    });
})();