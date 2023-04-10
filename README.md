# cordova-plugin-elk-files-share

Enables a cordova application to import/load files in a folder on the  SDCard via ELK File manager app. This is useful for those apps which do not have the MANAGE_ALL_FILES_ACCESS_PERMISSION and supporting Android 11 or higher.

<b> DEPENDENCIES</b>
The plugin has the following dependencies.
1. https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent
2. https://github.com/dpa99c/cordova-diagnostic-plugin
3. https://github.com/dearzhengxy/cordova-plugin-progressdialog

<b> PERMISSION REQUIREMENTS</b>
Ensure your Cordova app's Manifest include:
1. Intent-filters
```
     <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
     </intent-filter>
```
2.  PERMISSION
```
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
```

<b> USAGE</b>
To use the plugin, make calls to the two API functions below:

<b> cordova.plugins.ElkFilesShare.importFile</b> Call this method to start ELK File manager IMPORT_ACTION activity which reads the 2 files from the SDCard and saves them in the temporary folder and sends a file SEND_MULTIPLE intent back to your application. The function requires the full path of the folder containing the files to be copied. The example below uses the diagnostic plugin's function getExternalSdCardDetails() function to get the SDCard location on the device. This is then extended to include the subfolder with the required files.

<b>EXAMPLE</b>
```
     cordova.plugins.diagnostic.getExternalSdCardDetails(
           function(data) {
             var sdCardRoot = data[0].path;
             cordova.plugins.ElkFilesShare.importFile(
                 [sdCardRoot + "/SubFolder/"],    // e.g for SubFolder is 'KnowHow' or 'NPT' for KnowHow and Natural Playground apps respectively.
                 function(result){
                     console.log(result);
                 },
                 function(err){
                 console.log(err);
                 }
             );
           },
           function (err) {
               console.log("Failed to get SDCard Root Path on this device.")
               console.log("error: " + err);
           }
     );
```

<b> cordova.plugins.ElkFilesShare.processFile</b>: Call this function to process the SEND_MULTIPLE file intent your app receives from ELK File manager after initiating the file import described above. The example below uses the darryncampbell-cordova-plugin-intent plugin's getIntent() function to intercept intents. It then checks if it’s a SEND_MULTIPLE Intent and if it has file items before calling this plugin's importFile() function which requires two values to be passed via the params array parameter. The first parameter is an array with list of file URIs which is part of the received intent and the second is the target directory for saving the received files on the device. The example also uses the cordova.plugin.progressDialog plugin to display a progress message while the files are being saved to given target directory.

<b>EXAMPLE</b>
```
  window.plugins.intentShim.getIntent(
      function(intent)
      {
            console.log("Sent Intent Received");
             console.log(intent.action);
          if ( intent.action == 'android.intent.action.SEND_MULTIPLE' && intent.hasOwnProperty('clipItems')) {

              if (intent.clipItems.length > 0) {
                  console.log(intent.clipItems);
                  console.log('Receiving file: ' +  JSON.stringify(intent.clipItems[0]));
                 var targetSaveDirectory =  cordova.file.externalDataDirectory;
                //var targetSaveDirectory = cordova.file.dataDirectory
                 console.log(targetSaveDirectory);
                   var params = [
                    intent.clipItems,
                    targetSaveDirectory
                   ];
                    cordova.plugin.progressDialog.init({
                       // theme : 'HOLO_DARK',
                        progressStyle : 'SPINNER',
                        cancelable : true,
                        title : 'Please Wait...',
                        message : 'Copying files to application storage ...',
                    });
                  cordova.plugins.ElkFilesShare.processFile(
                      params,
                      function(result){
                          console.log(result);
                          cordova.plugin.progressDialog.dismiss();
                      },
                      function(err){
                          console.log(err);
                          cordova.plugin.progressDialog.dismiss();
                      }
                  );
              }
          }
      },
      function()
      {
          console.log('Error getting launch intent');
      }
  );
```

