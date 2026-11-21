package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.test.RecordingTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MailerConcurrencyTest {

    private static final int THREADS = 8;
    private static final int MAILS_PER_THREAD = 50;

    @Test
    void eightThreadsSendFourHundredMailsThroughOneMailer() throws Exception {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        CountDownLatch start = new CountDownLatch(1);
        List<String> expected = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                for (int i = 0; i < MAILS_PER_THREAD; i++) {
                    expected.add("mail-" + thread + "-" + i);
                }
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < MAILS_PER_THREAD; i++) {
                        mailer.send(Mail.using("main")
                                .to("reader" + thread + "@example.org")
                                .subject("mail-" + thread + "-" + i)
                                .text("Body")
                                .build());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(recording.attempts()).isEqualTo(400);
        assertThat(recording.sent()).hasSize(400);
        assertThat(recording.sent().stream().map(MailMessage::subject).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
