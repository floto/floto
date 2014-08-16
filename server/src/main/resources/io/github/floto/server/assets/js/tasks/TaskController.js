(function () {
    "use strict";

    app.controller("TaskController", function ($scope, TaskService, $stateParams) {
        $scope.tasks = TaskService.getTasks();
        $scope.$watch("tasks.length", function() {
            $scope.task = TaskService.getTask($stateParams.taskId);
        }, true);
        $scope.logs = TaskService.getLogs($stateParams.taskId);
        $scope.taskId = $stateParams.taskId;
    });
})();