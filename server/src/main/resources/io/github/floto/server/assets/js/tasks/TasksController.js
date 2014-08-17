(function () {
    "use strict";
    var classMap = {
        success: "success",
        error: "danger"
    }
    var iconMap = {
        success: "glyphicon-ok",
        error: "glyphicon-remove",
        running: "glyphicon-play"
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