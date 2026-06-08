package com.gamelog;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高强度压力测试工具
 *
 * 测试场景：
 * - 10个游戏服
 * - 每服200人
 * - 每5秒每人10条日志
 * - 持续1小时
 *
 * 预期负载：4,000 TPS，1小时约14,400,000条
 */
public class StressTest {

    private static final String BASE_URL = "http://localhost:8080/api/game-logs";
    private static final int SERVER_COUNT = 10;           // 游戏服数量
    private static final int PLAYERS_PER_SERVER = 200;    // 每服玩家数
    private static final int LOGS_PER_PLAYER = 10;       // 每5秒每人发送日志数
    private static final int INTERVAL_MS = 5000;        // 发送间隔（毫秒）
    private static final long TEST_DURATION_MINUTES = 12L * 5;    // 测试持续时间（分钟）
    
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong successCount = new AtomicLong(0);
    private static final AtomicLong failCount = new AtomicLong(0);
    private static final AtomicLong totalBytes = new AtomicLong(0);

    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Game Log Stress Test");
        System.out.println("========================================");
        System.out.println("  Servers: " + SERVER_COUNT);
        System.out.println("  Players per server: " + PLAYERS_PER_SERVER);
        System.out.println("  Logs per player per 5s: " + LOGS_PER_PLAYER);
        System.out.println("  Expected TPS: " + (SERVER_COUNT * PLAYERS_PER_SERVER * LOGS_PER_PLAYER * 1000L / INTERVAL_MS));
        System.out.println("  Duration: " + TEST_DURATION_MINUTES + " minute(s)");
        System.out.println("========================================\n");

        ExecutorService executor = Executors.newFixedThreadPool(SERVER_COUNT + 2);

        // 启动统计线程
        ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(new StatsReporter(), 10, 10, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis() + TEST_DURATION_MINUTES * 60 * 1000L;

        // 为每个游戏服创建一个线程池
        for (int serverId = 1; serverId <= SERVER_COUNT; serverId++) {
            final int server = serverId;
            executor.submit(() -> {
                runServer(server, endTime);
            });
        }

        // 等待测试结束
        executor.shutdown();
        executor.awaitTermination(TEST_DURATION_MINUTES + 1, TimeUnit.MINUTES);

        statsExecutor.shutdown();
        statsExecutor.awaitTermination(10, TimeUnit.SECONDS);

        printFinalReport();
    }

    private static void runServer(int serverId, long endTime) {
        ExecutorService playerExecutor = Executors.newFixedThreadPool(PLAYERS_PER_SERVER);

        for (int playerId = 1; playerId <= PLAYERS_PER_SERVER; playerId++) {
            final int player = playerId;
            playerExecutor.submit(() -> {
                runPlayer(serverId, player, endTime);
            });
        }

        playerExecutor.shutdown();
    }

    private static void runPlayer(int serverId, int playerId, long endTime) {
        String playerName = "Server" + serverId + "-Player" + playerId;

        while (System.currentTimeMillis() < endTime) {
            int batchSuccess = 0;
            int batchFail = 0;

            for (int i = 0; i < LOGS_PER_PLAYER; i++) {
                try {
                    sendLog(serverId, playerName, "action" + (i + 1), "detail" + System.nanoTime());
                    batchSuccess++;
                } catch (Exception e) {
                    batchFail++;
                }
            }

            totalRequests.addAndGet(LOGS_PER_PLAYER);
            successCount.addAndGet(batchSuccess);
            failCount.addAndGet(batchFail);

            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void sendLog(int serverId, String player, String action, String detail) throws Exception {
        URL url = new URL(BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        String json = String.format(
            "{\"gameName\":\"Game%d\",\"player\":\"%s\",\"action\":\"%s\",\"detail\":\"%s\",\"playTime\":\"%s\"}",
            serverId,
            player,
            action,
            detail,
            LocalDateTime.now().format(timeFormatter)
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            os.write(bytes);
            totalBytes.addAndGet(bytes.length);
        }

        int responseCode = conn.getResponseCode();
        conn.disconnect();

        if (responseCode != 202 && responseCode != 200) {
            throw new RuntimeException("HTTP " + responseCode);
        }
    }

    static class StatsReporter implements Runnable {
        private long startTime = System.currentTimeMillis();

        @Override
        public void run() {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long requests = totalRequests.get();
            long success = successCount.get();
            long fail = failCount.get();

            double avgTps = elapsed > 0 ? requests / (double) elapsed : 0;
            double instantTps = 1000.0 * success / INTERVAL_MS;

            System.out.printf("[%s] Requests: %d | Success: %d | Fail: %d | Avg TPS: %.1f | Total data: %.2f MB%n",
                formatElapsed(elapsed),
                requests,
                success,
                fail,
                avgTps,
                totalBytes.get() / (1024.0 * 1024.0)
            );
        }

        private String formatElapsed(long seconds) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            return String.format("%02d:%02d:%02d", h, m, s);
        }
    }

    private static void printFinalReport() {
        long total = totalRequests.get();
        long success = successCount.get();
        long fail = failCount.get();
        double mb = totalBytes.get() / (1024.0 * 1024.0);

        System.out.println("\n========================================");
        System.out.println("  Final Report");
        System.out.println("========================================");
        System.out.println("  Total requests: " + total);
        System.out.println("  Success: " + success);
        System.out.println("  Fail: " + fail);
        System.out.println("  Total data sent: " + String.format("%.2f MB", mb));
        if (total > 0) {
            System.out.println("  Success rate: " + String.format("%.2f%%", 100.0 * success / total));
        }
        System.out.println("========================================");
    }
}
