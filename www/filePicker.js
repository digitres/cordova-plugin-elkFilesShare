var exec = require('cordova/exec');

/**
 * File picker.
 */
var filePicker = {

    pickFile: function (success, error) {
        cordova.exec(
            success,
            error,
            'FilePicker',
            'pickFile',
            []
        );
    },
//
//   echo: function (success, error) {
//        cordova.exec(
//            success,
//            error,
//            'FilePicker',
//            'echo',
//            []
//        );
//   }
   alert: function (success, error) {
        cordova.exec(
            success,
            error,
            'FilePicker',
            'alert',
            []
        );
    }

}
module.exports = filePicker;