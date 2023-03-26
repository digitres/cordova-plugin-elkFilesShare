package com.digitres.cordova.plugin

import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.content.DialogInterface
import org.apache.cordova.CordovaInterface
import org.apache.cordova.CordovaWebView
import org.apache.cordova.PluginResult

import android.content.Context

import android.util.Log
import android.view.Window
import android.view.View
import android.view.WindowManager;
import android.app.ActionBar
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.content.ComponentName
import android.app.ActivityManager
import android.content.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.FileInputStream

public class elkFilesShare: CordovaPlugin(){

    override fun initialize(cordova: CordovaInterface?, webView: CordovaWebView?) {
        super.initialize(cordova, webView)
//        alert(
//            "test title", "Test alert message","buttonlabel"
//        )
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {

            when (action){

                "processFile" -> {
                    val obj: JSONObject = args.getJSONObject(0)
                    val appContext = this.cordova.getActivity().getApplicationContext()
                    val targetDirectory = File(appContext?.getExternalFilesDir(null)?.getAbsolutePath())
                    //val targetDirectory = File(appContext?.getFilesDir()?.getAbsolutePath())
                    val result = processFile(obj , targetDirectory,callbackContext)
                    return true
                }
                "echo" -> {
                    echo("Echo message ", callbackContext)
                    return true
                }
                "alert" -> {
                    alert("pick file", "Pick file requested message","buttonlabel")
                    return true
                }

//                "pickFile" -> {
//                    pickFile()
//                    return true
//                }

                else ->{
                    echo("Unknown action in ELKFilesShare Plugin execute function: $action",callbackContext)
                    return false
                }
            }
        } catch (ex: Exception){
            callbackContext.error("elkFilesShare Plugin error: ${ex.message}")
            return false
        }

    }
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

    private fun processFile(obj: JSONObject, targetDirectory: File, callbackContext: CallbackContext) {

        try {
            val sourceUri: Uri? = if (obj.has("uri")) Uri.parse(obj.getString("uri")) else null
            if (sourceUri == null) {
                callbackContext.error("Invalid URI passed")
                return
            }
            println("processing: $sourceUri")
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
                unzip(file,targetDirectory)
                callbackContext.success("File successfully saved")
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
        message: String= "Test echo message",
        callbackContext: CallbackContext
    ) {
        if (message.isNotEmpty()) {
            callbackContext.success(message)
        } else {
            callbackContext.error("Expected one non-empty string argument.")
        }
    }

    @kotlin.jvm.Synchronized
    fun alert(
        title: String,
        message: String,
        buttonLabel: String,
    ) {
        Builder(cordova.getActivity())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setNeutralButton(buttonLabel){dialog, which ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}