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
    selectDirectory: function (success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'selectDirectory',
            []
        );
    },

    importFolderFiles: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'importFolderFiles',
            params
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

    processFile: function (params, success, error) {
        cordova.exec(
            success,
            error,
            'ElkFilesShare',
            'processFile',
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
