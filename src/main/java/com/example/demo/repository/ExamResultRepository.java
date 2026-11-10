
package com.example.demo.repository;

import com.example.demo.entity.Exam;
import com.example.demo.entity.ExamResult;
import com.example.demo.entity.ExamAttempt;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {


    List<ExamResult> findByAttempt_Exam_Id(Long examId);

    boolean existsByAttempt(ExamAttempt attempt);

    Optional<ExamResult> findByAttempt(ExamAttempt attempt);
    Optional<ExamResult> findByAttemptId(Long attemptId);
    List<ExamResult> findByAttemptStudentOrderByCompletedAtDesc(User student);
    List<ExamResult> findByAttemptExamOrderByCorrectAnswersDescCompletedAtAsc(Exam exam);


}
