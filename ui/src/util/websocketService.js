import Promise from "bluebird";

var websocketService = {};

var messageHandlers = {};
websocketService.addMessageHandler = function addMessageHandler(messageType, messageHandlerFn) {
	messageHandlers[messageType] = messageHandlerFn;
};

websocketService.sendMessage = function sendMessage(message) {
	wsPromise.then(function (ws) {
		ws.send(JSON.stringify(message));
	});
};

var wsPromise;

var loc = window.location;
var websocketUri;
if (loc.protocol === "https:") {
	websocketUri = "wss:";
} else {
	websocketUri = "ws:";
}
if(loc.host === "localhost:8081") {
	// Development mode
	websocketUri += "//localhost:40004";
} else {
	websocketUri += "//" + loc.host;
}

websocketUri += loc.pathname + "api/_websocket";
function connectWebSocket(timeout) {
	wsPromise = new Promise((resolve, reject) => {
		setTimeout(function () {
			var ws = new WebSocket(websocketUri);

			ws.onopen = function () {
				console.log("Websocket connected");
				resolve(ws);
			};
			ws.onmessage = function (evt) {
				var message = JSON.parse(evt.data);
				var messageHandlerFn = messageHandlers[message.type];
				if (messageHandlerFn) {
					messageHandlerFn(message);
				} else {
					console.log("ERROR: unknown websocket message " + message.type);
					console.log(message);
				}
			};
			ws.onclose = function () {
				// websocket is closed.
				console.log("Connection is closed...");
				connectWebSocket(1000);
			};
		}, timeout);
	});
}

websocketService.start = function() {
	connectWebSocket(0);
};

export default websocketService;
