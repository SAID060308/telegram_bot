
package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_channels")
@Getter
@Setter
@NoArgsConstructor
public class TelegramChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "channel_username")
    private String channelUsername;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false, unique = true)
    private User teacher;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate() {
        linkedAt = LocalDateTime.now();
    }
}
