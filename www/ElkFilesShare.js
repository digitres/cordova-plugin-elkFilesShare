var exec = require('cordova/exec');

/**
 * ELK Files SHare .
 */
var elkFilesShare = {
    importFile: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'importFile',
            params
        );
    },
    processFile: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'processFile',
            params
        );
    },

    echo: function (message, success, error) {
        cordova.exec(
             success,
             error,
            'ElkFilesShare',
            'echo',
            [message]
        );
     }

//   alert: function (success, error) {
//        cordova.exec(
//            success,
//            error,
//            'ElkFilesShare',
//            'alert',
//            []
//        );
//   }

}
module.exports = elkFilesShare;