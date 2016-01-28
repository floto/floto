(function () {
    "use strict";

    app.directive("flotoRedeployButton", function (configuration, $rootScope) {
        function linkRedeployButton(scope, element, attrs) {
			scope.open = false;
            scope.what = attrs.what;
            scope.size = attrs.size;
            scope.deploymentModes = configuration.deploymentModes;
            scope.defaultDeploymentMode = configuration.defaultDeploymentMode;
            scope.defaultDeploymentModeCaption = configuration.defaultDeploymentModeCaption;
            scope.execute = function execute(mode) {
                scope.clickHandler({deploymentMode: mode});
            };
            scope.$watch(function () {
                return scope.$parent.armed;
            }, function(armed) {
                   scope.armed = armed;
                }
            );
        }

        return {
            restrict: 'E',
            replace: true,
            scope: {
                buttonClass: "=",
                clickHandler: "&onClick"
            },
            templateUrl: "/js/util/redeploy-button.html",
            link: linkRedeployButton
        };
    });

	app.directive("flotoAutoScroll", function ($window) {
		function linkAutoScroll(scope, element, attrs) {
			var scrollElement = element[0];
			var autoScrolltop = -1;
			scope.flotoAutoScrollTrigger = _.debounce(function() {
				if(scope.flotoAutoScroll) {
					scrollElement.scrollTop = scrollElement.scrollHeight;
					autoScrolltop = scrollElement.scrollTop;
				}
			}, 10, {maxWait: 10, leading: true});
			scope.$watch("flotoAutoScroll", function(newValue) {
				if(newValue) {
					scope.flotoAutoScrollTrigger();
				}
			});
			element.bind('scroll', function () {
				if (autoScrolltop !== scrollElement.scrollTop) {
					// Different from our set scrollTop, assume it was done by user and unset the autoscroll flag
					autoScrolltop = -1;
					scope.flotoAutoScroll = false;
					scope.$apply();
				}
			});

		}

		return {
			restrict: 'A',
			replace: false,
			scope: {
				flotoAutoScroll: "=",
				flotoAutoScrollTrigger: "="
			},
			link: linkAutoScroll
		};
	});

    app.directive("flotoFillHeight", function ($window) {
        function linkFillHeight(scope, element, attrs) {
            var fillHeight = scope.fillHeight || 1.0;
            scope.$watch("fillHeight", function (newValue) {
                fillHeight = scope.fillHeight || 1.0;
                updateHeight();
            });
            element.css("overflow-y", "auto");
            function updateHeight() {
                var top = element[0].getBoundingClientRect().top;
                element.css("height", fillHeight * ($window.innerHeight - top - 50) + "px");
            }

            angular.element($window).bind('resize', updateHeight);
            scope.$on("$destroy", function () {
                angular.element($window).unbind('resize', updateHeight);
            });
        }

        return {
            restrict: 'A',
			replace: false,
            scope: {
                fillHeight: "=flotoFillHeight"
            },
            link: linkFillHeight
        };
    });

    app.filter('humanDuration', function (moment, amMoment) {
        return function (value) {
            if (typeof value === 'undefined' || value === null) {
                return '';
            }

            return moment.duration(value).humanize();
        };
    });

    app.filter('duration', function (moment, amMoment) {
        return function (value) {
            if (typeof value === 'undefined' || value === null) {
                return '';
            }

            var duration = moment.duration(value);
            return  moment.utc(duration.asMilliseconds()).format("HH:mm:ss.SSS");
        };
    });


    // break infinite loop when compiling recursive directive (by Mark Lagendijk) - taken from http://stackoverflow.com/a/18609594
    app.factory('RecursionHelper', ['$compile', function($compile){
        return {
            /**
             * Manually compiles the element, fixing the recursion loop.
             * @param element
             * @param [link] A post-link function, or an object with function(s) registered via pre and post properties.
             * @returns An object containing the linking functions.
             */
            compile: function(element, link){
                // Normalize the link parameter
                if(angular.isFunction(link)){
                    link = { post: link };
                }

                // Break the recursion loop by removing the contents
                var contents = element.contents().remove();
                var compiledContents;
                return {
                    pre: (link && link.pre) ? link.pre : null,
                    /**
                     * Compiles and re-adds the contents
                     */
                    post: function(scope, element){
                        // Compile the contents
                        if(!compiledContents){
                            compiledContents = $compile(contents);
                        }
                        // Re-add the compiled contents to the element
                        compiledContents(scope, function(clone){
                            element.append(clone);
                        });

                        // Call the post-linking function, if any
                        if(link && link.post){
                            link.post.apply(null, arguments);
                        }
                    }
                };
            }
        };
    }]);

    app.directive("jsonStructure", function (RecursionHelper) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                object: "=object"
            },
            link: function(scope, element, attr) {

                scope.typeOf = function(input) {
                    return typeof input;
                }
            },
            templateUrl: "js/util/jsonStructure.html"
        };
    });

    app.directive("jsonItem", function (RecursionHelper) {
        return {
            restrict: 'AE',
            replace: true,
            scope: {
                key: "=",
                value: "="
            },
            compile: function(element) {
                // Use the compile function from the RecursionHelper,
                // And return the linking function(s) which it returns
                return RecursionHelper.compile(element, {
                    post: function(scope, element, attr) {
                        scope.isExpanded = false;
                        scope.isExpandable = false;
                        scope.isArray = false;
                        scope.isObject = false;
                        scope.isSimple = false;
                        if(Array.isArray(scope.value)) {
                            scope.isArray = true;
                            scope.isExpandable = true;
                        } else if(typeof scope.value === "object") {
                            scope.isObject = true;
                            scope.isExpandable = true;
                        } else {
                            scope.isSimple = true;
                        }

                        scope.typeOf = function(input) {
                            return typeof input;
                        }
                    }
                });
            },
            templateUrl: "js/util/jsonItem.html"
        };
    });

    app.directive("jsonTable", function (RecursionHelper) {
        return {
            scope: {
                object: "=object"
            },
            restrict: 'AE',
            replace: true,
            templateUrl: "js/util/jsonTable.html"
        };
    });

    app.filter('fileSize', function() {
        return function(bytes, precision) {
            if (isNaN(parseFloat(bytes)) || !isFinite(bytes)) return '-';
            if (typeof precision === 'undefined') precision = 1;
            var units = ['bytes', 'kB', 'MB', 'GB', 'TB', 'PB'],
                number = Math.floor(Math.log(bytes) / Math.log(1024));
            return (bytes / Math.pow(1024, Math.floor(number))).toFixed(precision) +  ' ' + units[number];
        }
    });


})();
