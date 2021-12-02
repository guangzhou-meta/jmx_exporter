package io.prometheus.jmx;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class LogTransport implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(LogTransport.class.getName());

    private static final Object LOGGER_LOCKER = new Object();
    private static final String JMX_THREADS = "jmx-threads";

    private static SimpleDateFormat dateFormatter;

    private String remoteServerAddress;
    private String appName;
    private List<TargetFileConfig> targetFileList;
    private SliceStyle sliceStyle;
    private boolean logThreadInfos;

    private boolean running;

    public LogTransport() {
        sliceStyle = SliceStyle.Day;
    }

    public static class Utils {

        private static String wrapString(String str) {
            return str == null ? null : str.trim();
        }

        private static boolean isNotEmptyString(String str) {
            return str != null && str.length() > 1;
        }

        private static boolean isValidLogFile(String filePath) {
            if (!isNotEmptyString(filePath)) {
                return false;
            }
            File logFile = new File(filePath);
            return logFile.exists() &&
                    logFile.isFile();
        }

        private static void safetyClose(Closeable stream) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    LOGGER.severe("Reader closed failed: " + e);
                }
            }
        }

        private static long streamReader(Reader reader, StringBuilder stringBuilder) throws Exception {
            long readSum = 0;
            int readCount;
            char[] buffer = new char[8192];
            while ((readCount = reader.read(buffer)) > 0) {
                stringBuilder.append(buffer, 0, readCount);
                readSum += readCount;
            }
            return readSum;
        }

        private static TargetFileConfigOperationStatus sendContent(String serverAddress, String appName, String targetName, StringBuilder content) throws Exception {
            if (!isNotEmptyString(targetName) ||
                    content.length() == 0) {
                return TargetFileConfigOperationStatus.SendFail;
            }
            PrintWriter outputStream = null;
            InputStreamReader inputStream = null;
            try {
                URL server = new URL(serverAddress);
                URLConnection connection = server.openConnection();
                connection.setRequestProperty("accept", "*/*");
                connection.setRequestProperty("connection", "Keep-Alive");
                connection.setRequestProperty("appName", appName);
                connection.setRequestProperty("targetName", targetName);
                connection.setConnectTimeout(3000); // connection timeout 3s
                connection.setReadTimeout(5000); // read timeout 5s
                connection.setDoOutput(true);
                connection.setDoInput(true);
                outputStream = new PrintWriter(connection.getOutputStream());
                outputStream.print(content);
                outputStream.flush();
                inputStream = new InputStreamReader(connection.getInputStream());
                StringBuilder stringBuilder = new StringBuilder();
                Utils.streamReader(inputStream, stringBuilder);
                LOGGER.fine(String.format("File(%s) content send response: %s", targetName, stringBuilder));
            } finally {
                safetyClose(outputStream);
                safetyClose(inputStream);
            }

            return TargetFileConfigOperationStatus.SendSuccess;
        }
    }

    private enum TargetFileConfigOperationStatus {
        ReadFail("read failed"),
        SendSuccess("send succeeded"),
        SendFail("send failed");

        private final String msg;

        TargetFileConfigOperationStatus(String msg) {
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }

    private enum SliceStyle {
        Year("yyyy"),
        Month("yyyy-MM"),
        Day("yyyy-MM-dd");

        private final String pattern;

        SliceStyle(String pattern) {
            this.pattern = pattern;
        }

        private static SliceStyle getSliceStyle(String style) {
            if (Utils.isNotEmptyString(style)) {
                style = style.toLowerCase();
                for (SliceStyle value : values()) {
                    if (value.name().toLowerCase().equals(style)) {
                        return value;
                    }
                }
            }
            return Day;
        }
    }

    protected static class TargetFileConfig {
        private String filePath;
        private String targetName;

        private String lastDate;
        private long lastIndex = -1;

        private TargetFileConfig() {
        }

        public static TargetFileConfig getInstance() {
            return new TargetFileConfig();
        }

        public TargetFileConfig assign(String file) {
            int nameIndex = file.indexOf(":");
            String filePath;
            String targetName;
            if (nameIndex > 0) {
                filePath = file.substring(0, nameIndex);
                targetName = file.substring(nameIndex + 1);
            } else {
                filePath = file;
                targetName = new File(filePath).getName();
            }
            return setFilePath(filePath)
                    .setTargetName(targetName);
        }

        public String getFilePath() {
            return filePath;
        }

        public TargetFileConfig setFilePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public String getTargetName() {
            return targetName;
        }

        public TargetFileConfig setTargetName(String targetName) {
            this.targetName = targetName;
            return this;
        }

        public boolean isValid() {
            return Utils.isNotEmptyString(filePath) &&
                    Utils.isValidLogFile(filePath) &&
                    Utils.isNotEmptyString(targetName);
        }

        public void readFile(String serverAddress, String appName) {
            if (!isValid()) {
                return;
            }
            FileReader fileReader = null;
            try {
                fileReader = new FileReader(getFilePath());
                String currentDate = dateFormatter.format(new Date());
                if (!currentDate.equals(lastDate)) {
                    lastDate = currentDate;
                    lastIndex = -1;
                }
                long skipCount = fileReader.skip(lastIndex + 1);
                LOGGER.fine(String.format("File(%s) content skip count %d",
                        this, skipCount));
                StringBuilder stringBuilder = new StringBuilder();
                lastIndex += Utils.streamReader(fileReader, stringBuilder);
                TargetFileConfigOperationStatus result = Utils.sendContent(serverAddress, appName, targetName, stringBuilder);
                LOGGER.fine(String.format("File(%s) content %s",
                        this, result));
            } catch (Exception e) {
                LOGGER.severe(String.format("File(%s) content %s: %s",
                        this, TargetFileConfigOperationStatus.ReadFail, e));
            } finally {
                Utils.safetyClose(fileReader);
            }
        }

        @Override
        public String toString() {
            return "TargetFileConfig{" +
                    "filePath='" + filePath + '\'' +
                    ", targetName='" + targetName + '\'' +
                    '}';
        }
    }

    public String getRemoteServerAddress() {
        return remoteServerAddress;
    }

    public LogTransport setRemoteServerAddress(String remoteServerAddress) {
        this.remoteServerAddress = Utils.wrapString(remoteServerAddress);
        return this;
    }

    public String getAppName() {
        return appName;
    }

    public LogTransport setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    public boolean isEnableThreadInfos() {
        return logThreadInfos;
    }

    public LogTransport enableThreadInfos(Boolean enable) {
        if (enable == null) {
            enable = false;
        }
        this.logThreadInfos = enable;
        return this;
    }

    public List<TargetFileConfig> getTargetFileList() {
        return targetFileList;
    }

    public LogTransport setTargetFiles(String targetFiles) {
        List<TargetFileConfig> targetFileList = new ArrayList<TargetFileConfig>();
        String[] filePathList = Utils.wrapString(targetFiles).split(",");
        for (String file : filePathList) {
            targetFileList.add(TargetFileConfig
                    .getInstance()
                    .assign(file));
        }
        return setTargetFileList(targetFileList);
    }

    public LogTransport setTargetFileList(List<TargetFileConfig> targetFileList) {
        this.targetFileList = targetFileList;
        return this;
    }

    public SliceStyle getSliceStyle() {
        return sliceStyle;
    }

    public LogTransport setSliceStyle(String style) {
        return setSliceStyle(SliceStyle.getSliceStyle(style));
    }

    public LogTransport setSliceStyle(SliceStyle sliceStyle) {
        this.sliceStyle = sliceStyle;
        return this;
    }

    public boolean isRunning() {
        return running;
    }

    private void logThreadInfo() {
        StringBuilder content = ThreadInfos.logThreadsInfo();
        if (content.length() > 0) {
            try {
                TargetFileConfigOperationStatus result = Utils.sendContent(remoteServerAddress, appName,
                        JMX_THREADS, content);
                LOGGER.fine(String.format("File(%s) content %s",
                        JMX_THREADS, result));
            } catch (Exception e) {
                LOGGER.severe(String.format("File(%s) content %s: %s",
                        JMX_THREADS, TargetFileConfigOperationStatus.ReadFail, e));
            }
        }
    }

    @Override
    public void run() {
        synchronized (LOGGER_LOCKER) {
            running = true;
            if (dateFormatter == null) {
                dateFormatter = new SimpleDateFormat(sliceStyle.pattern);
            }
            boolean hasRemoteServerAddress = Utils.isNotEmptyString(remoteServerAddress);
            if (hasRemoteServerAddress &&
                    targetFileList != null &&
                    !targetFileList.isEmpty()) {
                for (TargetFileConfig targetFileConfig : targetFileList) {
                    if (targetFileConfig != null && targetFileConfig.isValid()) {
                        targetFileConfig.readFile(remoteServerAddress, appName);
                    }
                }
            }
            if (hasRemoteServerAddress &&
                    logThreadInfos) {
                logThreadInfo();
            }
        }
        running = false;
    }
}
