(function () {
    "use strict";
    var classMap = {
        error: "danger",
        warn: "warning"
    }

    app.controller("TaskController", function ($scope, TaskService, $stateParams) {
        $scope.tasks = TaskService.getTasks();
        $scope.$watch("tasks.length", function() {
            $scope.task = TaskService.getTask($stateParams.taskId);
        }, true);
        $scope.logs = TaskService.getLogs($stateParams.taskId);
        $scope.logs.$promise.then(function(logs) {
            $scope.logs.logs.forEach(function(logEntry) {
                logEntry.class = classMap[logEntry.level];
            });
        });
        $scope.taskId = $stateParams.taskId;
    });
})();