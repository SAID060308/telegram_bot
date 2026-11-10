package com.example.demo.bot;

import com.example.demo.entity.*;
import com.example.demo.enums.*;
import com.example.demo.service.*;
import com.example.demo.util.AnswerParser;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChannel;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExamTelegramBot implements LongPollingUpdateConsumer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final TelegramClient telegramClient;
    private final UserService userService;
    private final StateService stateService;
    private final ChannelService channelService;
    private final ExamService examService;
    private final AttemptService attemptService;

    private Long botId;
    private String botUsername;

    public ExamTelegramBot(TelegramClient telegramClient, UserService userService, StateService stateService,
                           ChannelService channelService, ExamService examService,
                           AttemptService attemptService) {
        this.telegramClient = telegramClient;
        this.userService = userService;
        this.stateService = stateService;
        this.channelService = channelService;
        this.examService = examService;
        this.attemptService = attemptService;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            try {
                if (update.hasCallbackQuery()) {
                    handleCallback(update.getCallbackQuery());
                } else if (update.hasMessage() && update.getMessage().hasDocument()) {
                    handleDocument(update.getMessage());
                } else if (update.hasMessage() && update.getMessage().hasText()) {
                    handleText(update.getMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // =====================================================================
    //  MATNLI XABARLAR
    // =====================================================================

    private void handleText(Message message) throws Exception {
        String text = message.getText();
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        if (text.startsWith("/start")) {
            handleStart(message, text);
            return;
        }

        Optional<User> userOpt = userService.find(telegramId);
        if (userOpt.isEmpty()) {
            sendRoleChoice(chatId);
            return;
        }
        User user = userOpt.get();

        BotState state = stateService.getState(telegramId);
        if (state != BotState.NONE && Keyboards.isMenuButton(text)) {
            stateService.clear(telegramId);
            state = BotState.NONE;
        }

        if (user.getRole() == Role.TEACHER) {
            if (state == BotState.WAITING_CHANNEL) { handleChannelInput(message, user); return; }
            if (state == BotState.WAITING_TEST_TITLE) { handleTestTitle(message); return; }
            if (state == BotState.WAITING_TEST_FILE) {
                send(chatId, "📎 Iltimos, test faylini PDF yoki DOCX ko'rinishida yuboring.", null);
                return;
            }
            if (state == BotState.WAITING_TEST_ANSWERS) { handleTestAnswers(message, user); return; }
            if (state == BotState.WAITING_EXAM_DURATION) { handleExamDuration(message, user); return; }

            switch (text) {
                case Keyboards.T_LINK_CHANNEL -> askChannel(chatId, telegramId, user);
                case Keyboards.T_CREATE_TEST -> askCreateTest(chatId, telegramId, user);
                case Keyboards.T_START_EXAM -> askStartExam(chatId, user);
                case Keyboards.T_MY_EXAMS -> showMyExams(chatId, user);
                default -> send(chatId, "Menyudan tanlang 👇", Keyboards.teacherMenu());
            }
        } else {
            if (Keyboards.S_MY_RESULTS.equals(text)) {
                showMyResults(chatId, user);
            } else if (Keyboards.isMenuButton(text)) {
                send(chatId, "Menyudan tanlang 👇", Keyboards.studentMenu());
            } else {
                handleStudentAnswers(message, user);
            }
        }
    }

    private void handleStart(Message message, String text) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        Long examId = null;
        String[] parts = text.split(" ", 2);
        if (parts.length == 2 && parts[1].startsWith("exam_")) {
            try {
                examId = Long.parseLong(parts[1].substring(5));
                stateService.put(telegramId, "pendingExamId", examId);
            } catch (NumberFormatException ignored) {
            }
        }

        Optional<User> userOpt = userService.find(telegramId);
        if (userOpt.isEmpty()) {
            sendRoleChoice(chatId);
            return;
        }
        User user = userOpt.get();

        if (examId != null) {
            stateService.clear(telegramId);
            if (user.getRole() == Role.STUDENT) {
                startExamForStudent(chatId, user, examId);
            } else {
                send(chatId, "ℹ️ Bu havola o'quvchilar uchun.", Keyboards.teacherMenu());
            }
            return;
        }
        sendMenu(chatId, user);
    }

    // =====================================================================
    //  CALLBACK TUGMALAR
    // =====================================================================

    private void handleCallback(CallbackQuery cb) throws Exception {
        String data = cb.getData();
        Long chatId = cb.getMessage().getChatId();
        Long telegramId = cb.getFrom().getId();

        telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(cb.getId()).build());

        if (data.startsWith("role:")) {
            Role role = Role.valueOf(data.substring(5));
            User user = userService.register(cb.getFrom(), role);
            send(chatId, "✅ Ro'yxatdan o'tdingiz: " + (user.getRole() == Role.TEACHER ? "Ustoz" : "O'quvchi"), null);
            sendMenu(chatId, user);

            Long pending = stateService.get(telegramId, "pendingExamId");
            if (pending != null && user.getRole() == Role.STUDENT) {
                stateService.clear(telegramId);
                startExamForStudent(chatId, user, pending);
            }
            return;
        }

        Optional<User> userOpt = userService.find(telegramId);
        if (userOpt.isEmpty()) {
            sendRoleChoice(chatId);
            return;
        }
        User user = userOpt.get();
        String[] p = data.split(":");

        try {
            switch (p[0]) {
                case "start" -> onPickExam(chatId, telegramId, user, Long.parseLong(p[1]));
                case "publish" -> onPublish(cb, chatId, user, Long.parseLong(p[1]), Integer.parseInt(p[2]));
                case "cancel" -> {
                    removeButtons(cb);
                    stateService.clear(telegramId);
                    send(chatId, "Bekor qilindi.", null);
                }
                case "close" -> onCloseExam(cb, chatId, user, Long.parseLong(p[1]));
                case "res" -> onExamResults(chatId, user, Long.parseLong(p[1]));
                case "confirm" -> onConfirm(cb, chatId, user, Long.parseLong(p[1]));
                case "edit" -> onEdit(cb, chatId, user, Long.parseLong(p[1]));
                case "detail" -> onDetail(chatId, user, Long.parseLong(p[1]));
                default -> { }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // noto'g'ri callback ma'lumoti, e'tibor bermaymiz
        }
    }

    // =====================================================================
    //  USTOZ: KANAL BIRIKTIRISH
    // =====================================================================

    private void askChannel(Long chatId, Long telegramId, User teacher) throws Exception {
        String current = channelService.findByTeacher(teacher)
                .map(c -> "\n\nHozirgi kanal: " + c.getChannelName())
                .orElse("");
        stateService.setState(telegramId, BotState.WAITING_CHANNEL);
        send(chatId, """
                📢 Kanal biriktirish

                1) Botni kanalingizga ADMIN qiling (post yozish huquqi bilan)
                2) Kanal @username ini yuboring (masalan: @mening_kanalim)
                   yoki kanaldan istalgan postni shu yerga forward qiling
                """ + current, null);
    }

    private void handleChannelInput(Message message, User teacher) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text = message.getText().trim();

        String target;
        if (message.getForwardOrigin() instanceof MessageOriginChannel origin) {
            target = String.valueOf(origin.getChat().getId());
        } else if (text.startsWith("@")) {
            target = text;
        } else {
            send(chatId, "❗ @username yuboring yoki kanaldan post forward qiling.", null);
            return;
        }

        ChatFullInfo channel;
        try {
            channel = telegramClient.execute(GetChat.builder().chatId(target).build());
        } catch (TelegramApiException e) {
            send(chatId, "❌ Kanal topilmadi. Bot kanalga qo'shilganini tekshiring.", null);
            return;
        }
        if (!"channel".equals(channel.getType())) {
            send(chatId, "❌ Bu kanal emas. Kanal yuboring.", null);
            return;
        }
        String channelId = channel.getId().toString();

        try {
            ChatMember botMember = telegramClient.execute(new GetChatMember(channelId, botId()));
            if (!(botMember instanceof ChatMemberAdministrator admin)
                    || !Boolean.TRUE.equals(admin.getCanPostMessages())) {
                send(chatId, "❌ Bot kanalda admin emas yoki post yozish huquqi yo'q.", null);
                return;
            }
        } catch (TelegramApiException e) {
            send(chatId, "❌ Botni kanalga admin qilib qo'shing va qayta urinib ko'ring.", null);
            return;
        }

        try {
            ChatMember teacherMember = telegramClient.execute(new GetChatMember(channelId, telegramId));
            String status = teacherMember.getStatus();
            if (!"creator".equals(status) && !"administrator".equals(status)) {
                send(chatId, "❌ Siz bu kanalning admini emassiz.", null);
                return;
            }
        } catch (TelegramApiException e) {
            send(chatId, "❌ Siz bu kanalda topilmadingiz.", null);
            return;
        }

        if (channelService.isTakenByAnother(channel.getId(), teacher)) {
            send(chatId, "❌ Bu kanal boshqa ustozga biriktirilgan.", null);
            return;
        }

        channelService.link(teacher, channel.getId(), channel.getTitle(), channel.getUserName());
        stateService.clear(telegramId);
        send(chatId, "✅ Kanal biriktirildi: " + channel.getTitle(), null);
        sendMenu(chatId, teacher);
    }

    // =====================================================================
    //  USTOZ: TEST YARATISH
    // =====================================================================

    private void askCreateTest(Long chatId, Long telegramId, User teacher) throws Exception {
        if (channelService.findByTeacher(teacher).isEmpty()) {
            send(chatId, "❗ Test yaratish uchun avval kanal biriktiring: «" + Keyboards.T_LINK_CHANNEL + "»", null);
            return;
        }
        stateService.clear(telegramId);
        stateService.setState(telegramId, BotState.WAITING_TEST_TITLE);
        send(chatId, "📝 Test nomini yuboring:", null);
    }

    private void handleTestTitle(Message message) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String title = message.getText().trim();

        if (title.length() < 2 || title.length() > 100) {
            send(chatId, "❗ Test nomi 2 dan 100 gacha belgi bo'lsin.", null);
            return;
        }
        stateService.put(telegramId, "title", title);
        stateService.setState(telegramId, BotState.WAITING_TEST_FILE);
        send(chatId, "📎 Endi test faylini yuboring (PDF yoki DOCX):", null);
    }

    private void handleDocument(Message message) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        if (stateService.getState(telegramId) != BotState.WAITING_TEST_FILE) {
            return;
        }
        var doc = message.getDocument();
        String name = doc.getFileName() == null ? "test" : doc.getFileName();
        String lower = name.toLowerCase();

        FileType type;
        if (lower.endsWith(".pdf")) {
            type = FileType.PDF;
        } else if (lower.endsWith(".docx")) {
            type = FileType.DOCX;
        } else {
            send(chatId, "❗ Faqat PDF yoki DOCX fayl qabul qilinadi.", null);
            return;
        }

        stateService.put(telegramId, "fileId", doc.getFileId());
        stateService.put(telegramId, "fileName", name);
        stateService.put(telegramId, "fileType", type);
        stateService.setState(telegramId, BotState.WAITING_TEST_ANSWERS);

        send(chatId, """
                ✅ Fayl qabul qilindi.

                Endi to'g'ri javoblarni yuboring, har qatorda bittadan:

                1-a
                2-b
                3-c
                20--5
                21-1/4

                • Harf: a, b, c ...
                • Manfiy son: 20--5 (savol 20, javob -5)
                • Kasr faqat 1/4, 3/10 ko'rinishida (0.25 emas)
                """, null);
    }

    private void handleTestAnswers(Message message, User teacher) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        Map<Integer, String> key;
        try {
            key = AnswerParser.parse(message.getText());
        } catch (AnswerParser.AnswerFormatException e) {
            send(chatId, "❌ " + e.getMessage() + "\n\nQaytadan yuboring.", null);
            return;
        }

        TelegramChannel channel = channelService.findByTeacher(teacher).orElse(null);
        if (channel == null) {
            stateService.clear(telegramId);
            send(chatId, "❗ Kanal topilmadi. Avval kanal biriktiring.", null);
            return;
        }

        String title = stateService.get(telegramId, "title");
        String fileId = stateService.get(telegramId, "fileId");
        String fileName = stateService.get(telegramId, "fileName");
        FileType fileType = stateService.get(telegramId, "fileType");

        examService.createDraft(teacher, channel, title, fileId, fileName, fileType, AnswerParser.serialize(key));
        stateService.clear(telegramId);

        String preview = AnswerParser.serialize(key).replace("\n", ", ");
        if (preview.length() > 300) preview = preview.substring(0, 300) + "...";

        send(chatId, "✅ Test saqlandi!\n\n📝 " + title
                + "\n❓ Savollar soni: " + key.size()
                + "\n🔑 " + preview
                + "\n\nImtihonni e'lon qilish uchun «" + Keyboards.T_START_EXAM + "» ni bosing.", null);
        sendMenu(chatId, teacher);
    }

    // =====================================================================
    //  USTOZ: IMTIHONNI BOSHLASH / E'LON QILISH
    // =====================================================================

    private void askStartExam(Long chatId, User teacher) throws Exception {
        List<Exam> drafts = examService.findByTeacher(teacher).stream()
                .filter(e -> e.getStatus() == ExamStatus.DRAFT)
                .limit(20)
                .toList();
        if (drafts.isEmpty()) {
            send(chatId, "Boshlash uchun test yo'q. Avval «" + Keyboards.T_CREATE_TEST + "» orqali test yarating.", null);
            return;
        }
        send(chatId, "▶️ Qaysi testni boshlaymiz?", Keyboards.examPicker(drafts));
    }

    private void onPickExam(Long chatId, Long telegramId, User user, Long examId) throws Exception {
        Exam exam = ownedExam(user, examId);
        if (exam == null) {
            send(chatId, "❌ Imtihon topilmadi.", null);
            return;
        }
        if (exam.getStatus() != ExamStatus.DRAFT) {
            send(chatId, "ℹ️ Bu imtihon allaqachon e'lon qilingan.", null);
            return;
        }
        stateService.clear(telegramId);
        stateService.put(telegramId, "examId", examId);
        stateService.setState(telegramId, BotState.WAITING_EXAM_DURATION);
        send(chatId, "⏱ «" + exam.getTitle() + "» necha daqiqa davom etadi?\nRaqam yuboring (masalan: 60)", null);
    }

    private void handleExamDuration(Message message, User teacher) throws Exception {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        int minutes;
        try {
            minutes = Integer.parseInt(message.getText().trim());
        } catch (NumberFormatException e) {
            send(chatId, "❗ Faqat raqam yuboring (masalan: 60).", null);
            return;
        }
        if (minutes < 1 || minutes > 600) {
            send(chatId, "❗ Vaqt 1 dan 600 daqiqagacha bo'lsin.", null);
            return;
        }

        Long examId = stateService.get(telegramId, "examId");
        Exam exam = examId == null ? null : ownedExam(teacher, examId);
        stateService.clear(telegramId);
        if (exam == null) {
            send(chatId, "❌ Imtihon topilmadi. Qaytadan boshlang.", null);
            return;
        }

        int total = AnswerParser.parse(exam.getAnswerKey()).size();
        send(chatId, "📝 " + exam.getTitle()
                + "\n⏱ Vaqt: " + minutes + " daqiqa"
                + "\n❓ Savollar: " + total
                + "\n📢 Kanal: " + exam.getChannel().getChannelName()
                + "\n\nKanalga e'lon qilamizmi?", Keyboards.publishConfirm(exam.getId(), minutes));
    }

    private void onPublish(CallbackQuery cb, Long chatId, User user, Long examId, int minutes) throws Exception {
        removeButtons(cb);
        Exam template = ownedExam(user, examId);
        if (template == null || template.getStatus() != ExamStatus.DRAFT) {
            send(chatId, "❌ Test topilmadi.", null);
            return;
        }

        Exam run = examService.createRun(template, minutes);

        int total = AnswerParser.parse(run.getAnswerKey()).size();
        String url = "https://t.me/" + botUsername() + "?start=exam_" + run.getId();
        String text = "📝 " + run.getTitle()
                + "\n\n⏱ Vaqt: " + minutes + " daqiqa"
                + "\n❓ Savollar soni: " + total
                + "\n\nBoshlash uchun pastdagi tugmani bosing 👇";

        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(run.getChannel().getChatId().toString())
                    .text(text)
                    .replyMarkup(Keyboards.channelStartButton(url))
                    .build());
        } catch (TelegramApiException e) {
            examService.delete(run.getId());   // kanalga bormadi, nusxani qoldirmaymiz
            send(chatId, "❌ Kanalga yuborib bo'lmadi. Bot kanalda admin ekanini tekshiring.", null);
            return;
        }

        send(chatId, "✅ Imtihon e'lon qilindi: " + run.getChannel().getChannelName()
                + "\nNatijalarni «" + Keyboards.T_MY_EXAMS + "» bo'limidan ko'rasiz.", null);
    }

    private void onCloseExam(CallbackQuery cb, Long chatId, User user, Long examId) throws Exception {
        Exam exam = ownedExam(user, examId);
        if (exam == null) {
            send(chatId, "❌ Imtihon topilmadi.", null);
            return;
        }
        examService.close(examId);
        removeButtons(cb);
        send(chatId, "⏹ «" + exam.getTitle() + "» yopildi.\nYangi ishtirokchilar qo'shila olmaydi, "
                + "boshlaganlar o'z vaqti tugaguncha davom etadi.", null);
    }

    private void showMyExams(Long chatId, User teacher) throws Exception {
        List<Exam> exams = examService.findByTeacher(teacher).stream().limit(10).toList();
        if (exams.isEmpty()) {
            send(chatId, "Hozircha imtihonlar yo'q.", null);
            return;
        }
        for (Exam e : exams) {
            String status = switch (e.getStatus()) {
                case DRAFT -> "📄 Qoralama";
                case PUBLISHED -> "🟢 E'lon qilingan";
                case CLOSED -> "⏹ Yopilgan";
            };
            int total = AnswerParser.parse(e.getAnswerKey()).size();
            String duration = e.getDurationMinutes() == null ? "" : " · ⏱ " + e.getDurationMinutes() + " daq";
            send(chatId, "📝 " + e.getTitle() + "\n" + status + "\n❓ " + total + " ta savol" + duration,
                    Keyboards.examActions(e));
        }
    }

    private void onExamResults(Long chatId, User user, Long examId) throws Exception {
        Exam exam = ownedExam(user, examId);
        if (exam == null) {
            send(chatId, "❌ Imtihon topilmadi.", null);
            return;
        }
        List<ExamResult> results = attemptService.resultsByExam(exam);
        if (results.isEmpty()) {
            send(chatId, "📊 «" + exam.getTitle() + "»: hozircha natija yo'q.", null);
            return;
        }
        StringBuilder sb = new StringBuilder("📊 «" + exam.getTitle() + "» natijalari ("
                + results.size() + " ishtirokchi)\n\n");
        int i = 1;
        for (ExamResult r : results) {
            if (i > 40) { sb.append("..."); break; }
            sb.append(i++).append(". ").append(fullName(r.getAttempt().getStudent()))
                    .append(" — ").append(r.getCorrectAnswers()).append("/").append(r.getTotalQuestions())
                    .append(" (").append(String.format("%.1f", r.getScorePercentage())).append("%)\n");
        }
        send(chatId, sb.toString(), null);
    }

    // =====================================================================
    //  O'QUVCHI: IMTIHONNI BOSHLASH, JAVOB YUBORISH, TASDIQLASH
    // =====================================================================

    private void startExamForStudent(Long chatId, User student, Long examId) throws Exception {
        Exam exam = examService.findById(examId).orElse(null);
        if (exam == null || exam.getStatus() != ExamStatus.PUBLISHED) {
            send(chatId, "❌ Imtihon topilmadi yoki yopilgan.", Keyboards.studentMenu());
            return;
        }

        Optional<ExamAttempt> existing = attemptService.findByStudentAndExam(student, exam);
        if (existing.isPresent()) {
            ExamAttempt a = existing.get();
            if (AttemptService.isActive(a)) {
                if (LocalDateTime.now().isBefore(a.getDeadline())) {
                    send(chatId, "ℹ️ Siz bu imtihonni allaqachon boshlagansiz.\n⏰ Tugash vaqti: "
                            + a.getDeadline().format(TIME) + "\nJavoblarni yuboring.", null);
                    sendTestFile(chatId, exam, a);
                    return;
                }
                ExamResult r = attemptService.finish(a.getId());
                send(chatId, "⏰ Bu imtihon vaqti tugagan.", null);
                sendResult(chatId, r);
                return;
            }
            send(chatId, "ℹ️ Siz bu imtihonda allaqachon qatnashgansiz.", null);
            attemptService.findResult(a).ifPresent(r -> {
                try { sendResult(chatId, r); } catch (Exception ignored) { }
            });
            return;
        }

        Optional<ExamAttempt> active = attemptService.findActive(student);
        if (active.isPresent()) {
            ExamAttempt other = active.get();
            if (LocalDateTime.now().isBefore(other.getDeadline())) {
                send(chatId, "❗ Avval «" + other.getExam().getTitle() + "» imtihonini yakunlang.", null);
                return;
            }
            notifyTimeUp(student.getTelegramId(), attemptService.finish(other.getId()));
        }

        ExamAttempt attempt = attemptService.start(student, exam);
        sendTestFile(chatId, exam, attempt);
    }

    private void sendTestFile(Long chatId, Exam exam, ExamAttempt attempt) throws Exception {
        String caption = "📝 " + exam.getTitle()
                + "\n\n▶️ Boshlandi: " + attempt.getStartedAt().format(TIME)
                + "\n⏰ Tugash vaqti: " + attempt.getDeadline().format(TIME)
                + "\n\nJavoblarni BITTA xabarda, har qatorda bittadan yuboring:\n"
                + "1-a\n2-b\n20--5\n21-1/4"
                + "\n\nYuborgach «Tasdiqlash» yoki «O'zgartirish» tugmasi chiqadi.";
        telegramClient.execute(SendDocument.builder()
                .chatId(chatId.toString())
                .document(new InputFile(exam.getFileId()))
                .caption(caption)
                .build());
    }

    private void handleStudentAnswers(Message message, User student) throws Exception {
        Long chatId = message.getChatId();

        Optional<ExamAttempt> opt = attemptService.findActive(student);
        if (opt.isEmpty()) {
            send(chatId, "Menyudan tanlang 👇", Keyboards.studentMenu());
            return;
        }
        ExamAttempt attempt = opt.get();

        if (!LocalDateTime.now().isBefore(attempt.getDeadline())) {
            ExamResult r = attemptService.finish(attempt.getId());
            notifyTimeUp(student.getTelegramId(), r);
            return;
        }

        Map<Integer, String> given;
        try {
            given = AnswerParser.parseStudent(message.getText());
        } catch (AnswerParser.AnswerFormatException e) {
            send(chatId, "❌ " + e.getMessage() + "\n\nQaytadan yuboring.", null);
            return;
        }

        int total = AnswerParser.parse(attempt.getExam().getAnswerKey()).size();
        if (given.keySet().stream().anyMatch(n -> n < 1 || n > total)) {
            send(chatId, "❌ Bu imtihonda " + total + " ta savol bor. Savol raqamlarini tekshiring.", null);
            return;
        }

        attemptService.saveDraft(attempt.getId(), AnswerParser.serialize(given));

        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= total; i++) if (!given.containsKey(i)) missing.add(i);

        StringBuilder sb = new StringBuilder("📋 Javoblaringiz (" + given.size() + "/" + total + "):\n\n");
        given.forEach((n, a) -> sb.append(n).append("-").append(a).append("\n"));
        if (!missing.isEmpty()) {
            sb.append("\n⚠️ Javob berilmagan: ")
                    .append(missing.stream().map(String::valueOf).collect(Collectors.joining(", "))).append("\n");
        }
        sb.append("\n⏰ Tugash vaqti: ").append(attempt.getDeadline().format(TIME))
                .append("\nTasdiqlangach o'zgartirib bo'lmaydi.");
        send(chatId, sb.toString(), Keyboards.attemptConfirm(attempt.getId()));
    }

    private void onConfirm(CallbackQuery cb, Long chatId, User user, Long attemptId) throws Exception {
        ExamAttempt attempt = ownedAttempt(user, attemptId);
        if (attempt == null) return;
        removeButtons(cb);

        if (!AttemptService.isActive(attempt)) {
            send(chatId, "ℹ️ Bu imtihon allaqachon yakunlangan.", null);
            return;
        }
        if (attempt.getDraftAnswers() == null || attempt.getDraftAnswers().isBlank()) {
            send(chatId, "❗ Avval javoblarni yuboring.", null);
            return;
        }
        sendResult(chatId, attemptService.finish(attemptId));
    }

    private void onEdit(CallbackQuery cb, Long chatId, User user, Long attemptId) throws Exception {
        ExamAttempt attempt = ownedAttempt(user, attemptId);
        if (attempt == null) return;
        removeButtons(cb);

        if (!AttemptService.isActive(attempt)) {
            send(chatId, "ℹ️ Bu imtihon allaqachon yakunlangan.", null);
            return;
        }
        if (!LocalDateTime.now().isBefore(attempt.getDeadline())) {
            notifyTimeUp(user.getTelegramId(), attemptService.finish(attemptId));
            return;
        }
        attemptService.reopen(attemptId);
        send(chatId, "✏️ Javoblarni to'liq qaytadan yuboring (oldingisi almashtiriladi).\n⏰ Tugash vaqti: "
                + attempt.getDeadline().format(TIME), null);
    }

    // =====================================================================
    //  NATIJALAR
    // =====================================================================

    private void showMyResults(Long chatId, User student) throws Exception {
        List<ExamResult> results = attemptService.resultsByStudent(student).stream().limit(10).toList();
        if (results.isEmpty()) {
            send(chatId, "📊 Hozircha natijalar yo'q.", null);
            return;
        }
        StringBuilder sb = new StringBuilder("📊 Oxirgi natijalaringiz:\n\n");
        int i = 1;
        for (ExamResult r : results) {
            sb.append(i++).append(". ").append(r.getAttempt().getExam().getTitle())
                    .append(" — ").append(r.getCorrectAnswers()).append("/").append(r.getTotalQuestions())
                    .append(" (").append(String.format("%.1f", r.getScorePercentage())).append("%) · ")
                    .append(r.getCompletedAt().format(DATE_TIME)).append("\n");
        }
        sb.append("\nBatafsil ko'rish uchun tanlang 👇");
        send(chatId, sb.toString(), Keyboards.resultButtons(results));
    }

    private void onDetail(Long chatId, User user, Long attemptId) throws Exception {
        ExamResult r = attemptService.findResultByAttemptId(attemptId).orElse(null);
        if (r == null || !r.getAttempt().getStudent().getId().equals(user.getId())) {
            send(chatId, "❌ Natija topilmadi.", null);
            return;
        }
        sendResult(chatId, r);
    }

    private void sendResult(Long chatId, ExamResult r) throws Exception {
        List<StudentAnswer> answers = attemptService.answers(r.getAttempt());

        List<String> wrongList = answers.stream()
                .filter(a -> !a.isCorrect() && !AttemptService.NO_ANSWER.equals(a.getStudentAnswer()))
                .map(a -> String.valueOf(a.getQuestionNumber()))
                .toList();
        List<String> blankList = answers.stream()
                .filter(a -> AttemptService.NO_ANSWER.equals(a.getStudentAnswer()))
                .map(a -> String.valueOf(a.getQuestionNumber()))
                .toList();

        StringBuilder sb = new StringBuilder("🏁 Imtihon yakunlandi!\n\n📝 ")
                .append(r.getAttempt().getExam().getTitle())
                .append("\n\n✔️ To'g'ri: ").append(r.getCorrectAnswers()).append("/").append(r.getTotalQuestions())
                .append("\n❌ Noto'g'ri: ").append(wrongList.size())
                .append("\n⚪️ Javobsiz: ").append(blankList.size())
                .append("\n📊 Natija: ").append(String.format("%.1f", r.getScorePercentage())).append("%");
        if (!wrongList.isEmpty()) sb.append("\n\n❌ Noto'g'ri savollar: ").append(String.join(", ", wrongList));
        if (!blankList.isEmpty()) sb.append("\n⚪️ Javob berilmagan: ").append(String.join(", ", blankList));
        send(chatId, sb.toString(), null);
    }

    /** Scheduler ham, bot ham shu metoddan foydalanadi. */
    public void notifyTimeUp(Long telegramId, ExamResult r) {
        try {
            send(telegramId, "⏰ Vaqt tugadi!", null);
            sendResult(telegramId, r);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    //  YORDAMCHI METODLAR
    // =====================================================================

    private Exam ownedExam(User teacher, Long examId) {
        if (teacher.getRole() != Role.TEACHER) return null;
        return examService.findById(examId)
                .filter(e -> e.getTeacher().getId().equals(teacher.getId()))
                .orElse(null);
    }

    private ExamAttempt ownedAttempt(User student, Long attemptId) {
        return attemptService.findById(attemptId)
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .orElse(null);
    }

    private String fullName(User u) {
        return u.getFirstName() + (u.getLastName() == null ? "" : " " + u.getLastName());
    }

    private Long botId() throws Exception {
        if (botId == null) botId = telegramClient.execute(new GetMe()).getId();
        return botId;
    }

    private String botUsername() throws Exception {
        if (botUsername == null) botUsername = telegramClient.execute(new GetMe()).getUserName();
        return botUsername;
    }

    private void removeButtons(CallbackQuery cb) {
        try {
            telegramClient.execute(EditMessageReplyMarkup.builder()
                    .chatId(cb.getMessage().getChatId().toString())
                    .messageId(cb.getMessage().getMessageId())
                    .build());
        } catch (Exception ignored) {
        }
    }

    private void sendRoleChoice(Long chatId) throws Exception {
        send(chatId, """
                Assalomu alaykum! 👋

                Botimizga xush kelibsiz!
                Iltimos, kim ekanligingizni tanlang:""", Keyboards.roleChoice());
    }

    private void sendMenu(Long chatId, User user) throws Exception {
        if (user.getRole() == Role.TEACHER) {
            send(chatId, "👨‍🏫 Ustoz paneli", Keyboards.teacherMenu());
        } else {
            send(chatId, "🎓 O'quvchi paneli", Keyboards.studentMenu());
        }
    }

    private void send(Long chatId, String text, ReplyKeyboard keyboard) throws Exception {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text);
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        telegramClient.execute(builder.build());
    }
}