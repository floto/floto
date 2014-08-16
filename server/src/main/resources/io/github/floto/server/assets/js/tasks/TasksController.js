(function () {
	"use strict";

	app.controller("TasksController", function ($scope, TaskService, NotificationService) {
        $scope.tasks = TaskService.getTasks();
        $scope.refresh = function() {
            TaskService.refreshTasks();
        };

        TaskService.refreshTasks();

		});
	})();