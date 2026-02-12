package com.digitres.cordova.plugin

import android.app.Activity
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.AlertDialog
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.webkit.MimeTypeMap
import java.io.IOException

public class ElkFilesShare: CordovaPlugin(){

    private val TAG: String = "ElkFilesShare_Log"
    private var progressDialog: AlertDialog? = null
    private var callbackContext : CallbackContext? = null
    private val SELECT_FILE_OR_DIRECTORY_REQUEST_CODE = 1001
    private val IMPORT_FILE_OR_DIRECTORY_REQUEST_CODE = 1002
    // Add this new property
    private var targetDirectoryForImport: String? = null

    override fun pluginInitialize() {
        super.pluginInitialize()
        // Set this plugin to handle activity results
        this.cordova.setActivityResultCallback(this)
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            Log.d(TAG, "running method: $action")
            this.callbackContext = callbackContext

            when (action){
                "selectFile" -> {
                    selectFileOrDirectory(callbackContext, SELECT_FILE_OR_DIRECTORY_REQUEST_CODE, Intent.ACTION_OPEN_DOCUMENT)
                    return true
                }
                "selectDirectory" -> {
                    selectFileOrDirectory(callbackContext, SELECT_FILE_OR_DIRECTORY_REQUEST_CODE, Intent.ACTION_OPEN_DOCUMENT_TREE)
                    return true
                }
                "importFile" -> {
                    if (args.length() == 1) {
                        this.targetDirectoryForImport = args.getString(0).replace("file:///", "")
                        selectFileOrDirectory(callbackContext,IMPORT_FILE_OR_DIRECTORY_REQUEST_CODE,Intent.ACTION_OPEN_DOCUMENT)
                    } else {
                        callbackContext.error("A target directory must be provided.")
                    }
                    return true
                }
                "importDirectory" -> {
                    if (args.length() == 1) {
                        this.targetDirectoryForImport = args.getString(0).replace("file:///", "")
                        selectFileOrDirectory(callbackContext,IMPORT_FILE_OR_DIRECTORY_REQUEST_CODE,Intent.ACTION_OPEN_DOCUMENT_TREE)
                    } else {
                        callbackContext.error("A target directory must be provided.")
                    }

                    return true
                }

                "checkAppInstallStatus" -> {
                    val packageName = args.getString(0)
                    val isInstalled = isAppInstalled(this.cordova.activity.applicationContext, packageName)

                    if (isInstalled) {
                        callbackContext.success("App is installed")
                    } else {
                        callbackContext.error("App is not installed")
                    }
                    return true
                }
                "echo" -> {
                    val message = args.getString(0)
                    echo("Backend echo: $message ", callbackContext)
                    return true
                }
                else ->{
                    callbackContext.error("Unknown action in ELKFilesShare Plugin execute function: $action")
                    return false
                }
            }
        } catch (ex: Exception){
            callbackContext.error("elkFilesShare Plugin error: ${ex.message}")
            return false
        }
    }

    private fun selectFileOrDirectory(callbackContext: CallbackContext, requestCode: Int, intetAction: String) {
        try {
            var intent: Intent
            if (intetAction == Intent.ACTION_OPEN_DOCUMENT) {
                intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // Required to be */* when using EXTRA_MIME_TYPES
                    val mimeTypes = arrayOf("application/zip", "application/x-zip-compressed")
                    putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                }
            } else {  // for Intent.ACTION_OPEN_DOCUMENT_TREE
                intent = Intent(intetAction)
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            this.cordova.startActivityForResult(this, intent, requestCode)
            // Keep the callback context alive for the async result
            val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
            pluginResult.keepCallback = true
            callbackContext.sendPluginResult(pluginResult)
        } catch (e: Exception) {
            Log.e(TAG, "File/Directory selection failed", e)
            callbackContext.error("File/Directory selection failed")
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SELECT_FILE_OR_DIRECTORY_REQUEST_CODE -> { // We just pass back teh selcted file/directory uri to cordova app
                if (resultCode == Activity.RESULT_OK && this.callbackContext != null) {
                    val selectedUri = data?.data
                    if (selectedUri != null) {
                        // Persist access permissions
                        val contentResolver = this.cordova.activity.contentResolver
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(selectedUri, takeFlags)

                        this.callbackContext?.success(selectedUri.toString())
                    } else {
                        this.callbackContext?.error("No directory was selected.")
                    }
                } else {
                    this.callbackContext?.error("Directory selection was canceled.")
                }
            }
            IMPORT_FILE_OR_DIRECTORY_REQUEST_CODE -> { // We import the select file/folder to the provided target folder
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val selectedUri = data.data
                    val targetDirectoryString = this.targetDirectoryForImport
                    if (selectedUri != null && targetDirectoryString != null) {
                        // Persist access permissions
                        val contentResolver = this.cordova.activity.contentResolver
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                       // showProgress()
                        cordova.threadPool.execute {
                            run() {
                                importFilesFromUri(selectedUri,targetDirectoryString)
                            }
                        }
                       // dismissProgress()

                    } else {
                        this.callbackContext?.error("No directory was selected.")
                    }
                } else {
                    this.callbackContext?.error("Directory selection was canceled.")
                }
            }
            else -> {}
        }

    }

    private fun echo(
        message: String= "default message",
        callbackContext: CallbackContext
    ) {
        if (message.isNotEmpty()) {
            callbackContext.success(message)
        } else {
            callbackContext.error("Expected one non-empty string argument.")
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }



    /**
     * Imports files from a source directory URI (from SAF) into a target local directory.
     * This function replaces the dependency on an external file manager app.
     */

    private fun importFilesFromUri(sourceUri: Uri, targetDirectoryPath: String) {

        try {
            var successCount = 0
            var failCount = 0
            val context = this.cordova.activity.applicationContext
            val contentResolver = context.contentResolver

            // first prepare target folder
            val targetDirectory = File(targetDirectoryPath)
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }
            //determine if picked a directory or file
            val isDirectory = DocumentsContract.isTreeUri(sourceUri)
            Log.d(TAG,"Is Directory determined by flags: $isDirectory")

            if (isDirectory) {
                Log.d(TAG, "Selected item is a directory: $sourceUri")
                val treeDocId = DocumentsContract.getTreeDocumentId(sourceUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, treeDocId)
                val cursor = contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, OpenableColumns.DISPLAY_NAME), null, null, null)

                if (cursor == null) {
                    this.callbackContext?.error("Could not list files in the selected directory. Check permissions.")
                    return
                }

                cursor.use {
                    while (it.moveToNext()) {
                        var docId = it.getString(0)
                        val fileName = it.getString(1)
                        // Log.e(TAG, "DocID: ${docId}")

                        try {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, docId)
                            //  Log.e(TAG, "FileURI: ${file?.uri}")
                            copyFile(fileUri, targetDirectory,fileName )
                          successCount++
                            // Log.d(TAG, "Successfully imported and processed: $fileName")
                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "Failed to import a file: ${e.message}",e)
                          //  val stackTraceString = e.stackTraceToString()
                            this.callbackContext?.error("ERROR DURING IMPORT: \n${e.message}\n sourceDirectoryUriString: ${sourceUri.toString()} \n targetDirectoryPath:$targetDirectoryPath \nfileName:$fileName")
                            return // Stop execution
                        }
                    }
                }
            } else {
                // The selected item is a file (or other non-directory type)
                Log.d(TAG, "Selected item is a file: ${sourceUri.toString()}")
                val fileName = getFileNameFromUri(context,sourceUri)
                copyFile(sourceUri, targetDirectory, fileName!!)
                successCount++
            }
            this.callbackContext?.success("Import complete. $successCount file(s) copied.")

        } catch (e: Exception) {
          //  val stackTraceString = e.stackTraceToString()
            Log.e(TAG, "Error during file import process: ${e.message}", e)
            this.callbackContext?.error("ERROR DURING IMPORT: \n${e.message} \n sourceDirectoryUriString: ${sourceUri.toString()} \n targetDirectoryPath:$targetDirectoryPath")
        }
    }

