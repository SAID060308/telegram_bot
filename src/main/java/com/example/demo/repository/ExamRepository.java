
package com.example.demo.repository;

import com.example.demo.entity.Exam;
import com.example.demo.entity.User;
import com.example.demo.entity.TelegramChannel;
import com.example.demo.enums.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByTeacher(User teacher);

    List<Exam> findByTeacherAndStatus(User teacher, ExamStatus status);

    List<Exam> findByChannel(TelegramChannel channel);

    Optional<Exam> findByIdAndTeacher(Long id, User teacher);

    boolean existsByTitleAndTeacher(String title, User teacher);

    List<Exam> findByTeacherOrderByCreatedAtDesc(User teacher);

}
