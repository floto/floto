(function () {

    app.controller("PatchesController", function ($scope, FlotoService, TaskService, NotificationService) {
        $scope.refresh = function () {
        };

        $scope.createInitialPatch = function () {
            FlotoService.createInitialPatch();
        };

    });
})();