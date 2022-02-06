package controllers;

import com.silibrina.tecnova.coherence.CoherenceMain;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.conversion.ConversionMain;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import static com.silibrina.tecnova.commons.utils.TestsUtils.cleanUpEntries;
import static com.silibrina.tecnova.commons.utils.TestsUtils.getFileSystemsToTest;
import static com.silibrina.tecnova.opendata.utils.ConstantUtils.FilesPathUtils.PDF_FILE;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static play.test.Helpers.POST;
import static play.test.Helpers.route;

@RunWith(Parameterized.class)
public class ParallelRequestsTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(DataGatheringTests.class);

    private CoherenceMain coherenceMain;
    private ConversionMain conversionMain;

    private final FileSystem fileSystem;

    public ParallelRequestsTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        cleanUpEntries();

        coherenceMain = new CoherenceMain(false);
        coherenceMain.start();

        conversionMain = new ConversionMain(false, fileSystem);
        conversionMain.start();

        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        coherenceMain.stopCoherence();
        conversionMain.stopConverter();

        cleanUpEntries();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    @Test
    public void createTest() throws InterruptedException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        int threadsNum = 8;

        Thread[] threads = new Thread[threadsNum];
        for (int i = 0; i < threads.length; i++) {
            final int label = i;
            threads[i] = new Thread("worker-" + label) {

                @Override
                public void run() {
                    try {
                        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri("/data");
                        request.bodyMultipart(generateFormBody(label, 0, 0, PDF_FILE), mat);

                        Result result = route(request);
                        assertResultIsOk(result);

                    } catch (Throwable e) {
                        logger.error("An error while creating entries", e);
                    } finally {
                        logger.debug("client - finishing creating: {}", label);
                    }
                }
            };
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }


}
