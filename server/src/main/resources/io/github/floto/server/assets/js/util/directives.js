(function () {
    "use strict";

    app.directive("flotoRedeployButton", function (configuration) {
        function linkRedeployButton(scope, element, attrs) {
            scope.what = attrs.what;
            scope.size = attrs.size;
            scope.deploymentModes = configuration.deploymentModes;
            scope.defaultDeploymentMode = configuration.defaultDeploymentMode;
            scope.defaultDeploymentModeCaption = configuration.defaultDeploymentModeCaption;
            scope.execute = function execute(mode) {
                scope.clickHandler({deploymentMode: mode});
            };
        }

        return {
            restrict: 'E',
            replace: true,
            scope: {
                clickHandler: "&onClick"
            },
            templateUrl: "/js/util/redeploy-button.html",
            link: linkRedeployButton
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


})();
