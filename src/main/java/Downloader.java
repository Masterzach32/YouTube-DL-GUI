import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Downloader extends Task {

    String url, output, title, id;

    public Downloader(String url, String output) {
        this.url = url;
        this.output = output;
        this.id = url.substring(url.indexOf("?v=") + 3, url.indexOf("=") + 12);
    }

    public Object call() {
        if(!Arrays.stream(new File(output).listFiles()).filter(file -> file.getName().contains(id)).collect(Collectors.toList()).isEmpty()) {
            Platform.runLater(() -> {
                App.info.setFill(Color.GREEN);
                App.info.setText("Video already converted!");
                App.download.setDisable(false);
                App.urlField.setEditable(true);
                App.outB.setDisable(false);
                App.indicator.setVisible(false);
            });
            return null;
        }
        Platform.runLater(() -> App.info.setText("Converting..."));

        ProcessBuilder pb = new ProcessBuilder("youtube-dl", "--get-title", "--", url);
        try {
            Process p = pb.start();
            InputStream in = p.getInputStream();
            byte[] buffer = new byte[1024];
            int amountRead = -1;
            while (((amountRead = in.read(buffer)) > -1)) {
                title = new String(Arrays.copyOf(buffer, amountRead)).replaceAll("\n", "");
                break;
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                App.info.setFill(Color.FIREBRICK);
                App.info.setText("Could not get video title!");
                App.download.setDisable(false);
                App.urlField.setEditable(true);
                App.outB.setDisable(false);
                App.indicator.setVisible(false);
            });
            return null;
        }

        Process ytdlProcess, ffmpegProcess;
        Thread ytdlToFFmpegThread, ytdlErrGobler, ffmpegErrGobler;

        List<String> ytdl = new ArrayList<>();
        ytdl.add("youtube-dl");
        ytdl.add("-q"); //quiet. No standard out.
        ytdl.add("-f"); //Format to download. Attempts best audio-only, followed by best video/audio combo
        ytdl.add("bestaudio/best");
        ytdl.add("--no-playlist"); //If the provided link is part of a Playlist, only grabs the video, not playlist too.
        ytdl.add("-4"); //Forcing Ipv4 for OVH's Ipv6 range is blocked by youtube
        ytdl.add("--no-cache-dir"); //We don't want random screaming
        ytdl.add("-o"); //Output, output to STDout
        ytdl.add("-");

        List<String> ffmpeg = new ArrayList<>();
        ffmpeg.add("ffmpeg");
        ffmpeg.add("-i"); //Input file, specifies to read from STDin (pipe)
        ffmpeg.add("-");
        ffmpeg.add("-vcodec");
        ffmpeg.add("mp4");
        ffmpeg.add("-map"); //Makes sure to only output audio, even if the specified format supports other streams
        ffmpeg.add("a");
        File f = new File(output + "/" + title + " - " + id + ".mp3");
        ffmpeg.add(f.getPath());

        try {
            ProcessBuilder pBuilder = new ProcessBuilder();

            ytdl.add("--");
            ytdl.add(url);
            pBuilder.command(ytdl);
            ytdlProcess = pBuilder.start();

            pBuilder.command(ffmpeg);
            ffmpegProcess = pBuilder.start();

            final Process ytdlProcessF = ytdlProcess;
            final Process ffmpegProcessF = ffmpegProcess;

            ytdlToFFmpegThread = new Thread("ytdlToFFmpeg Bridge") {
                @Override
                public void run() {
                    InputStream fromYTDL = null;
                    OutputStream toFFmpeg = null;
                    try {
                        fromYTDL = ytdlProcessF.getInputStream();
                        toFFmpeg = ffmpegProcessF.getOutputStream();

                        byte[] buffer = new byte[1024];
                        int amountRead = -1;
                        while (!isInterrupted() && ((amountRead = fromYTDL.read(buffer)) > -1)) {
                            toFFmpeg.write(buffer, 0, amountRead);
                        }
                        toFFmpeg.flush();
                        Platform.runLater(() -> {
                            App.info.setFill(Color.GREEN);
                            App.info.setText("Converted " + title);
                        });
                    } catch (IOException e) {
                        //If the pipe being closed caused this problem, it was because it tried to write when it closed.
                        if (e.getMessage().contains("The pipe has been ended") || e.getMessage().contains("Broken pipe"))
                            System.out.println("RemoteStream encountered an 'error' : " + e.getMessage() + " (not really an error.. probably)");
                        else
                            e.printStackTrace();
                        showErrorDialog(e);
                    } finally {
                        try {
                            if (fromYTDL != null)
                                fromYTDL.close();
                        } catch (Throwable e) {
                        }
                        try {
                            if (toFFmpeg != null)
                                toFFmpeg.close();
                        } catch (Throwable e) {
                        }
                        // enable stuffs
                        Platform.runLater(() -> {
                            App.download.setDisable(false);
                            App.urlField.setEditable(true);
                            App.outB.setDisable(false);
                            App.indicator.setVisible(false);
                        });
                    }
                }
            };

            ytdlErrGobler = new Thread("ytdlErrGobler") {
                @Override
                public void run() {
                    InputStream fromYTDL = null;
                    try {
                        fromYTDL = ytdlProcessF.getErrorStream();
                        if (fromYTDL == null)
                            System.out.println("RemoteStream: YTDL-ErrGobler: fromYTDL is null");

                        byte[] buffer = new byte[1024];
                        int amountRead = -1;
                        while (!isInterrupted() && ((amountRead = fromYTDL.read(buffer)) > -1)) {
                            System.out.println("ERR YTDL: " + new String(Arrays.copyOf(buffer, amountRead)));
                        }
                    } catch (IOException e) {
                        showErrorDialog(e);
                        e.printStackTrace();
                    } finally {
                        try {
                            if (fromYTDL != null)
                                fromYTDL.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            };

            ffmpegErrGobler = new Thread("ffmpegErrGobler") {
                @Override
                public void run() {
                    InputStream fromFFmpeg = null;
                    try {
                        fromFFmpeg = ffmpegProcessF.getErrorStream();
                        if (fromFFmpeg == null)
                            System.out.println("RemoteStream: FFmpeg-ErrGobler: fromYTDL is null");

                        byte[] buffer = new byte[1024];
                        int amountRead = -1;
                        while (!isInterrupted() && ((amountRead = fromFFmpeg.read(buffer)) > -1)) {
                            String info = new String(Arrays.copyOf(buffer, amountRead));
                            /*if (info.contains("time=")) {
                                Matcher m = TIME_PATTERN.matcher(info);
                                if (m.find()) {
                                    timestamp = AudioTimestamp.fromFFmpegTimestamp(m.group());
                                }
                            }*/
                        }
                    } catch (IOException e) {
                        showErrorDialog(e);
                        e.printStackTrace();
                    } finally {
                        try {
                            if (fromFFmpeg != null)
                                fromFFmpeg.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            };

            ytdlToFFmpegThread.start();
            ytdlErrGobler.start();
            ffmpegErrGobler.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showErrorDialog(Exception e) {
        Platform.runLater(() -> {
            App.info.setFill(Color.FIREBRICK);
            App.info.setText("Could not convert video!");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Converting Video");
            alert.setHeaderText("An error occurred while converting this video:\n" + title + " - " + id);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String exceptionText = sw.toString();
            Label label = new Label("Stacktrace:");

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);
            expContent.add(new Label("If you believe this is a bug, feel free to create an new issue on GitHub."), 0, 2);

            // Set expandable Exception into the dialog pane.
            alert.getDialogPane().setExpandableContent(expContent);

            alert.showAndWait();
        });
    }
}