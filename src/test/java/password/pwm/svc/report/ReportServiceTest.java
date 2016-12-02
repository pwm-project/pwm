package password.pwm.svc.report;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import password.pwm.config.Configuration;
import password.pwm.svc.report.ReportService.RecordIterator;
import password.pwm.util.java.JsonUtil;

import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.*;

public class ReportServiceTest {
    private static final Type USER_CACHE_RECORD_LIST_TYPE = new TypeToken<ArrayList<UserCacheRecord>>(){}.getType();

    private Configuration configuration;
    private ReportService reportService;

    @Before
    public void setUp() throws Exception {
        configuration = mock(Configuration.class);

        // Call the real ReportService.outputToCsv() method, but mock the ReportService.iterator() method so it loads the user records from a json file.
        reportService = mock(ReportService.class);
        doCallRealMethod().when(reportService).outputToCsv(any(OutputStream.class), anyBoolean(), any(Locale.class), any(Configuration.class), any(ReportColumnFilter.class));
        doAnswer(new Answer<RecordIterator<UserCacheRecord>>() {
            @Override
            public RecordIterator<UserCacheRecord> answer(InvocationOnMock invocation) throws Throwable {
                final String userRecordsJson = IOUtils.toString(getClass().getResourceAsStream("allUserRecords.json"));
                final List<UserCacheRecord> userCacheRecords = JsonUtil.deserialize(userRecordsJson, USER_CACHE_RECORD_LIST_TYPE);
                final Iterator<UserCacheRecord> userCacheRecordIterator = userCacheRecords.iterator();

                RecordIterator<UserCacheRecord> recordIterator = new RecordIterator<UserCacheRecord>() {
                    @Override
                    public void close() {}

                    @Override
                    public void remove() {
                        userCacheRecordIterator.remove();
                    }

                    @Override
                    public boolean hasNext() {
                        return userCacheRecordIterator.hasNext();
                    }

                    @Override
                    public UserCacheRecord next() {
                        return userCacheRecordIterator.next();
                    }
                };

                return recordIterator;
            }
        }).when(reportService).iterator();
    }

    @Test
    public void testOutputToCsv_normal() throws Exception {
        // Set the desired filters
        ReportColumnFilter columnFilter = new ReportColumnFilter();
        setAllTrue(columnFilter);

        // Test the reportService.outputToCsv method()
        StringWriter outputWriter = new StringWriter();
        reportService.outputToCsv(new WriterOutputStream(outputWriter), true, Locale.ENGLISH, configuration, columnFilter);

        // Verify the results
        String actual = outputWriter.toString().replaceAll("\r", "");
        String expected = IOUtils.toString(getClass().getResourceAsStream("allUserRecordsReport.csv")).replaceAll("\r", "");
        assertEqualsIgnoreLineEndings(actual, expected);
    }

    @Test
    public void testOutputToCsv_noUserDnColumn() throws Exception {
        // Set the desired filters
        ReportColumnFilter columnFilter = new ReportColumnFilter();
        setAllTrue(columnFilter);
        columnFilter.setUserDnVisible(false);

        // Test the reportService.outputToCsv method()
        StringWriter outputWriter = new StringWriter();
        reportService.outputToCsv(new WriterOutputStream(outputWriter), true, Locale.ENGLISH, configuration, columnFilter);

        // Verify the results
        String actual = outputWriter.toString().replaceAll("\r", "");
        String expected = IOUtils.toString(getClass().getResourceAsStream("allUserRecordsReport-noUserDnColumn.csv")).replaceAll("\r", "");
        assertEqualsIgnoreLineEndings(actual, expected);
    }

    @Test
    public void testOutputToCsv_onlyUserDnColumn() throws Exception {
        // Set the desired filters
        ReportColumnFilter columnFilter = new ReportColumnFilter();
        columnFilter.setUserDnVisible(true);

        // Test the reportService.outputToCsv method()
        StringWriter outputWriter = new StringWriter();
        reportService.outputToCsv(new WriterOutputStream(outputWriter), true, Locale.ENGLISH, configuration, columnFilter);

        // Verify the results
        String actual = outputWriter.toString().replaceAll("\r", "");
        String expected = IOUtils.toString(getClass().getResourceAsStream("allUserRecordsReport-onlyUserDnColumn.csv")).replaceAll("\r", "");
        assertEqualsIgnoreLineEndings(actual, expected);
    }

    private void setAllTrue(ReportColumnFilter columnFilter) throws Exception {
        for (Method method : columnFilter.getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("set")) {
                method.invoke(columnFilter, true);
            }
        }
    }

    public static void assertEqualsIgnoreLineEndings(String actual, String expected) {
        if (actual != null) {
            actual = actual.replaceAll("\r\n", "\n");
        }

        if (expected !=null) {
            expected = expected.replaceAll("\r\n", "\n");
        }

        assertThat(actual).isEqualTo(expected);
    }


}
