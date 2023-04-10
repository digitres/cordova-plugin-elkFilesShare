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

import android.app.ProgressDialog


public class ElkFilesShare: CordovaPlugin(){

    private val TAG: String = "ElkFilesShare"
    private lateinit var progressDialog: ProgressDialog

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            Log.d(TAG, "running method: $action")
            when (action){
                "importFile" -> {
                    val sourceDirectoryString = args.getString(0)
                    this.cordova.activity.runOnUiThread {
                        run() {
                            startImportActivity(sourceDirectoryString, callbackContext)
                        }
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

    private fun startImportActivity(sourceDirectory: String, callbackContext: CallbackContext) {
        try {
            Log.d(TAG, "Starting import from : $sourceDirectory")
            val elkFileManagerPackageName = "org.rff.digitres.elkfilemanager"
            val intent = Intent("$elkFileManagerPackageName.IMPORT_ACTION")
            val packageName = this.cordova.getActivity().getPackageName()
            intent.putExtra("callingPackage", packageName.toString())
            intent.putExtra("contentPath", sourceDirectory)
            intent.setPackage(elkFileManagerPackageName)
            this.cordova.getActivity().startActivity(intent);
            callbackContext.success("ELK File Manager import activity successfully started ")
        }catch (exc: Exception) {
            callbackContext.success("Error encountered while starting ELK File Manager Import Activity")
        }
    }

    private fun processFile(filesArray: JSONArray, targetDirectory: File, callbackContext: CallbackContext) {
        try {
            for (i in 0 until filesArray.length()) {
                val fileJSONObj = filesArray.getJSONObject(i)
                val sourceUri: Uri? = if (fileJSONObj.has("uri")) Uri.parse(fileJSONObj.getString("uri")) else null
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
                    Log.d(TAG, "Finished copying files into: $targetDirectory")
                    callbackContext.success("Files successfully saved")
                } else {
                    callbackContext.error("Error saving file")
                }

            }
        } catch (e: Exception) {
            println("Error saving received file(s):: ${e.printStackTrace()}")
            callbackContext.error("Error saving received file(s)")
        }
    }


    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val file = File(targetDirectory, entry!!.name)
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

    private fun showLoadingDialog(message: String) {
        try {

            val runnable = Runnable {
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
                val appContext = this.cordova.getActivity().applicationContext
                progressDialog = ProgressDialog(appContext)
                progressDialog.setTitle("Please wait...")
                progressDialog.setMessage(message)
                progressDialog.setCanceledOnTouchOutside(false)
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
                progressDialog.show()
            }
            cordova.activity.runOnUiThread(runnable)

        } catch (e: Exception) {
            println("dialog::error::${e.message}")
        }
    }


    private fun closeLoadingDialog() {
        println("closeLoadingDialog")
        if (::progressDialog.isInitialized) {
            progressDialog.dismiss()
        }
    }

    private fun getSDCardFolder(appFolder: String): File? {
        val context = this.cordova.activity.applicationContext
        val sdcard = context.getExternalFilesDirs(null )[1]
            .parentFile
            ?.parentFile
            ?.parentFile
            ?.parent
        val sdcardDocuments = File("${sdcard}/${appFolder}")
        return if (!sdcardDocuments.listFiles().isNullOrEmpty()) {
            sdcardDocuments
        } else null
    }

    private fun getExternalCardDirectory(): String {
        val defaultValue = "N/A"
        val context = this.cordova.getActivity().applicationContext
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
        try {
            val storageVolumeClassReflection = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
            val getPath = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    //    private fun pickFile() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.type = "application/zip"
//        folderPickerLauncher.launch(intent)
//    }
//    private var folderPickerLauncher =
//        this.cordova.getActivity().registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        try {
//            val appContext = this.cordova.getActivity().getApplicationContext()
//            val destinationFolder = appContext.getFilesDir()
//            val data = result.data
//            val isFileSaved = data?.data?.let { uri ->
//                saveFile(
//                    uri,
//                    destinationFolder
//                )
//            } ?: kotlin.run { false }
//            if (isFileSaved) {
//                println("fileSaved")
//            } else {
//                println("fileNoteSaved")
//            }
//        } catch (e: Exception) {
//            println("error getting file uri:: ${e.printStackTrace()}")
//        }
//    }
}