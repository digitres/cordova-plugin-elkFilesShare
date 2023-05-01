# cordova-plugin-elk-files-share

Enables a cordova application to import/load files in a folder on the  SDCard via ELK File manager app. This is useful for those apps which do not have the MANAGE_ALL_FILES_ACCESS_PERMISSION and supporting Android 11 or higher.

<b> DEPENDENCIES</b>
The plugin has the following dependencies.
1. https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent
3. https://github.com/dearzhengxy/cordova-plugin-progressdialog
4. In addition to the above cordova plugins you need to have <a href="https://play.google.com/store/apps/details?id=org.rff.digitres.elkfilemanager"> ELK File Manager</a> installed and ran at least once to ensure user has given it all the permissions it requires.

<b> FILTERS AND PERMISSION REQUIREMENTS</b>
Ensure your Cordova app's Manifest include:
1. Intent-filters
```
     <intent-filter>
           <action android:name="android.intent.action.SEND_MULTIPLE" />
           <category android:name="android.intent.category.DEFAULT" />
           <data android:mimeType="*/*" />
     </intent-filter>
```
2.  Permissions
```
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
```

<b> USAGE</b></br>
To use the plugin, make calls to the two API functions below:

<b> cordova.plugins.ElkFilesShare.importFile</b> Call this method to start ELK File manager IMPORT_ACTION activity which reads the files from the SDCard and saves them in the temporary folder and sends a file SEND_MULTIPLE intent back to your application. The function requires name of the sub-folder in the SDCard root containing the files to be copied. The plugin will determine the SDCard root on its own and concatenates it with the supplied sub-folder to form a full absolute path of the folder.  
<b>EXAMPLE</b>
```
  cordova.plugins.ElkFilesShare.importFile(
       ["SubFolder"],    // e.g for SubFolder is 'KnowHow' or 'NPT' for KnowHow and Natural Playground apps respectively.
       function(result){
           console.log(result);
       },
       function(err){
       console.log(err);
       }
   );
```

<b> cordova.plugins.ElkFilesShare.processFile</b>: Call this function to process the SEND_MULTIPLE file intent your app receives from ELK File manager after initiating the file import described above. The example below uses the darryncampbell-cordova-plugin-intent plugin's getIntent() function to intercept intents. It then checks if it’s a SEND_MULTIPLE Intent and if it has file items before calling this plugin's processFile() function which requires two values to be passed via the params array parameter. The first parameter is an array with list of file URIs which is part of the received intent and the second is the target directory for saving the received files on the device. The example also uses the cordova.plugin.progressDialog plugin to display a progress message while the files are being saved to given target directory.

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
                        cancelable : false,
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

