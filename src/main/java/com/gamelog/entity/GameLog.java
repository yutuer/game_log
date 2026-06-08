package com.gamelog.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "game_log", indexes = {
    @Index(name = "idx_game_name", columnList = "gameName"),
    @Index(name = "idx_player", columnList = "player"),
    @Index(name = "idx_play_time", columnList = "playTime"),
    @Index(name = "idx_game_name_play_time", columnList = "gameName, playTime")
})
public class GameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
