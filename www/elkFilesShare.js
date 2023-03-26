var exec = require('cordova/exec');

/**
 * File picker.
 */
var elkFilesShare = {

    processFile: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'processFile',
            [params]
        );
    },

   echo: function (success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'echo',
            []
        );
   },
   alert: function (success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'alert',
            []
        );
    }

}
module.exports = elkFilesShare;