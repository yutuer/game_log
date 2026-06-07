package com.gamelog.controller;

import com.gamelog.dto.*;
import com.gamelog.entity.GameLog;
import com.gamelog.service.GameLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Result<Void>> createGameLogBatch(@RequestBody GameLogBatchCreateDTO dto) {
        gameLogService.createGameLogBatch(dto);
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
}
