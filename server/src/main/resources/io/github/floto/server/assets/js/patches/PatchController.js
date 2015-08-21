(function () {

    app.controller("PatchController", function ($scope, FlotoService, $stateParams, TaskService, NotificationService) {
        var patchId = $stateParams["patchId"];
        $scope.patchId = patchId;
        $scope.patch = FlotoService.getPatchInfo(patchId);
    });
})();