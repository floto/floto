(function () {
    "use strict";
    app.factory('TaskService', function ($resource, $http, $rootScope, $q, NotificationService) {
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
            return $resource(app.urlPrefix + 'tasks/' + taskId + '/logs').get();
        };

        TaskService.getTask = function getTask(taskId) {
            return _.find(this.tasks, {id: taskId});
        };

        var taskCompletionPromises = {};
        TaskService.getTaskCompletionPromise = function getTaskCompletionPromise(taskId) {
            var deferred = taskCompletionPromises[taskId] = taskCompletionPromises[taskId] || $q.defer();
            var message = {
                command: "registerCompletionListener",
                taskId: taskId
            };
            sendMessage(message);
            return deferred.promise;
        };

        function sendMessage(message) {
            wsPromise.then(function (ws) {
                ws.send(JSON.stringify(message));
            });
        }

        var loc = window.location;
        var websocketUri;
        if (loc.protocol === "https:") {
            websocketUri = "wss:";
        } else {
            websocketUri = "ws:";
        }
        websocketUri += "//" + loc.host;
        websocketUri += loc.pathname + "tasks/_websocket";

        var wsPromise;

        function connectWebSocket() {
            var deferred = $q.defer();
            var ws = new WebSocket(websocketUri);

            ws.onopen = function () {
                deferred.resolve(ws);
            };
            ws.onmessage = function (evt) {
                var message = JSON.parse(evt.data);
                if (message.type === "taskComplete") {
                    TaskService.refreshTasks();
                    var taskId = message.taskId;
                    var deferred = taskCompletionPromises[taskId];
                    if (message.status === "success") {
                        deferred.resolve(null);
                    } else {
                        NotificationService.notify({
                            title: message.errorMessage,
                            text: '<a href="#/tasks/' + taskId + '">Click for details (Task #' + taskId + ')</a>',
                            type: 'error'
                        });
                        deferred.reject("TODO: Error");
                    }
                }
            };
            ws.onclose = function () {
                // websocket is closed.
                console.log("Connection is closed...");
            };
            wsPromise = deferred.promise;
        }

        connectWebSocket();

        TaskService.httpPost = function httpPost(url, request) {
            return $http.post(url, request).then(function (result) {
                var taskId = result.data.taskId;
                return TaskService.getTaskCompletionPromise(taskId);
            });
        }

        return TaskService;
    });

})();