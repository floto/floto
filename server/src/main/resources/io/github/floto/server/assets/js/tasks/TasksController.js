(function () {
    "use strict";
    var classMap = {
        SUCCESS: "success",
        ERROR: "danger"
    }
    var iconMap = {
        SUCCESS: "glyphicon-ok",
        ERROR: "glyphicon-remove",
        RUNNING: "glyphicon-play"
    }

    app.controller("TasksController", function ($scope, TaskService, NotificationService) {
        $scope.tasks = TaskService.getTasks();

        $scope.$watchCollection("tasks", function(task) {
            $scope.tasks.forEach(function(task) {
                task.class = classMap[task.status];
                task.icon = iconMap[task.status];
            });
        });
        $scope.refresh = function () {
            TaskService.refreshTasks();
        };

        TaskService.refreshTasks();

    });
})();