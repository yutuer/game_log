package com.gamelog.dto;

import lombok.Data;
import java.util.List;

@Data
public class GameLogBatchCreateDTO {
    private List<GameLogCreateDTO> logs;
}
