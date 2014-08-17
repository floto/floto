(function () {
    "use strict";

    app.controller("TaskController", function ($scope, TaskService, $stateParams) {
        $scope.tasks = TaskService.getTasks();
        $scope.$watchCollection("tasks", function () {
            $scope.task = TaskService.getTask($stateParams.taskId);
        });
        $scope.taskId = $stateParams.taskId;
        $scope.autoScroll = true;
    });
})();