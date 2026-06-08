package com.gamelog.controller;

import com.gamelog.dto.*;
import com.gamelog.entity.GameLog;
import com.gamelog.service.GameLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/game-logs")
@RequiredArgsConstructor
public class GameLogController {

    private final GameLogService gameLogService;

    /**
     * 新增日志（异步写入）
     */
    @PostMapping
    public ResponseEntity<Result<Void>> createGameLog(@RequestBody GameLogCreateDTO dto) {
        gameLogService.createGameLog(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.success(null));
    }

    /**
     * 批量新增日志
     */
    @PostMapping("/batch")
    public ResponseEntity<Result<Void>> createGameLogBatch(@RequestBody List<GameLogCreateDTO> logs) {
        gameLogService.createGameLogBatch(logs);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.success(null));
    }

    /**
     * 分页查询日志
     */
    @GetMapping
    public Result<Page<GameLog>> queryGameLogs(GameLogQueryDTO queryDTO) {
        Page<GameLog> page = gameLogService.queryGameLogs(queryDTO);
        return Result.success(page);
    }

    /**
     * 查询单条日志
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<GameLog>> getGameLogById(@PathVariable Long id) {
        return gameLogService.getGameLogById(id)
                .map(gameLog -> ResponseEntity.ok(Result.success(gameLog)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.error(404, "日志不存在")));
    }

    /**
     * 删除日志
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteGameLog(@PathVariable Long id) {
        if (gameLogService.deleteGameLog(id)) {
            return ResponseEntity.ok(Result.success());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "日志不存在"));
    }

    /**
     * 统计数据
     */
    @GetMapping("/stats")
    public Result<GameLogStatsDTO> getStats() {
        return Result.success(gameLogService.getStats());
    }

    /**
     * 队列状态监控
     */
    @GetMapping("/queue-status")
    public Result<QueueStatusDTO> getQueueStatus() {
        return Result.success(gameLogService.getQueueStatus());
    }

    /**
     * 导出CSV
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(GameLogQueryDTO queryDTO) {
        // 限制最大导出数量
        if (queryDTO.getSize() == null || queryDTO.getSize() > 10000) {
            queryDTO.setSize(10000);
        }
        Page<GameLog> page = gameLogService.queryGameLogs(queryDTO);
        List<GameLog> logs = page.getContent();

        StringBuilder csv = new StringBuilder();
        csv.append("ID,游戏名称,玩家,操作,详情,游戏时长(分钟),游戏时间,记录时间\n");
        for (GameLog log : logs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s\n",
                    log.getId(),
                    escapeCsv(log.getGameName()),
                    escapeCsv(log.getPlayer()),
                    escapeCsv(log.getAction()),
                    escapeCsv(log.getDetail()),
                    log.getDuration() != null ? log.getDuration() : "",
                    log.getPlayTime(),
                    log.getCreatedAt()));
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "game_logs_" + System.currentTimeMillis() + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", filename);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private String escapeCsv(String field) {
        if (field == null) return "";
        return field.replace("\"", "\"\"");
    }
}
