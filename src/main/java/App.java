/**
 * Created by zach on 10/28/16.
 */

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class App extends Application {

    static TextField urlField, outputField;
    static Button outB, download;
    static Text info;
    static ProgressIndicator indicator;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // bottom
        BorderPane borderPane = new BorderPane();

        // center
        GridPane pane = new GridPane();
        pane.setPadding(new Insets(10));
        pane.setHgap(10);
        pane.setVgap(10);

        HBox h = new HBox();
        Text title = new Text("YouTube to MP3 Converter");
        title.setFont(new Font("Calibri", 20));
        h.getChildren().addAll(title);
        h.setAlignment(Pos.CENTER);
        h.setPadding(new Insets(10, 0, 0, 0));
        borderPane.setTop(h);

        Text url = new Text("YouTube URL:");
        url.setFont(new Font("Calibri", 14));
        pane.add(url, 0, 0);

        urlField = new TextField();
        urlField.setPrefSize(300, 25);
        pane.add(urlField, 1, 0, 2, 1);

        Text output = new Text("Output Location:");
        output.setFont(new Font("Calibri", 14));
        pane.add(output, 0, 1);

        outputField = new TextField();
        outputField.setPrefSize(165, 25);
        outputField.setEditable(false);
        outputField.setText(System.getProperty("user.home"));
        pane.add(outputField, 1, 1);

        outB = new Button("Select Folder");
        outB.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File file = chooser.showDialog(stage);
            if(file != null)
                outputField.setText(file.getAbsolutePath());
        });
        pane.add(outB, 2, 1);

        info = new Text();
        info.setFont(new Font("Calibri", 16));
        info.setFill(Color.FIREBRICK);
        pane.add(info, 0, 2, 2, 2);

        h = new HBox();
        h.setAlignment(Pos.CENTER_LEFT);
        h.setAlignment(Pos.CENTER_RIGHT);
        download = new Button("Start");
        download.setOnAction(event -> {
            if(urlField.getText().length() < 11 || !urlField.getText().contains("youtube.com")) {
                info.setFill(Color.FIREBRICK);
                info.setText("Please enter a valid YouTube URL!");
            } else {
                // disable stuffs
                download.setDisable(true);
                urlField.setEditable(false);
                outB.setDisable(true);
                indicator.setVisible(true);
                Task t = new Downloader(urlField.getText(), outputField.getText());
                info.setFill(Color.BLUE);
                Thread task = new Thread(t);
                task.setDaemon(true);
                task.start();
            }
        });
        indicator = new ProgressIndicator(-1);
        indicator.setVisible(false);
        h.getChildren().addAll(indicator, download);
        pane.add(h, 2, 2);


        //pane.setGridLinesVisible(true);
        borderPane.setCenter(pane);

        Scene scene = new Scene(borderPane, 400, 160);

        stage.setTitle("YouTube to MP3");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        if(!checkBinaries()) {
            System.exit(1);
        }
    }

    private boolean checkBinaries() {
        // youtube-dl
        try {
            new ProcessBuilder("youtube-dl").start().waitFor();
        } catch (InterruptedException | IOException e) {
            showMissingBinary("youtube-dl", "https://rg3.github.io/youtube-dl/download.html");
            return false;
        }
        try {
            new ProcessBuilder("ffmpeg").start().waitFor();
        } catch (InterruptedException | IOException e) {
            showMissingBinary("FFMPEG", "https://www.ffmpeg.org/download.html");
            return false;
        }
        return true;
    }

    private void showMissingBinary(String name, String link) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Missing " + name + " Executable");
        alert.setHeaderText("Please Install " + name + "!");
        alert.setContentText("This program requires " + name + ", and cannot run without it.\nYou can download and install " + name + " here:");
        GridPane ex = new GridPane();
        ex.add(new Hyperlink(link), 0, 0);
        alert.getDialogPane().setExpandableContent(ex);

        alert.showAndWait();
    }
}