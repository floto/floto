(function () {
    "use strict";
    app.factory('TaskService', function ($resource, $http, $rootScope, $q, NotificationService, WebSocketService) {
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
            return deferred.promise;
        };
        function sendMessage(message) {
			WebSocketService.sendMessage(message);
        }
		WebSocketService.addMessageHandler("taskLogEntry", function(message) {
			var streamId = message.streamId;
			logSubscriptions[streamId](message.entry);
		});

		WebSocketService.addMessageHandler("taskComplete", function(message) {
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
		});

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
                type: "subscribeToTaskLog",
                taskId: taskId,
                streamId: streamId
            };
            sendMessage(message);
        };

        return TaskService;
    });

})();