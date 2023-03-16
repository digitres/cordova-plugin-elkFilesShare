package com.digitres.cordova.plugin
class FilePicker: CordovaPlugin(){

    override fun initialize(cordova: CordovaInterface?, webView: CordovaWebView?) {
        super.initialize(cordova, webView)
        echo()
        alert(
            "test title", "Test alert message","buttonlabel"
        )
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            //  this.eventsContext = callbackContext
            when (action){
                "echo" -> {
                    echo("Echo message ")
                    return true
                }
                "alert" -> {
                    alert("pick file", "Pick file requested message","buttonlabel")
                }
                "pickFile" -> {
                   pickFile()
                }

                else ->{
                    echo("Unknown action in BlockAppExit Plugin execute function: $action",callbackContext)
                    return false
                }
            }
        } catch (ex: Exception){
            callbackContext.error("FilePicker Plugin error: ${ex.message}");
            return false
        }

    }
    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/zip"
        folderPickerLauncher.launch(intent)
    }
    private var folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val data = result.data
                val isFileSaved = data?.data?.let { uri ->
                    saveFile(
                        uri,
                        destinationFolder
                    )
                } ?: kotlin.run { false }
                if (isFileSaved) {
                    println("fileSaved")
                } else {
                    println("fileNoteSaved")
                }
            } catch (e: Exception) {
                println("error getting file uri:: ${e.printStackTrace()}")
            }
        }

    private fun saveFile(uri: Uri, directory: File): Boolean {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val fileName =
            cursor?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        cursor?.close()

        return if (fileName != null) {
            val file = File(directory, fileName)
            file.outputStream().use { contentResolver.openInputStream(uri)?.copyTo(it) }
            true
        } else {
            false
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