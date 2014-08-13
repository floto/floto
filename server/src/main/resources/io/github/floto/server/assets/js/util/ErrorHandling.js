(function () {
	"use strict";
	app.config([ '$httpProvider', function ($httpProvider, $window) {
		$httpProvider.interceptors.unshift(function ($injector, $q, NotificationService) {
			return {
				responseError: function (response) {
					if (response.config &&  !response.config.suppressErrorNotfications) {
						if(typeof response.data == "string") {
							response.data = JSON.parse(response.data);
						}
						console.log(response.data.message);
						console.log(response);
						var message = response.data.message;
						if (message) {
							message = message.slice(0, 300);
						} else {
							message = "Unable to contact server";
						}
						NotificationService.notify({
							title: response.config.url,
							text: message,
							type: 'error'
						});
					}
					return $q.reject(response);
				}
			};
		});

	} ]);
	app.factory('$exceptionHandler', function ($log) {
		return function (exception, cause) {
			$log.error.apply($log, arguments);
			if (cause) {
				exception.message += ' (caused by "' + cause + '")';
			}
			throw exception;
		};
	});

})();
