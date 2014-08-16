(function () {
    "use strict";

    app.controller("TaskController", function ($scope, TaskService, $stateParams) {
        $scope.logs = TaskService.getLogs($stateParams.taskId);
    });
})();