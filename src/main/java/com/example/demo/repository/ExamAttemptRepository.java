
package com.example.demo.repository;

import com.example.demo.entity.ExamAttempt;
import com.example.demo.entity.Exam;
import com.example.demo.entity.User;
import com.example.demo.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    List<ExamAttempt> findByStudent(User student);

    List<ExamAttempt> findByExam(Exam exam);


    List<ExamAttempt> findByStudentAndStatus(User student, AttemptStatus status);

    boolean existsByStudentAndExam(User student, Exam exam);

    long countByExamAndStatus(Exam exam, AttemptStatus status);

    Optional<ExamAttempt> findByStudentAndExam(User student, Exam exam);
    Optional<ExamAttempt> findFirstByStudentAndStatusInOrderByStartedAtDesc(User student, Collection<AttemptStatus> statuses);
    List<ExamAttempt> findByStatusInAndDeadlineBefore(Collection<AttemptStatus> statuses, LocalDateTime time);


}
