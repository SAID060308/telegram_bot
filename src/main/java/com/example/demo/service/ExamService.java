package com.example.demo.service;

import com.example.demo.entity.Exam;
import com.example.demo.entity.TelegramChannel;
import com.example.demo.entity.User;
import com.example.demo.enums.ExamStatus;
import com.example.demo.enums.FileType;
import com.example.demo.repository.ExamRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExamService {

    private final ExamRepository repo;

    public ExamService(ExamRepository repo) { this.repo = repo; }

    public Exam createDraft(User teacher, TelegramChannel channel, String title,
                            String fileId, String fileName, FileType fileType, String answerKey) {
        Exam exam = new Exam();
        exam.setTeacher(teacher);
        exam.setChannel(channel);
        exam.setTitle(title);
        exam.setFileId(fileId);
        exam.setFileName(fileName);
        exam.setFileType(fileType);
        exam.setAnswerKey(answerKey);
        exam.setStatus(ExamStatus.DRAFT);
        return repo.save(exam);
    }

    public List<Exam> findByTeacher(User teacher) { return repo.findByTeacherOrderByCreatedAtDesc(teacher); }

    public Optional<Exam> findById(Long id) { return repo.findById(id); }

    @Transactional
    public Exam publish(Long examId, int minutes) {
        Exam e = repo.findById(examId).orElseThrow();
        e.setDurationMinutes(minutes);
        e.setStatus(ExamStatus.PUBLISHED);
        e.setPublishedAt(LocalDateTime.now());
        return e;
    }

    @Transactional
    public void close(Long examId) {
        repo.findById(examId).ifPresent(e -> e.setStatus(ExamStatus.CLOSED));
    }

    /** Testning nusxasini e'lon qilingan imtihon sifatida yaratadi. Asl test DRAFT bo'lib qoladi. */
    public Exam createRun(Exam template, int minutes) {
        Exam run = new Exam();
        run.setTeacher(template.getTeacher());
        run.setChannel(template.getChannel());
        run.setTitle(template.getTitle());
        run.setDescription(template.getDescription());
        run.setFileId(template.getFileId());
        run.setFileName(template.getFileName());
        run.setFileType(template.getFileType());
        run.setAnswerKey(template.getAnswerKey());
        run.setDurationMinutes(minutes);
        run.setStatus(ExamStatus.PUBLISHED);
        run.setPublishedAt(LocalDateTime.now());
        return repo.save(run);
    }

    /** Kanalga yuborib bo'lmasa, yaratilgan nusxani qaytarib o'chirish uchun. */
    public void delete(Long examId) {
        repo.deleteById(examId);
    }

}
