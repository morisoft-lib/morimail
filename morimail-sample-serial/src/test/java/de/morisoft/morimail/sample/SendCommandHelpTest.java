package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Checks that the send usage text describes the campaign directory and the inline rules (D-09, D-11). */
class SendCommandHelpTest {

    @Test
    void sendUsageNamesTheCampaignFilesAndTheInlineRules() {
        String usage = SerialMail.commandLine(SerialMail::realMailer, sleep -> { })
                .getSubcommands().get("send").getUsageMessage(CommandLine.Help.Ansi.OFF);

        assertThat(usage).contains("recipients.csv", "attachments/", "inline/", "cid:NAME");
        assertThat(usage).doesNotContain("%");
    }
}
