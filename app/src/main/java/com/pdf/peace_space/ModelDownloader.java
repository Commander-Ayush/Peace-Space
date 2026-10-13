package com.pdf.peace_space;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModelDownloader {

    private static final String TAG = "ModelDownloader";
    public static final String DEFAULT_MODEL_FILENAME = "gemma3-1b-it-int4.task";

    private final Context context;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;
    private HttpURLConnection activeConnection;

    public interface DownloadCallback {
        void onProgress(int progressPercent, long downloadedBytes, long totalBytes);
        void onCompleted(File modelFile);
        void onError(Exception e);
    }

    public ModelDownloader(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File getTargetModelFile(Context context) {
        return new File(context.getFilesDir(), DEFAULT_MODEL_FILENAME);
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public void downloadModel(String modelUrl, DownloadCallback callback) {
        if (isDownloading) {
            return;
        }

        isDownloading = true;

        executorService.execute(() -> {
            File outputFile = getTargetModelFile(context);
            File tempFile = new File(context.getFilesDir(), DEFAULT_MODEL_FILENAME + ".tmp");

            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                String currentUrl = modelUrl;
                int redirectCount = 0;
                int responseCode = -1;

                while (redirectCount < 5) {
                    URL url = new URL(currentUrl);
                    activeConnection = (HttpURLConnection) url.openConnection();
                    activeConnection.setConnectTimeout(20000);
                    activeConnection.setReadTimeout(30000);
                    activeConnection.setInstanceFollowRedirects(false);

                    if (BuildConfig.HF_TOKEN != null && !BuildConfig.HF_TOKEN.trim().isEmpty()) {
                        activeConnection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HF_TOKEN.trim());
                    }

                    activeConnection.connect();
                    responseCode = activeConnection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                            || responseCode == 307
                            || responseCode == 308) {
                        String newUrl = activeConnection.getHeaderField("Location");
                        if (newUrl == null) {
                            throw new Exception("Redirected with empty Location header");
                        }
                        currentUrl = newUrl;
                        activeConnection.disconnect();
                        redirectCount++;
                    } else {
                        break;
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new Exception("Authentication failed (HTTP " + responseCode + "). Please check your Hugging Face token in local.properties.");
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP response code: " + responseCode);
                }

                long fileLength = activeConnection.getContentLengthLong();
                inputStream = activeConnection.getInputStream();
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[16384];
                long totalBytesRead = 0;
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new Exception("Download cancelled");
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (fileLength > 0) {
                        final int progress = (int) ((totalBytesRead * 100) / fileLength);
                        final long currentTotal = totalBytesRead;
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onProgress(progress, currentTotal, fileLength);
                            }
                        });
                    }
                }

                outputStream.flush();
                outputStream.close();
                outputStream = null;
                inputStream.close();
                inputStream = null;

                if (tempFile.renameTo(outputFile)) {
                    isDownloading = false;
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onCompleted(outputFile);
                        }
                    });
                } else {
                    throw new Exception("Failed to rename temporary download file");
                }

            } catch (Exception e) {
                isDownloading = false;
                Log.e(TAG, "Error downloading model", e);
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete temp download file");
                    }
                }
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (activeConnection != null) activeConnection.disconnect();
                } catch (Exception ignored) {}
            }
        });
    }

    public void cancelDownload() {
        executorService.shutdownNow();
        if (activeConnection != null) {
            try {
                activeConnection.disconnect();
            } catch (Exception ignored) {}
        }
        isDownloading = false;
    }
}