// Copy the file from the source URI to the target file path
private fun copyFile(fileUri: Uri, targetDirectory: File, fileName: String) {
    try {
        val context = this.cordova.activity.applicationContext
        val contentResolver = context.contentResolver
        val targetFile = File(targetDirectory, fileName)
        contentResolver.openInputStream(fileUri)?.use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        // Unzip if it's a zip file, then delete the archive
        if (targetFile.extension.equals("zip", ignoreCase = true)) {
            unzip(targetFile, targetDirectory)
            targetFile.delete()
        }
    } catch (e: IOException) {
        Log.e("FileError", "Copy failed", e)
        throw e
    }
}

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        // Check if it already has an extension
        if (!fileName!!.contains(".")) {
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (extension != null) {
                fileName = "$fileName.$extension"
            }
        }
        return fileName
    }


//    private fun importFilesFromUri(sourceDirectoryUriString: String, targetDirectoryPath: String, callbackContext: CallbackContext) {
//        try {
//            val context = this.cordova.activity.applicationContext
//            val sourceUri = Uri.parse(sourceDirectoryUriString)
//
//            // 1. Get the root folder directly from the Tree URI
//            val rootFolder = DocumentFile.fromTreeUri(context, sourceUri)
//
//            if (rootFolder == null || !rootFolder.canRead()) {
//                callbackContext.error("Cannot read the selected folder. Check permissions.")
//                return
//            }
//
//            val targetDirectory = File(targetDirectoryPath).apply { if (!exists()) mkdirs() }
//
//            // 2. Use listFiles() instead of a manual Cursor
//            // This is much more stable on Android 8.1 USB providers
//            val files = rootFolder.listFiles()
//            var successCount = 0
//
//            for (file in files) {
//                if (file.isFile) {
//                    val fileName = file.name ?: "unknown_file"
//                    val targetFile = File(targetDirectory, fileName)
//
//                    try {
//                        // 3. Open the stream directly from the DocumentFile's URI
//                        context.contentResolver.openInputStream(file.uri)?.use { input ->
//                            targetFile.outputStream().use { output ->
//                                input.copyTo(output)
//                            }
//                        }
//
//                        if (targetFile.extension.equals("zip", ignoreCase = true)) {
//                            unzip(targetFile, targetDirectory)
//                            targetFile.delete()
//                        }
//                        successCount++
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Failed to copy $fileName: ${e.message}")
//                        callbackContext.error("Import failed: ${e.localizedMessage}")
//                    }
//                }
//            }
//            callbackContext.success("Import complete. Success: $successCount")
//
//        } catch (e: Exception) {
//            callbackContext.error("Import failed: ${e.localizedMessage}")
//        }
//    }

