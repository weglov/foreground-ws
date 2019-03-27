// JS interface
var exec = require('cordova/exec');

var WsForeground = {
	serviceName: "WsForeground",

	start: function(args, success, error) {
		exec(success, error, this.serviceName, "start", [args]);
	},
	stop: function(args, success, error) {
		exec(success, error, this.serviceName, "stop", [args]);
	},
};

module.exports = WsForeground;