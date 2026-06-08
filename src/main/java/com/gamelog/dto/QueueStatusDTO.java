package com.gamelog.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueueStatusDTO {
    private int queueSize;           // 当前队列积压数量
    private long totalWriteCount;   // 总写入次数
    private Long lastFlushTime;     // 上次刷新时间（时间戳）
}