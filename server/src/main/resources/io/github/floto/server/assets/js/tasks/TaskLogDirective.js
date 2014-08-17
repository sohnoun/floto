(function () {
    "use strict";
    var classMap = {
        ERROR: "danger",
        WARN: "warning"
    }


    app.directive("taskLog", function ($window, TaskService) {
        function taskLog(scope, element, attrs) {
            var table = element.find("table");
            var $scrollElement = element.parent();
            var scrollElement = $scrollElement[0];

            function shouldActivateAutoScroll() {
                // + 1 catches off by one errors in chrome
                return scrollElement.scrollTop + scrollElement.clientHeight + 1 >= scrollElement.scrollHeight;
            }

            $scrollElement.bind('scroll', function () {
                var activate = shouldActivateAutoScroll();
                if (scope.autoScroll != activate) {
                    scope.autoScroll = activate;
                    scope.$apply();
                }
            });

            TaskService.subscribeToLog(scope.taskId, function (entry) {
                var cls = classMap[entry.level];
                var classPart = "";
                if (cls) {
                    classPart = ' class="' + cls + '" '
                }
                var stackTracePart = "";
                if (entry.stackTrace) {
                    stackTracePart = "<pre>" + entry.stackTrace + "</pre>";
                }
                var row = "<tr" + classPart + ">"
                row += "<td>" + entry.timestamp.substring(11, 23) + "</td>";
                row += "<td>" + entry.message + stackTracePart + "</td>";
                row += "<td>" + entry.level + "</td>";
                row += "<td>" + entry.logger + "</td>";
                row += "</tr>"
                table.append(row);
                if (scope.autoScroll) {
                    scrollElement.scrollTop = scrollElement.scrollHeight;
                }
            });
        }

        return {
            restrict: 'E',
            templateUrl: "js/tasks/tasklog.html",
            scope: {
                taskId: "=",
                autoScroll: "="
            },
            link: taskLog
        };
    });

})();
