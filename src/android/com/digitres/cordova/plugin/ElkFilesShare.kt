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


public class ElkFilesShare: CordovaPlugin(){

    private val TAG: String = "ElkFilesShare"

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            Log.d(TAG, "running method: $action")
            when (action){

                "processFile" -> {
                    val obj: JSONObject = args.getJSONObject(0)
                    var targetDirectory: File? = null
                    if (args.length() > 1) {
                        val targetDirectoryString = args.getString(1).replace("file:///","")
                        targetDirectory = File(targetDirectoryString)
                        Log.d(TAG, "DIR provided: $targetDirectoryString")
                    } else {  // set directory if not provided
                        val appContext = this.cordova.getActivity().getApplicationContext()
                        val targetDirectory = File(appContext?.getExternalFilesDir(null)?.getAbsolutePath())
                        // val targetDirectory = File(appContext?.getFilesDir()?.getAbsolutePath())
                        Log.d(TAG, "DIR not provided")
                    }
                    val result = processFile(obj , targetDirectory!!,callbackContext)
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

    private fun processFile(obj: JSONObject, targetDirectory: File, callbackContext: CallbackContext) {
        Log.d(TAG, "Inside ProcessFile method")
        try {
            val sourceUri: Uri? = if (obj.has("uri")) Uri.parse(obj.getString("uri")) else null
            if (sourceUri == null) {
                callbackContext.error("Invalid URI passed")
                return
            }
            Log.d(TAG, "Processing: $sourceUri")
            Log.d(TAG, "Tagert directoty is: $targetDirectory")

            println("Processing: $sourceUri")
            println("Target folder: $targetDirectory")

            val activity = this.cordova.getActivity()
            val cursor = activity.contentResolver.query(sourceUri, null, null, null, null)
            cursor?.moveToFirst()
            val fileName =
                cursor?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            cursor?.close()

            if (fileName != null) {
                val file = File(targetDirectory, fileName)
                file.outputStream().use { activity.contentResolver.openInputStream(sourceUri)?.copyTo(it) }
                Log.d(TAG, "Finished reading file, now extracting it into: $targetDirectory")
                unzip(file,targetDirectory)
                Log.d(TAG, "Finished unzipping file: $targetDirectory")
                callbackContext.success("Files successfully saved")
            } else {
                callbackContext.error("Error saving file")
            }
        } catch (e: Exception) {
            println("Error procesing file uri:: ${e.printStackTrace()}")
            callbackContext.error("Error saving file")
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