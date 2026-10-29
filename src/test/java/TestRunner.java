import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import main.Main;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Runs the public Energex-Lite scenarios. */
@ExtendWith(TestCaseWatcher.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class TestRunner {
    private static final ObjectMapper MAPPER = new ObjectMapper(
            new JsonFactory().enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
    );
    private static final List<DevmindResult> DEVMIND_RESULTS = new ArrayList<>();
    private static final String PASSED = "PASSED";
    private static final int TOTAL_TESTS = 18;

    private static int testsScore;
    private static int checkstyleScore;
    private static int readmeScore;
    private static int gitScore;
    private static int checkstyleErrorCount = -1;
    private static final int[] TEST_POINTS = {
            5, 4, 3, 3, 3, 3, 3, 3, 5, 5, 5, 6, 6, 6,
            5, 5, 5, 5
    };
    /** Supplies the public input, output and reference paths. */
    public static Stream<Arguments> data() {
        return IntStream.rangeClosed(1, TOTAL_TESTS)
                .mapToObj(TestRunner::testArguments);
    }

    private static Arguments testArguments(final int index) {
        String testNumber = "%02d".formatted(index);
        return Arguments.of(
                "test" + testNumber,
                "input/test_" + testNumber + "_input.json",
                "output/test_" + testNumber + "_output.json",
                "ref/test_" + testNumber + "_reference.json",
                TEST_POINTS[index - 1]
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void run(final String testName, final String inputPath,
                    final String outputPath, final String refPath,
                    final int points) throws IOException {
        try {
            Main.action(inputPath, outputPath);
            JsonNode outputJson = MAPPER.readTree(new File(outputPath));
            JsonNode referenceJson = MAPPER.readTree(new File(refPath));

            assertThatJson(outputJson).isEqualTo(referenceJson);
            DEVMIND_RESULTS.add(new DevmindResult(testName, PASSED, points));
            testsScore += points;
        } catch (IOException | RuntimeException | AssertionError error) {
            DEVMIND_RESULTS.add(new DevmindErrorResult(
                    testName, points, error.getMessage()));
            throw error;
        }
    }

    @Test
    public void testCheckstyle() throws CheckstyleException, IOException {
        File configFile = new File("src/test/resources/checkstyle/checkstyle.xml");
        Configuration configuration = ConfigurationLoader.loadConfiguration(
                new InputSource(configFile.getAbsolutePath()),
                new PropertiesExpander(System.getProperties()),
                ConfigurationLoader.IgnoredModulesOptions.EXECUTE
        );

        CheckstyleAuditListener listener = new CheckstyleAuditListener();
        Checker checker = new Checker();
        checker.setModuleClassLoader(CheckerConstants.class.getClassLoader());
        checker.addListener(listener);
        checker.configure(configuration);

        List<File> files = SourceFileCollector.getJavaSourceFiles("src/main/java");
        int errorCount = checker.process(files);
        checkstyleErrorCount = errorCount;
        checker.destroy();

        String checkstyleErrors = listener.toString();
        try {
            assertThat(errorCount)
                    .isLessThanOrEqualTo(CheckerConstants.MAXIMUM_ERROR_CHECKSTYLE);
            DEVMIND_RESULTS.add(new DevmindResult(
                    "checkstyle", PASSED, CheckerConstants.CHECKSTYLE_POINTS));
            System.out.println(checkstyleErrors);
            TestCaseWatcher.totalPoints += CheckerConstants.CHECKSTYLE_POINTS;
            checkstyleScore = CheckerConstants.CHECKSTYLE_POINTS;
        } catch (AssertionError error) {
            DEVMIND_RESULTS.add(new DevmindErrorResult(
                    "checkstyle", CheckerConstants.CHECKSTYLE_POINTS,
                    error.getMessage()));
            throw new CheckstyleException(checkstyleErrors);
        }
    }
    @Test
    public void testReadme() {
        boolean exists = Files.exists(Paths.get("README"))
                || Files.exists(Paths.get("README.md"))
                || Files.exists(Paths.get("README.txt"));

        try {
            assertThat(exists)
                    .as("README, README.md or README.txt must exist")
                    .isTrue();

            DEVMIND_RESULTS.add(new DevmindResult(
                    "README", PASSED, CheckerConstants.README_POINTS));
            TestCaseWatcher.totalPoints += CheckerConstants.README_POINTS;
            readmeScore = CheckerConstants.README_POINTS;
        } catch (AssertionError error) {
            DEVMIND_RESULTS.add(new DevmindErrorResult(
                    "README", CheckerConstants.README_POINTS,
                    error.getMessage()));
            throw error;
        }
    }
    private boolean hasNonDeveloperAuthor(final RevCommit commit) {
        List<String> exceptedAuthors = List.of(
                "anacristina.stanciulescu@gmail.com",
                "andrei.marcu@sap.com",
                "bogdan.svai2004@gmail.com",
                "ionutgabrielmantu517@gmail.com",
                "tiberiu.gutanu@stud.acs.upb.ro"
        );
        return !exceptedAuthors.contains(commit.getAuthorIdent().getEmailAddress());
    }

    @Test
    public void testGitCommits() throws GitAPIException, IOException {
        File repositoryRoot = findRepositoryRoot();
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder()
                .setGitDir(new File(repositoryRoot, ".git"))
                .setWorkTree(repositoryRoot);

        try (Repository repository = repositoryBuilder.build();
             Git git = new Git(repository)) {
            List<RevCommit> commits = StreamSupport.stream(
                            git.log().call().spliterator(), false)
                    .filter(this::hasNonDeveloperAuthor)
                    .sorted(Comparator.comparing(RevCommit::getCommitTime))
                    .toList();

            assertThat(commits).isNotNull();
            assertThat(commits.size())
                    .isGreaterThanOrEqualTo(CheckerConstants.GIT_MINIMUM_COMMITS);

            DEVMIND_RESULTS.add(new DevmindResult(
                    "git", PASSED, CheckerConstants.GIT_POINTS));
            TestCaseWatcher.totalPoints += CheckerConstants.GIT_POINTS;
            gitScore = CheckerConstants.GIT_POINTS;
        } catch (IOException | AssertionError | GitAPIException error) {
            DEVMIND_RESULTS.add(new DevmindErrorResult(
                    "git", CheckerConstants.GIT_POINTS, error.getMessage()));
            throw error;
        }
    }

    private static File findRepositoryRoot() throws IOException {
        File directory = new File(".").getCanonicalFile();
        while (directory != null && !new File(directory, ".git").exists()) {
            directory = directory.getParentFile();
        }
        if (directory == null) {
            throw new IOException("Git repository could not be found");

        }
        return directory;
    }

    @AfterAll
    public static void afterAll() throws JsonProcessingException {
        boolean isDevmindEnvironment = Optional.ofNullable(
                        System.getProperty("environment"))
                .map(value -> value.equals("devmind"))
                .orElse(false);

        if (isDevmindEnvironment) {
            printDevmindResults();
        } else {
            printLocalResults();
        }
    }

    private static void printLocalResults() {
        System.out.println(TestCaseWatcher.stringBuilder);

        String separator = "-----------------------------------------------------";
        int maximumTestsScore = IntStream.of(TEST_POINTS).sum();
        int maximumScore = maximumTestsScore + CheckerConstants.CHECKSTYLE_POINTS
                + CheckerConstants.README_POINTS + CheckerConstants.GIT_POINTS;
        int totalScore = testsScore + checkstyleScore + readmeScore + gitScore;

        System.out.println(separator);
        System.out.println("Tests score: " + testsScore + "/" + maximumTestsScore);

        System.out.println(separator);
        if (checkstyleErrorCount >= 0) {
            System.out.println("Checkstyle: "
                    + (checkstyleScore == CheckerConstants.CHECKSTYLE_POINTS
                    ? "OK" : "FAILED"));
            System.out.println("Checkstyle errors: " + checkstyleErrorCount);
        }
        System.out.println("Checkstyle score: " + checkstyleScore + "/"
                + CheckerConstants.CHECKSTYLE_POINTS);

        System.out.println(separator);
        System.out.println("README score: " + readmeScore + "/"
                + CheckerConstants.README_POINTS);

        System.out.println(separator);
        System.out.println("GIT commit score: " + gitScore + "/"
                + CheckerConstants.GIT_POINTS);

        System.out.println(separator);
        System.out.println("Total: " + totalScore + "/" + maximumScore);

        boolean allPassed = DEVMIND_RESULTS.size() == TOTAL_TESTS + 3
                && DEVMIND_RESULTS.stream()
                .allMatch(result -> PASSED.equals(result.getStatus()));

        if (allPassed) {
            System.out.println("Toate testele Energex-Lite au trecut cu succes!");
        }
    }

    private static void printDevmindResults() throws JsonProcessingException {
        System.out.println("BEGIN-DEVMIND-TEST-RESULTS");
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(DEVMIND_RESULTS));
        System.out.println("END-DEVMIND-TEST-RESULTS");
    }
}
