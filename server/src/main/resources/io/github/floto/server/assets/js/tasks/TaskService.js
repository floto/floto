(function () {
    "use strict";
    app.factory('TaskService', function ($resource, $http, $rootScope, $q, NotificationService) {
        var TaskService = {};

        var tasks = TaskService.tasks = [];

        TaskService.getTasks = function getTasks() {
            return this.tasks;
        };

        TaskService.refreshTasks = function refreshTasks() {
            $resource(app.urlPrefix + 'tasks').query().$promise.then(function (serverTasks) {
                serverTasks = _.sortBy(serverTasks, "startDate");
                serverTasks.reverse();
                tasks.length = 0;
                Array.prototype.push.apply(tasks, serverTasks);

            });
        };

        TaskService.getLogs = function getLogs(taskId) {
            return $resource(app.urlPrefix + 'tasks/' + taskId + '/logs').query();
        };

        TaskService.getTask = function getTask(taskId) {
            return _.find(this.tasks, {id: taskId});
        };

        var taskCompletionPromises = {};
        var logSubscriptions = {};
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

        function connectWebSocket(timeout) {
            var deferred = $q.defer();
            wsPromise = deferred.promise;
            setTimeout(function () {
                var ws = new WebSocket(websocketUri);

                ws.onopen = function () {
                    console.log("Connected")
                    deferred.resolve(ws);
                };
                ws.onmessage = function (evt) {
                    var message = JSON.parse(evt.data);
                    if (message.type === "taskComplete") {
                        TaskService.refreshTasks();
                        var taskId = message.taskId;
                        var deferred = taskCompletionPromises[taskId];
                        var linkText = ' <a onclick="$(this).closest(\'.ui-pnotify\').find(\'.ui-pnotify-closer\').trigger(\'click\');" href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
                        if (message.status === "success") {
                            NotificationService.notify({
                                title: "Success: " + message.taskTitle + linkText,
                                type: 'success'
                            });
                            deferred.resolve(null);
                        } else {
                            NotificationService.notify({
                                title: "Error: " + message.taskTitle + linkText,
                                text: message.errorMessage,
                                type: 'error',
                                hide: false
                            });
                            deferred.reject(message.errorMessage);
                        }
                        TaskService.refreshTasks();
                    } else if (message.type === "logEntry") {
                        var streamId = message.streamId;
                        logSubscriptions[streamId](message.entry);
                    }

                };
                ws.onclose = function () {
                    // websocket is closed.
                    console.log("Connection is closed...");
                    connectWebSocket(1000);
                };
            }, timeout);
        }

        connectWebSocket(0);
        TaskService.httpPost = function httpPost(url, request) {
            return $http.post(url, request).then(function (result) {
                var taskId = result.data.taskId;
                var linkText = ' <a href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
                TaskService.refreshTasks();
                NotificationService.notify({
                    title: "Task started: " + result.data.title + linkText,
                    type: 'info'
                });
                return TaskService.getTaskCompletionPromise(taskId);
            });
        };

        var nextStreamId = 1;
        function getNextStreamId() {
            var streamId = nextStreamId;
            nextStreamId++;
            return streamId;
        }
        TaskService.subscribeToLog = function subscribeToLog(taskId, callback) {
            var streamId = getNextStreamId();
            logSubscriptions[ streamId] = callback;
            var message = {
                command: "registerLogListener",
                taskId: taskId,
                streamId: streamId
            };
            sendMessage(message);
        };

        return TaskService;
    });

})();