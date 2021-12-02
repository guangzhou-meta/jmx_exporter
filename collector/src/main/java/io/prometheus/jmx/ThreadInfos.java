package io.prometheus.jmx;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ThreadInfos {
    private static final SimpleDateFormat dateFormatter;

    static {
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    }

    protected static StringBuilder logThreadsInfo() {
        String currentDate = dateFormatter.format(new Date());
        StringBuilder stringBuilder = new StringBuilder();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            stringBuilder.append("[").append(currentDate).append("]") // DateTime
                    .append("\n")
                    .append(thread.toString()) // ThreadName
                    .append("  ")
                    .append("[")
                    .append("Id=").append(thread.getId())
                    .append(", ")
                    .append("State=").append(thread.getState().name())
                    .append(", ")
                    .append("Alive=").append(thread.isAlive())
                    .append(", ")
                    .append("Daemon=").append(thread.isDaemon())
                    .append(", ")
                    .append("Interrupted=").append(thread.isInterrupted())
                    .append("]")
                    .append("\n");
            for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
                stringBuilder.append("  ")
                        .append(stackTraceElement.toString())
                        .append("\n");
            }
        }
        return stringBuilder;
    }
}
