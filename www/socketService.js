// JS interface
var exec = require('cordova/exec');

var SocketService = {
	serviceName: "SocketService",

	start: function(args, callback) {
		exec(callback, null, this.serviceName, "start", [args]);
	},
	stop: function(args, callback) {
		exec(callback, null, this.serviceName, "stop", [args]);
	},
};

module.exports = SocketService;