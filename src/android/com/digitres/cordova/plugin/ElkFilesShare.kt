package com.digitres.cordova.plugin

import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.FileInputStream

import android.content.Intent

//import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.os.storage.StorageVolume


public class ElkFilesShare: CordovaPlugin(){

    private val TAG: String = "ElkFilesShare"
   // private lateinit var progressDialog: ProgressDialog
    private var callbackContext : CallbackContext? = null

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            Log.d(TAG, "running method: $action")
            this.callbackContext = callbackContext;
            when (action){
                "importFile" -> {
                    val sdcardRoot = getExternalCardDirectory()
                    if (sdcardRoot != null) {
                        val sourceDirectoryString = args.getString(0)
                        this.cordova.activity.runOnUiThread {
                            run() {
                                startImportActivity( "$sdcardRoot/${sourceDirectoryString.trim('/')}", callbackContext)
                            }
                        }
                    } else {
                        Log.d(TAG, "SDCard is not available or not supported on this device.")
                        callbackContext.error("SDCard is not available or not supported on this device.")
                    }
                    return true
                }
                "processFile" -> {
                    if (args.length() > 1) {
                        val filesArray = args.getJSONArray(0)
                        var targetDirectory: File? = null
                        val targetDirectoryString = args.getString(1).replace("file:///","")
                        targetDirectory = File(targetDirectoryString)
                        cordova.threadPool.execute {
                            run() {
                                processFile(filesArray , targetDirectory,callbackContext)
                            }
                        }
                    } else {  // set directory if not provided
                        Log.d(TAG, "Both Files Array and Target directory must be provided")
                        callbackContext.error("Both Files Array and Target directory must be provided in the parameter array")
                    }
                    return true
                }
                "isAppInstalled" -> {
                    val packageName = args.getString(0)
                    Log.d(TAG, "Package name: $packageName")
                    cordova.threadPool.execute {
                        run() {
                            isAppInstalled(callbackContext, packageName)
                        }
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

    fun isAppInstalled( callbackContext: CallbackContext, packageName: String): Boolean {
        return try {
            val pm = callbackContext.packageManager
            // For Android 13 (API 33) and above, use ApplicationInfoFlags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            callbackContext.success(true)
        } catch (e: PackageManager.NameNotFoundException) {
            callbackContext.error(false)
        }
    }

    private fun startImportActivity(sourceDirectory: String, callbackContext: CallbackContext) {
        try {
            val elkFileManagerPackageName = "org.rff.digitres.elkfilemanager"
            val intent = Intent("$elkFileManagerPackageName.IMPORT_ACTION")
            val packageName = this.cordova.activity.packageName
            Log.d(TAG, "PACKAGE NAME: $packageName")
            intent.putExtra("callingPackage", packageName.toString())
            intent.putExtra("contentPath", sourceDirectory)
            intent.setPackage(elkFileManagerPackageName)
            this.cordova.activity.startActivity(intent);
            callbackContext.success("ELK File Manager import started on: $sourceDirectory ")

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
                    Log.d(TAG, "${fileUriString} is not a valid file and therfore cannot be copied")
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

    private fun getExternalCardDirectory(): String? {
        val defaultValue: String? = null
        val context = this.cordova.getActivity().applicationContext
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
        try {
            val storageVolumeClassReflection = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
            val getPath = if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                storageVolumeClassReflection.getMethod("getDirectory")
            } else {
                storageVolumeClassReflection.getMethod("getPath")
            }
            val isRemovable = storageVolumeClassReflection.getMethod("isRemovable")
            val result = getVolumeList.invoke(storageManager) as Array<StorageVolume>
            result.forEach {
                if (isRemovable.invoke(it) as Boolean) {
                    return when(val invokeResult = getPath.invoke(it)) {
                        is File -> invokeResult.absolutePath

                        is String -> invokeResult

                        else -> defaultValue.also {
                            Log.d(TAG,"Reflection unsupported type; Invoke result: $invokeResult" )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG,"Could not get SD card path; Exception: $e" )
        }
        return defaultValue
    }


//    private fun showLoadingDialog(message: String) {
//        try {
//
//            val runnable = Runnable {
//                if (progressDialog.isShowing) {
//                    progressDialog.dismiss()
//                }
//                val appContext = this.cordova.getActivity().applicationContext
//                progressDialog = ProgressDialog(appContext)
//                progressDialog.setTitle("Please wait...")
//                progressDialog.setMessage(message)
//                progressDialog.setCanceledOnTouchOutside(false)
//                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
//                progressDialog.show()
//            }
//            cordova.activity.runOnUiThread(runnable)
//
//        } catch (e: Exception) {
//            println("dialog::error::${e.message}")
//        }
//    }


//    private fun closeLoadingDialog() {
//        println("closeLoadingDialog")
//        if (::progressDialog.isInitialized) {
//            progressDialog.dismiss()
//        }
//    }

//    fun externalMemoryAvailable(): Boolean {
//        return if (android.os.Environment.isExternalStorageRemovable()) {
//            val state: String = android.os.Environment.getExternalStorageState()
//            state == android.os.Environment.MEDIA_MOUNTED || state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY
//        } else {
//            false
//        }
//    }

//    private fun getSdCardFolder(path: String): String? {
//        val subfolder: String? = path.trim('/')
//        val context = this.cordova.activity.applicationContext
//        val sdcard = context.getExternalFilesDirs(null )[1]
//            .parentFile
//            ?.parentFile
//            ?.parentFile
//            ?.parent
//        val sourceDirectory = "${sdcard}/${subfolder}"
//        Log.d(TAG, "RECREATED FOLDER = : $sourceDirectory")
//        val sdcardDocuments = File(sourceDirectory )
//        return if (!sdcardDocuments.listFiles().isNullOrEmpty()) {
//            sourceDirectory
//        } else null
//    }

//    @kotlin.jvm.Synchronized
//    fun alert(
//        title: String,
//        message: String,
//        buttonLabel: String,
//    ) {
//        Builder(cordova.getActivity())
//            .setTitle(title)
//            .setMessage(message)
//            .setCancelable(false)
//            .setNeutralButton(buttonLabel){dialog, which ->
//                dialog.dismiss()
//            }
//            .create()
//            .show()
//    }
}