(function () {
	"use strict";

	app.controller("MainController", function ($scope, FlotoService) {
		$scope.compileManifest = function compileManifest() {
			FlotoService.compileManifest();
		};

		$scope.createPatch = function createPatch() {
			FlotoService.createInitialPatch();
		};
	});
})();