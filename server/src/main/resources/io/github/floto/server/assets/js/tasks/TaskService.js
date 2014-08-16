(function () {
    "use strict";
    app.factory('TaskService', function ($resource, $http, $rootScope) {
        var TaskService = {};

        var tasks = TaskService.tasks = [];

        TaskService.getTasks = function getTasks() {
            return this.tasks;
        };

        TaskService.refreshTasks = function refreshTasks() {
            $resource(app.urlPrefix + 'tasks').get().$promise.then(function (taskState) {
                tasks.length = 0;
                Array.prototype.push.apply(tasks, taskState.tasks);
            });
        };

        TaskService.getLogs = function getLogs(taskId) {
            return $resource(app.urlPrefix + 'tasks/'+taskId+'/logs').get();
        };

        return TaskService;
    });

})();