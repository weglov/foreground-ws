// JS interface
var exec = require('cordova/exec');

var WsForeground = {
	serviceName: "WsForeground",

	start: function(success, error, args) {
		exec(success, error, this.serviceName, "start", [args]);
	},
	stop: function(success, error, args) {
		exec(success, error, this.serviceName, "stop", [args]);
	},
};

module.exports = WsForeground;