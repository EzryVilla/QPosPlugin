var exec = require('cordova/exec');

var posPlug = {
	connectBluetoothDevice: function (success, fail, autoConnect, macAddress) {
		exec(success, fail, "dspread_pos_plugin", "connectBluetoothDevice", [autoConnect, macAddress]);
	},
	setAmount: function (success, fail, amount, cashback, transactionType) {
		exec(success, fail, "dspread_pos_plugin", "setAmount", [amount, cashback, transactionType]);
	},
	doTrade: function (success, fail, timeout) {
		exec(success, fail, "dspread_pos_plugin", "doTrade", [timeout]);
	}

};
module.exports = posPlug;