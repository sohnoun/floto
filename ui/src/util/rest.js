import Promise from "bluebird";

let urlPrefix = "/api/";

export function send(request) {
	request.options = request.options || {};
	return new Promise(function (resolve, reject) {
		var xhr = new XMLHttpRequest;
		xhr.addEventListener("error", reject);

		if (xhr.upload && request.options.uploadProgressFn) {
			xhr.upload.addEventListener("progress", (progressEvent) => {
				if (progressEvent.lengthComputable) {
					var percentComplete = (progressEvent.loaded*100 / progressEvent.total).toFixed(1);
					request.options.uploadProgressFn({percentComplete});
					// ...
				} else {
					// Unable to compute progress information since the total size is unknown
				}
			});
		}
		xhr.addEventListener("load", (result) => {
			if (xhr.status >= 200 && xhr.status < 300) {
				if (!request.accept) {
					let responseJson = null;
					if (xhr.status !== 204) {
						responseJson = JSON.parse(xhr.responseText);
					}
					resolve(responseJson);
				} else {
					resolve(xhr.responseText);
				}
			} else {
				try {
					let responseJson = JSON.parse(xhr.responseText);
					reject(responseJson);
				} catch (ignored) {
					reject(xhr.responseText);
				}

			}
		});
		xhr.open(request.method || "GET", urlPrefix + request.url);
		xhr.setRequestHeader("Accept", request.accept || "application/json");
		xhr.setRequestHeader("Content-Type", "application/json");
		if (request.request) {
			if (request.request.blob) {
				xhr.send(request.request.blob);
			} else {
				xhr.send(JSON.stringify(request.request));
			}
		} else {
			xhr.send();
		}
	});
}





