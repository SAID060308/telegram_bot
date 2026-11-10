package com.example.demo.bot;

import com.example.demo.entity.Exam;
import com.example.demo.entity.ExamResult;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public final class Keyboards {

    private Keyboards() {}

    // Menyu tugmalari matni (handler'larda ham shu konstantalar ishlatiladi)
    public static final String T_CREATE_TEST = "📝 Test yaratish";
    public static final String T_START_EXAM = "▶️ Imtihonni boshlash";
    public static final String T_LINK_CHANNEL = "📢 Kanal biriktirish";
    public static final String T_MY_EXAMS = "📋 Imtihonlarim";
    public static final String S_MY_RESULTS = "📊 Natijalarim";

    public static InlineKeyboardMarkup roleChoice() {
        InlineKeyboardButton teacher = InlineKeyboardButton.builder()
                .text("👨‍🏫 Ustoz").callbackData("role:TEACHER").build();
        InlineKeyboardButton student = InlineKeyboardButton.builder()
                .text("🎓 O'quvchi").callbackData("role:STUDENT").build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(teacher, student))
                .build();
    }

    public static ReplyKeyboardMarkup teacherMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(
                        new KeyboardRow(T_CREATE_TEST, T_START_EXAM),
                        new KeyboardRow(T_MY_EXAMS, T_LINK_CHANNEL)))
                .resizeKeyboard(true)
                .build();
    }

    public static ReplyKeyboardMarkup studentMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(new KeyboardRow(S_MY_RESULTS)))
                .resizeKeyboard(true)
                .build();
    }

    public static boolean isMenuButton(String text) {
        return T_CREATE_TEST.equals(text) || T_START_EXAM.equals(text)
                || T_LINK_CHANNEL.equals(text) || T_MY_EXAMS.equals(text)
                || S_MY_RESULTS.equals(text);
    }

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static String cut(String s, int max) { return s.length() <= max ? s : s.substring(0, max) + "…"; }

    public static InlineKeyboardMarkup examPicker(List<Exam> exams) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Exam e : exams) rows.add(new InlineKeyboardRow(btn("📝 " + cut(e.getTitle(), 40), "start:" + e.getId())));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup examActions(Exam e) {
        InlineKeyboardRow row = switch (e.getStatus()) {
            case DRAFT -> new InlineKeyboardRow(btn("▶️ Boshlash", "start:" + e.getId()));
            case PUBLISHED -> new InlineKeyboardRow(btn("📊 Natijalar", "res:" + e.getId()),
                    btn("⏹ Yopish", "close:" + e.getId()));
            case CLOSED -> new InlineKeyboardRow(btn("📊 Natijalar", "res:" + e.getId()));
        };
        return InlineKeyboardMarkup.builder().keyboardRow(row).build();
    }

    public static InlineKeyboardMarkup publishConfirm(Long examId, int minutes) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        btn("📢 E'lon qilish", "publish:" + examId + ":" + minutes),
                        btn("❌ Bekor", "cancel:x")))
                .build();
    }

    public static InlineKeyboardMarkup channelStartButton(String url) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("▶️ Testni boshlash").url(url).build()))
                .build();
    }

    public static InlineKeyboardMarkup attemptConfirm(Long attemptId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        btn("✅ Tasdiqlash", "confirm:" + attemptId),
                        btn("✏️ O'zgartirish", "edit:" + attemptId)))
                .build();
    }

    public static InlineKeyboardMarkup resultButtons(List<ExamResult> results) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ExamResult r : results) {
            rows.add(new InlineKeyboardRow(btn("🔍 " + cut(r.getAttempt().getExam().getTitle(), 35),
                    "detail:" + r.getAttempt().getId())));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

}