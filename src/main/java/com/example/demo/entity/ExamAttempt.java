
package com.example.demo.entity;

import com.example.demo.enums.AttemptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "student_id", nullable = false) private User student;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "exam_id", nullable = false) private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;


    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "draft_answers", columnDefinition = "TEXT")
    private String draftAnswers;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = AttemptStatus.IN_PROGRESS;
    }


}
