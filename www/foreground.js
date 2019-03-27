// JS interface
var exec = require('cordova/exec');

var ForegroundWs = {
	serviceName: "ForegroundWs",

	start: function(success, error, token) {
		exec(success, error, this.serviceName, "start", [token]);
	},
};

module.exports = ForegroundWs;