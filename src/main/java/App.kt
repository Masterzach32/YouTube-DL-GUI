import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.stage.DirectoryChooser
import javafx.stage.Stage

import java.io.IOException

class App : Application() {

    internal var urlField: TextField
    internal var outputField: TextField
    internal var outB: Button
    internal var download: Button
    internal var info: Text
    internal var indicator: ProgressIndicator

    init {
        outputField = TextField()
        outB = Button()
        urlField = TextField()
        download = Button()
        info = Text()
        indicator = ProgressIndicator()
    }

    override fun start(stage: Stage) {
        // bottom
        val borderPane = BorderPane()

        // center
        val pane = GridPane()
        pane.padding = Insets(10.0)
        pane.hgap = 10.0
        pane.vgap = 10.0

        var h = HBox()
        val title = Text("YouTube to MP3 Converter")
        title.font = Font("Calibri", 20.0)
        h.children.addAll(title)
        h.alignment = Pos.CENTER
        h.padding = Insets(10.0, 0.0, 0.0, 0.0)
        borderPane.top = h

        val url = Text("YouTube URL:")
        url.font = Font("Calibri", 14.0)
        pane.add(url, 0, 0)

        urlField = TextField()
        urlField.setPrefSize(300.0, 25.0)
        pane.add(urlField, 1, 0, 2, 1)

        val output = Text("Output Location:")
        output.font = Font("Calibri", 14.0)
        pane.add(output, 0, 1)

        outputField.setPrefSize(165.0, 25.0)
        outputField.isEditable = false
        outputField.text = System.getProperty("user.home")
        pane.add(outputField, 1, 1)

        outB = Button("Select Folder")
        outB.setOnAction { event ->
            val chooser = DirectoryChooser()
            val file = chooser.showDialog(stage)
            if (file != null)
                outputField.text = file.absolutePath
        }
        pane.add(outB, 2, 1)

        info = Text()
        info.font = Font("Calibri", 16.0)
        info.fill = Color.FIREBRICK
        pane.add(info, 0, 2, 2, 2)

        h = HBox()
        h.alignment = Pos.CENTER_LEFT
        h.alignment = Pos.CENTER_RIGHT
        download = Button("Start")
        download.setOnAction { event ->
            if (urlField.text.length < 11 || !urlField.text.contains("youtube.com") || urlField.text.indexOf("?v=") + 14 > urlField.text.length) {
                info.fill = Color.FIREBRICK
                info.text = "Please enter a valid YouTube URL!"
            } else {
                // disable stuffs
                download.isDisable = true
                urlField.isEditable = false
                outB.isDisable = true
                indicator.isVisible = true
                val t = Downloader(urlField.text, outputField.text, this)
                info.fill = Color.BLUE
                val task = Thread(t)
                task.isDaemon = true
                task.start()
            }
        }
        indicator = ProgressIndicator(-1.0)
        indicator.isVisible = false
        h.children.addAll(indicator, download)
        pane.add(h, 2, 2)

        borderPane.center = pane

        val scene = Scene(borderPane, 400.0, 160.0)

        stage.title = "YouTube to MP3"
        stage.scene = scene
        stage.isResizable = false
        stage.show()

        if (!checkBinaries()) {
            System.exit(1)
        }
    }

    private fun checkBinaries(): Boolean {
        try {
            ProcessBuilder("youtube-dl").start().waitFor()
        } catch (e: InterruptedException) {
            showMissingBinary("youtube-dl", "https://rg3.github.io/youtube-dl/download.html")
            return false
        } catch (e: IOException) {
            showMissingBinary("youtube-dl", "https://rg3.github.io/youtube-dl/download.html")
            return false
        }

        try {
            ProcessBuilder("ffmpeg").start().waitFor()
        } catch (e: InterruptedException) {
            showMissingBinary("FFMPEG", "https://www.ffmpeg.org/download.html")
            return false
        } catch (e: IOException) {
            showMissingBinary("FFMPEG", "https://www.ffmpeg.org/download.html")
            return false
        }

        return true
    }

    private fun showMissingBinary(name: String, link: String) {
        val alert = Alert(Alert.AlertType.WARNING)
        alert.title = "Missing $name Executable"
        alert.headerText = "Please Install $name!"
        alert.contentText = "This program requires $name, and cannot run without it.\nYou can download and install $name here:"
        val ex = GridPane()
        ex.add(Hyperlink(link), 0, 0)
        alert.dialogPane.expandableContent = ex

        alert.showAndWait()
    }
}

fun main(args: Array<String>) = Application.launch(*args)