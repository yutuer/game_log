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
 * 用法：
 *   java com.gamelog.StressTest [选项]
 *
 * 选项：
 *   --cloud             云服务器模式（300 TPS，默认 10 分钟）
 *   --tps=N             目标 TPS（覆盖默认值）
 *   --servers=N         游戏服数量
 *   --players=N         每服玩家数
 *   --duration=N        测试持续时间（分钟）
 *   --url=URL           目标地址（默认 http://localhost:8080/api/game-logs）
 *   --help              显示帮助
 *
 * 示例：
 *   java com.gamelog.StressTest                              ← 本地 3000 TPS
 *   java com.gamelog.StressTest --cloud                      ← 云上 300 TPS
 *   java com.gamelog.StressTest --tps=500 --duration=10      ← 自定义 500 TPS
 *   java com.gamelog.StressTest --url=http://10.0.0.1:8080/api/game-logs --cloud
 *
 * TPS = servers × players × 10(条/5s) × 1000 ÷ 5000(ms)
 * 即 TPS = servers × players × 2
 */
public class StressTest {

    // ========== 默认参数 ==========
    private static final int LOGS_PER_PLAYER = 10;
    private static final int INTERVAL_MS = 5000;

    private static String BASE_URL = "http://localhost:8080/api/game-logs";
    private static int SERVER_COUNT = 10;
    private static int PLAYERS_PER_SERVER = 150;
    private static long TEST_DURATION_MINUTES = 5;

    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong successCount = new AtomicLong(0);
    private static final AtomicLong failCount = new AtomicLong(0);
    private static final AtomicLong totalBytes = new AtomicLong(0);

    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) throws Exception {
        // 增大 HTTP Keep-Alive 连接池上限（默认仅 5），减少端口耗尽风险
        System.setProperty("http.maxConnections", "1000");

        parseArgs(args);

        long expectedTps = (long) SERVER_COUNT * PLAYERS_PER_SERVER * LOGS_PER_PLAYER * 1000L / INTERVAL_MS;
        System.out.println("========================================");
        System.out.println("  Game Log Stress Test");
        System.out.println("========================================");
        System.out.println("  URL: " + BASE_URL);
        System.out.println("  Servers: " + SERVER_COUNT);
        System.out.println("  Players per server: " + PLAYERS_PER_SERVER);
        System.out.println("  Logs per player per 5s: " + LOGS_PER_PLAYER);
        System.out.println("  Expected TPS: " + expectedTps);
        System.out.println("  Duration: " + TEST_DURATION_MINUTES + " minute(s)");
        System.out.println("  Expected total: ~" + (expectedTps * TEST_DURATION_MINUTES * 60) + " requests");
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

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                System.exit(0);
            } else if ("--cloud".equals(arg)) {
                // 云模式：保守参数
                SERVER_COUNT = 3;
                PLAYERS_PER_SERVER = 50;
                TEST_DURATION_MINUTES = 10;
            } else if (arg.startsWith("--tps=")) {
                // 自定义 TPS：自动分配 servers × players = TPS / 2
                int tps = Integer.parseInt(arg.substring(6));
                int totalPlayers = tps / 2;  // TPS = n × 2
                if (totalPlayers < 1) totalPlayers = 1;
                // 优先用 10 服平均分配
                SERVER_COUNT = Math.min(totalPlayers, 10);
                PLAYERS_PER_SERVER = (int) Math.ceil((double) totalPlayers / SERVER_COUNT);
            } else if (arg.startsWith("--servers=")) {
                SERVER_COUNT = Integer.parseInt(arg.substring(10));
            } else if (arg.startsWith("--players=")) {
                PLAYERS_PER_SERVER = Integer.parseInt(arg.substring(10));
            } else if (arg.startsWith("--duration=")) {
                TEST_DURATION_MINUTES = Integer.parseInt(arg.substring(11));
            } else if (arg.startsWith("--url=")) {
                BASE_URL = arg.substring(6);
                // 去掉末尾 /
                if (BASE_URL.endsWith("/")) {
                    BASE_URL = BASE_URL.substring(0, BASE_URL.length() - 1);
                }
            } else {
                System.err.println("未知参数: " + arg);
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java com.gamelog.StressTest [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --cloud              云服务器模式（300 TPS, 10分钟）");
        System.out.println("  --tps=N              目标 TPS（覆盖默认值）");
        System.out.println("  --servers=N          游戏服数量");
        System.out.println("  --players=N          每服玩家数");
        System.out.println("  --duration=N         测试持续时间（分钟）");
        System.out.println("  --url=URL            目标地址");
        System.out.println("  --help               显示帮助");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  StressTest                             本地 3000 TPS");
        System.out.println("  StressTest --cloud                     云上 300 TPS");
        System.out.println("  StressTest --tps=500 --duration=10     自定义 500 TPS");
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
        int round = 0;

        while (System.currentTimeMillis() < endTime) {
            round++;
            int batchSuccess = 0;
            int batchFail = 0;
            long batchStart = System.currentTimeMillis();

            for (int i = 0; i < LOGS_PER_PLAYER; i++) {
                long reqStart = System.currentTimeMillis();
                try {
                    sendLog(serverId, playerName, "action" + (i + 1), "detail" + System.nanoTime());
                    batchSuccess++;
                } catch (Exception e) {
                    batchFail++;
                    System.out.printf("[FAIL] %s | req#%d | %s: %s%n",
                        playerName, i + 1, e.getClass().getSimpleName(), e.getMessage());
                }
                long reqCost = System.currentTimeMillis() - reqStart;
                if (reqCost > 100) {
                    System.out.printf("[SLOW] %s | req#%d cost=%dms%n", playerName, i + 1, reqCost);
                }
            }

            totalRequests.addAndGet(LOGS_PER_PLAYER);
            successCount.addAndGet(batchSuccess);
            failCount.addAndGet(batchFail);

            long batchCost = System.currentTimeMillis() - batchStart;
            System.out.printf("[SEND] %s | round=%d | ok=%d fail=%d | cost=%dms | sleep=%ds%n",
                playerName, round, batchSuccess, batchFail, batchCost, INTERVAL_MS / 1000);

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
        // 读取并丢弃响应体，使连接可被 keep-alive 缓存复用
        try (java.io.InputStream is = conn.getInputStream()) {
            while (is.read() != -1) {}
        }

        if (responseCode != 202 && responseCode != 200) {
            System.out.printf("[ERR] %s | HTTP %d%n", player, responseCode);
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
