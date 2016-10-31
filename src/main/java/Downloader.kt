import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.paint.Color

import java.io.*
import java.util.ArrayList
import java.util.Arrays
import java.util.stream.Collectors

class Downloader( var url: String, var output: String, var gui: App) : Task<kotlin.Any>() {

    internal var title: String
    internal var id: String
    internal var app: App

    init {
        this.id = url.substring(url.indexOf("?v=") + 3, url.indexOf("=") + 12)
        title = ""
        app = gui
    }

    override fun call(): Any? {
        if (!Arrays.stream(File(output).listFiles()!!).filter { file -> file.name.contains(id) }.collect(Collectors.toList<File>()).isEmpty()) {
            Platform.runLater {
                app.info.fill = Color.GREEN
                app.info.text = "Video already converted!"
                app.download.isDisable = false
                app.urlField.isEditable = true
                app.outB.isDisable = false
                app.indicator.isVisible = false
            }
            return null
        }
        Platform.runLater { app.info.text = "Converting..." }

        val pb = ProcessBuilder("youtube-dl", "--get-title", "--", url)
        try {
            val p = pb.start()
            val `in` = p.inputStream
            val buffer = ByteArray(1024)
            var amountRead = `in`.read(buffer)
            while (amountRead > -1) {
                title = String(Arrays.copyOf(buffer, amountRead)).replace("\n".toRegex(), "")
                break
            }
        } catch (e: IOException) {
            Platform.runLater {
                app.info.fill = Color.FIREBRICK
                app.info.text = "Could not get video title!"
                app.download.isDisable = false
                app.urlField.isEditable = true
                app.outB.isDisable = false
                app.indicator.isVisible = false
            }
            return null
        }

        val ytdlProcess: Process
        val ffmpegProcess: Process
        val ytdlToFFmpegThread: Thread
        val ytdlErrGobler: Thread
        val ffmpegErrGobler: Thread

        val ytdl = ArrayList<String>()
        ytdl.add("youtube-dl")
        ytdl.add("-q") //quiet. No standard out.
        ytdl.add("-f") //Format to download. Attempts best audio-only, followed by best video/audio combo
        ytdl.add("bestaudio/best")
        ytdl.add("--no-playlist") //If the provided link is part of a Playlist, only grabs the video, not playlist too.
        ytdl.add("-4") //Forcing Ipv4 for OVH's Ipv6 range is blocked by youtube
        ytdl.add("--no-cache-dir") //We don't want random screaming
        ytdl.add("-o") //Output, output to STDout
        ytdl.add("-")

        val ffmpeg = ArrayList<String>()
        ffmpeg.add("ffmpeg")
        ffmpeg.add("-i") //Input file, specifies to read from STDin (pipe)
        ffmpeg.add("-")
        ffmpeg.add("-vcodec")
        ffmpeg.add("mp4")
        ffmpeg.add("-map") //Makes sure to only output audio, even if the specified format supports other streams
        ffmpeg.add("a")
        val f = File("$output/$title - $id.mp3")
        ffmpeg.add(f.path)

        try {
            val pBuilder = ProcessBuilder()

            ytdl.add("--")
            ytdl.add(url)
            pBuilder.command(ytdl)
            ytdlProcess = pBuilder.start()

            pBuilder.command(ffmpeg)
            ffmpegProcess = pBuilder.start()

            val ytdlProcessF = ytdlProcess
            val ffmpegProcessF = ffmpegProcess

            ytdlToFFmpegThread = object : Thread("ytdlToFFmpeg Bridge") {
                override fun run() {
                    var fromYTDL: InputStream? = null
                    var toFFmpeg: OutputStream? = null
                    try {
                        fromYTDL = ytdlProcessF.inputStream
                        toFFmpeg = ffmpegProcessF.outputStream

                        val buffer = ByteArray(1024)
                        var amountRead = fromYTDL!!.read(buffer)
                        while (!isInterrupted && amountRead > -1) {
                            toFFmpeg!!.write(buffer, 0, amountRead)
                            amountRead = fromYTDL.read(buffer)
                        }
                        toFFmpeg!!.flush()
                        Platform.runLater {
                            if (title == "") {
                                app.info.fill = Color.FIREBRICK
                                app.info.text = "Couldn't find video " + id
                            } else {
                                app.info.fill = Color.GREEN
                                app.info.text = "Converted " + title!!
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        showErrorDialog(e)
                    } finally {
                        try {
                            if (fromYTDL != null)
                                fromYTDL.close()
                        } catch (e: Throwable) {
                        }

                        try {
                            if (toFFmpeg != null)
                                toFFmpeg.close()
                        } catch (e: Throwable) {
                        }

                        // enable stuffs
                        Platform.runLater {
                            app.download.isDisable = false
                            app.urlField.isEditable = true
                            app.outB.isDisable = false
                            app.indicator.isVisible = false
                        }
                    }
                }
            }

            ytdlErrGobler = object : Thread("ytdlErrGobler") {
                override fun run() {
                    var fromYTDL: InputStream? = null
                    try {
                        fromYTDL = ytdlProcessF.errorStream
                        if (fromYTDL != null) {
                            val buffer = ByteArray(1024)
                            var amountRead = fromYTDL!!.read(buffer)
                            while (!isInterrupted && amountRead > -1) {
                                println("ERR YTDL: " + String(Arrays.copyOf(buffer, amountRead)))
                                amountRead = fromYTDL!!.read(buffer)
                            }
                        }
                    } catch (e: IOException) {
                        showErrorDialog(e)
                        e.printStackTrace()
                    } finally {
                        try {
                            if (fromYTDL != null)
                                fromYTDL!!.close()
                        } catch (ignored: Throwable) {
                        }

                    }
                }
            }

            ffmpegErrGobler = object : Thread("ffmpegErrGobler") {
                override fun run() {
                    var fromFFmpeg: InputStream? = null
                    try {
                        fromFFmpeg = ffmpegProcessF.errorStream
                        if (fromFFmpeg == null)
                            println("RemoteStream: FFmpeg-ErrGobler: fromYTDL is null")
                    } catch (e: IOException) {
                        showErrorDialog(e)
                        e.printStackTrace()
                    } finally {
                        try {
                            if (fromFFmpeg != null)
                                fromFFmpeg.close()
                        } catch (ignored: Throwable) {
                        }

                    }
                }
            }

            ytdlToFFmpegThread.start()
            ytdlErrGobler.start()
            ffmpegErrGobler.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    private fun showErrorDialog(e: Exception) {
        Platform.runLater {
            app.info.fill = Color.FIREBRICK
            app.info.text = "Could not convert video!"
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Error Converting Video"
            alert.headerText = "An error occurred while converting this video:\n$title - $id"
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            val exceptionText = sw.toString()

            val textArea = TextArea(exceptionText)
            textArea.isEditable = false
            textArea.isWrapText = true

            textArea.maxWidth = java.lang.Double.MAX_VALUE
            textArea.maxHeight = java.lang.Double.MAX_VALUE
            GridPane.setVgrow(textArea, Priority.ALWAYS)
            GridPane.setHgrow(textArea, Priority.ALWAYS)

            val expContent = GridPane()
            expContent.maxWidth = java.lang.Double.MAX_VALUE
            expContent.add(Label("Stacktrace:"), 0, 0)
            expContent.add(textArea, 0, 1)
            expContent.add(Label("If you believe this is a bug, feel free to create an new issue on GitHub."), 0, 2)

            // Set expandable Exception into the dialog pane.
            alert.dialogPane.expandableContent = expContent

            alert.showAndWait()
        }
    }
}