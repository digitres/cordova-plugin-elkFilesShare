var exec = require('cordova/exec');

/**
 * ELK Files Share.
 */
var elkFilesShare = {
    /**
     * Opens the system's file picker to allow the user to select a directory.
     * The selected directory's URI is returned in the success callback.
     * This is the preferred method for getting access to SD Card or USB drive roots.
     * @param {function} success Callback function for success, receives the directory URI.
     * @param {function} error Callback function for error.
     */

     selectFile: function (success, error) {
         cordova.exec(
             success,
             error,
             'ElkFilesShare',
             'selectFile',
             []
         );
     },
    selectDirectory: function (success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'selectDirectory',
            []
        );
    },
    importFile: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'importFile',
            params
        );
    },
    importDirectory: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'importDirectory',
            params
        );
    },

    checkAppInstallStatus: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'checkAppInstallStatus',
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
};

module.exports = elkFilesShare;