//    //This function which calls FileManager will be removed once all cordova apps have been updated to use the above functionw hich doe snot depend of file manager.
//    private fun startImportActivity(sourceDirectoryUri: String, callbackContext: CallbackContext) {
//        try {
//            val elkFileManagerPackageName = "org.rff.digitres.elkfilemanager"
//            val intent = Intent("$elkFileManagerPackageName.IMPORT_ACTION")
//            val packageName = this.cordova.activity.packageName
//            Log.d(TAG, "PACKAGE NAME: $packageName")
//            intent.putExtra("callingPackage", packageName.toString())
//            // Pass the URI as a string
//            intent.putExtra("contentPath", sourceDirectoryUri)
//            intent.setPackage(elkFileManagerPackageName)
//            // Add flags to grant URI permissions to the receiving app
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            this.cordova.activity.startActivity(intent);
//            callbackContext.success("ELK File Manager import started on: $sourceDirectoryUri ")
//
//        }catch (exc: Exception) {
//            Log.d(TAG, exc.message!!)
//            Log.d(TAG, exc.stackTraceToString())
//            callbackContext.error("Error encountered while starting ELK File Manager Import Activity")
//        }
//    }
//
//    private fun processFile(filesArray: JSONArray, targetDirectory: File, callbackContext: CallbackContext) {
//        Log.d(TAG, "Received files: ${filesArray.length()}")
//        try {
//            var successCount: Int = 0
//            var failCount: Int = 0
//
//            for (i in 0 until filesArray.length()) {
//                val fileJSONObj = filesArray.getJSONObject(i)
//                val fileUriString = fileJSONObj.getString("uri")
//                val sourceUri: Uri? = if (fileJSONObj.has("uri")) Uri.parse(fileUriString) else null
//                if (sourceUri == null) {
//                    callbackContext.error("Invalid URI passed")
//                    return
//                }
//                val fileExtension = fileJSONObj.getString("extension")
//                val activity = this.cordova.getActivity()
//
//                val cursor = activity.contentResolver.query(sourceUri, null, null, null, null)
//                cursor?.moveToFirst()
//                val fileName =
//                    cursor?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
//                cursor?.close()
//
//                if (fileName != null) {
//                    val file = File(targetDirectory, fileName)
//                    file.outputStream().use { activity.contentResolver.openInputStream(sourceUri)?.copyTo(it) }
//                    if (fileExtension == "zip") {
//                        unzip(file,targetDirectory)
//                        file.delete()
//                    }
//                    successCount = successCount + 1
//                    Log.d(TAG, "Finished copying file: $fileUriString")
//                } else {
//                    failCount = failCount + 1
//                    Log.d(TAG, "${fileUriString} is not a valid file and therefore cannot be copied")
//                }
//            }
//            callbackContext.success("Finished coping files to app storage: $targetDirectory \n Files Copied: $successCount \n Fail count: $failCount")
//        } catch (e: Exception) {
//            Log.d(TAG, "Error saving received file(s):: ${e.printStackTrace()}")
//            callbackContext.error("Error encounter while saving received file(s) to $targetDirectory ")
//        }
//    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val file = File(targetDirectory, entry!!.name)
                    val canonicalPath: String = file.getCanonicalPath()
                    if (!canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                        throw SecurityException("Unsafe entry in zipped file.")
                    } else {
                        if (entry!!.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.let {
                                it.mkdirs()
                                FileOutputStream(file).use { fos ->
                                    val buffer = ByteArray(1024)
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("Error", "Unzipping file failed", e)
            throw e
        }
    }

    private fun showProgress() {
        Log.d(TAG, "iNSIDE showProgress()")
        try {
            cordova.getActivity().runOnUiThread {
                val builder = AlertDialog.Builder(cordova.getActivity())
                builder.setTitle("ElkFilesShare Copying Files...")
                // Create a simple horizontal or indeterminate progress bar
                val progressBar = ProgressBar(cordova.getActivity(), null, android.R.attr.progressBarStyleHorizontal)
                progressBar.isIndeterminate = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                progressBar.layoutParams = lp

                builder.setView(progressBar)
                builder.setCancelable(false)

                progressDialog = builder.create()
                progressDialog?.show()
            }
        } catch (e: IOException) {
            Log.e("Error", "Error shoing progress bar", e)
            throw e
        }
    }

    private fun dismissProgress() {
        cordova.getActivity().runOnUiThread {
            progressDialog?.dismiss()
        }
    }
}
