import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.SchedulerVerification;

class SchedulerVerificationOptionsTest {
    @Test
    void parseAcceptsCommonFlagStylesAndCreatesOutput(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("verification-output");

        assertDoesNotThrow(() -> SchedulerVerification.main(new String[] {
                "check",
                output.toString(),
                "--seed", "7",
                "--cases", "2",
                "--warmups", "1",
                "--repeats", "1",
                "--sizes", "100, 200"
        }));

        assertTrue(Files.exists(output.resolve("correctness-results.csv")));
    }

    @Test
    void parseRejectsMalformedNumericOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerVerification.main(new String[] {"check", "output", "--seed", "abc"}));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerVerification.main(new String[] {"check", "output", "--sizes=0,5"}));
    }
}
