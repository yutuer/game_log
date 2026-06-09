package com.gamelog.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "game_log", uniqueConstraints = @UniqueConstraint(
    name = "uk_game_player_action_time",
    columnNames = {"gameName", "player", "action", "playTime"}
), indexes = {
    @Index(name = "idx_game_name", columnList = "gameName"),
    @Index(name = "idx_player", columnList = "player"),
    @Index(name = "idx_play_time", columnList = "playTime"),
    @Index(name = "idx_game_name_play_time", columnList = "gameName, playTime"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_action", columnList = "action")
})
public class GameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "game_log_id_gen")
    @TableGenerator(
        name = "game_log_id_gen",
        table = "id_sequence",
        pkColumnName = "gen_name",
        valueColumnName = "gen_value",
        pkColumnValue = "game_log_seq",
        allocationSize = 50
    )
    private Long id;

    @Column(nullable = false, length = 100)
    private String gameName;

    @Column(nullable = false, length = 100)
    private String player;

    @Column(nullable = false, length = 200)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime playTime;

    @Column(nullable = true)
    private Integer duration;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
