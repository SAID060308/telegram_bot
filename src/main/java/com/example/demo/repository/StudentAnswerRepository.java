
package com.example.demo.repository;

import com.example.demo.entity.StudentAnswer;
import com.example.demo.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    List<StudentAnswer> findByAttempt(ExamAttempt attempt);

    Optional<StudentAnswer> findByAttemptAndQuestionNumber(
            ExamAttempt attempt,
            Integer questionNumber
    );

    boolean existsByAttemptAndQuestionNumber(
            ExamAttempt attempt,
            Integer questionNumber
    );

    long countByAttempt(ExamAttempt attempt);

    long countByAttemptAndCorrectTrue(ExamAttempt attempt);

    long countByAttemptAndCorrectFalse(ExamAttempt attempt);

    List<StudentAnswer> findByAttemptOrderByQuestionNumber(ExamAttempt attempt);


}
