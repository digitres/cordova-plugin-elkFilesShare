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

public class ElkFilesShare: CordovaPlugin(){

    private val TAG: String = "ElkFilesShare_Log"
    private var callbackContext : CallbackContext? = null
    private val SELECT_DIRECTORY_REQUEST_CODE = 1001

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
                "selectDirectory" -> {
                    selectDirectory(callbackContext)
                    return true
                }
                "importFolderFiles" -> {
                    // Refactored to handle direct import
                    if (args.length() > 1) {
                        val sourceUriString = args.getString(0)
                        val targetDirectoryString = args.getString(1).replace("file:///", "")
                        Log.d(TAG, "Source DIR: $sourceUriString")
                        Log.d(TAG, "Target DIR: $targetDirectoryString")
                        cordova.threadPool.execute {
                            run() {
                                importFilesFromUri(sourceUriString, targetDirectoryString, callbackContext)
                            }
                        }
                    } else {
                        callbackContext.error("A source URI and a target directory must be provided.")
                    }
                    return true
                }
                "importFile" -> {
                    // The 'importFile' action now expects the directory URI from the caller
                    val directoryUriString = args.getString(0)
                    Log.d(TAG, "Source DIR: $directoryUriString")
                    this.cordova.activity.runOnUiThread {
                        run() {
                            startImportActivity(directoryUriString, callbackContext)
                        }
                    }
                    return true
                }
                "processFile" -> {
                    if (args.length() > 1) {
                        val filesArray = args.getJSONArray(0)
                        val targetDirectoryString = args.getString(1).replace("file:///","")
                        var targetDirectory = File(targetDirectoryString)
                        cordova.threadPool.execute {
                            run() {
                                processFile(filesArray, targetDirectory, callbackContext)
                            }
                        }
                    } else {
                        Log.d(TAG, "Both Files Array and Target directory must be provided")
                        callbackContext.error("Both Files Array and Target directory must be provided in the parameter array")
                    }
                    return true
                }
                "checkAppInstallStatus" -> {
                    val packageName = args.getString(0)
                    Log.d(TAG, "Package name: $packageName")
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

    private fun selectDirectory(callbackContext: CallbackContext) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        // Optionally, you can suggest a starting location
        // intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)

        this.cordova.startActivityForResult(this, intent, SELECT_DIRECTORY_REQUEST_CODE)

        // Keep the callback context alive for the async result
        val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
        pluginResult.keepCallback = true
        callbackContext.sendPluginResult(pluginResult)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_DIRECTORY_REQUEST_CODE && this.callbackContext != null) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    // Persist access permissions
                    val contentResolver = this.cordova.activity.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d(TAG, "Directory selected: ${uri.toString()}")
                    this.callbackContext?.success(uri.toString())
                } else {
                    this.callbackContext?.error("No directory was selected.")
                }
            } else {
                this.callbackContext?.error("Directory selection was canceled.")
            }
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
    private fun importFilesFromUri(sourceDirectoryUriString: String, targetDirectoryPath: String, callbackContext: CallbackContext) {

        try {

            val context = this.cordova.activity.applicationContext
            val contentResolver = context.contentResolver
            val sourceUri = Uri.parse(sourceDirectoryUriString)
            if (sourceUri.path?.startsWith("/tree/") != true) {
                val errorMessage = "Invalid URI provided. Expected a directory (tree) URI from the Storage Access Framework, but received a document URI instead: $sourceDirectoryUriString"
                Log.e(TAG, errorMessage)
                callbackContext.error(errorMessage)
                return // Stop execution
            }
            val targetDirectory = File(targetDirectoryPath)
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }

            val treeDocId = DocumentsContract.getTreeDocumentId(sourceUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, treeDocId)
            val cursor = contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, OpenableColumns.DISPLAY_NAME), null, null, null)

            if (cursor == null) {
                callbackContext.error("Could not list files in the selected directory. Check permissions.")
                return
            }

            var successCount = 0
            var failCount = 0

            cursor.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0)
                    val fileName = it.getString(1)
                    try {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, docId)

                        val targetFile = File(targetDirectory, fileName)

                        // Copy the file from the source URI to the target file path
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

                        successCount++
                        Log.d(TAG, "Successfully imported and processed: $fileName")

                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "Failed to import a file: ${e.message}")
                        val stackTraceString = e.stackTraceToString()
                        callbackContext.error("ERROR DURING IMPORT \n${e.message} \n $stackTraceString \n sourceDirectoryUriString: $sourceDirectoryUriString \n targetDirectoryPath:$targetDirectoryPath \nfileName:$fileName")
                    }
                }
            }
            callbackContext.success("Import complete. Success: $successCount, Failed: $failCount")

        } catch (e: Exception) {
            val stackTraceString = e.stackTraceToString()
            Log.e(TAG, "Error during file import process: ${e.message}", e)
            callbackContext.error("ERROR DURING IMPORT \n${e.message} \n $stackTraceString \n sourceDirectoryUriString: $sourceDirectoryUriString \n targetDirectoryPath:$targetDirectoryPath")
        }
    }

    //This function which calls FileManager will be removed once all cordova apps have been updated to use the above functionw hich doe snot depend of file manager.
    private fun startImportActivity(sourceDirectoryUri: String, callbackContext: CallbackContext) {
        try {
            val elkFileManagerPackageName = "org.rff.digitres.elkfilemanager"
            val intent = Intent("$elkFileManagerPackageName.IMPORT_ACTION")
            val packageName = this.cordova.activity.packageName
            Log.d(TAG, "PACKAGE NAME: $packageName")
            intent.putExtra("callingPackage", packageName.toString())
            // Pass the URI as a string
            intent.putExtra("contentPath", sourceDirectoryUri)
            intent.setPackage(elkFileManagerPackageName)
            // Add flags to grant URI permissions to the receiving app
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.cordova.activity.startActivity(intent);
            callbackContext.success("ELK File Manager import started on: $sourceDirectoryUri ")

        }catch (exc: Exception) {
            Log.d(TAG, exc.message!!)
            Log.d(TAG, exc.stackTraceToString())
            callbackContext.error("Error encountered while starting ELK File Manager Import Activity")
        }
    }

    private fun processFile(filesArray: JSONArray, targetDirectory: File, callbackContext: CallbackContext) {
        Log.d(TAG, "Received files: ${filesArray.length()}")
        try {
            var successCount: Int = 0
            var failCount: Int = 0

            for (i in 0 until filesArray.length()) {
                val fileJSONObj = filesArray.getJSONObject(i)
                val fileUriString = fileJSONObj.getString("uri")
                val sourceUri: Uri? = if (fileJSONObj.has("uri")) Uri.parse(fileUriString) else null
                if (sourceUri == null) {
                    callbackContext.error("Invalid URI passed")
                    return
                }
                val fileExtension = fileJSONObj.getString("extension")
                val activity = this.cordova.getActivity()

                val cursor = activity.contentResolver.query(sourceUri, null, null, null, null)
                cursor?.moveToFirst()
                val fileName =
                    cursor?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                cursor?.close()

                if (fileName != null) {
                    val file = File(targetDirectory, fileName)
                    file.outputStream().use { activity.contentResolver.openInputStream(sourceUri)?.copyTo(it) }
                    if (fileExtension == "zip") {
                        unzip(file,targetDirectory)
                        file.delete()
                    }
                    successCount = successCount + 1
                    Log.d(TAG, "Finished copying file: $fileUriString")
                } else {
                    failCount = failCount + 1
                    Log.d(TAG, "${fileUriString} is not a valid file and therefore cannot be copied")
                }
            }
            callbackContext.success("Finished coping files to app storage: $targetDirectory \n Files Copied: $successCount \n Fail count: $failCount")
        } catch (e: Exception) {
            Log.d(TAG, "Error saving received file(s):: ${e.printStackTrace()}")
            callbackContext.error("Error encounter while saving received file(s) to $targetDirectory ")
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
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
    }
}
