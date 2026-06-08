package com.gamelog.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GameLogCreateDTO {
    private String gameName;
    private String player;
    private String action;
    private String detail;
    private LocalDateTime playTime;
    private Integer duration;
}
