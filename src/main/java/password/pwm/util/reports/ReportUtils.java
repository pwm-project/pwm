package password.pwm.util.reports;

import password.pwm.svc.report.ReportColumnFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ReportUtils {
    public static ReportColumnFilter toReportColumnFilter(final String desiredColumnsStr) {
        final ReportColumnFilter reportColumnFilter = new ReportColumnFilter();

        if (desiredColumnsStr != null) {
            final String[] selectedColumnsArray = desiredColumnsStr.split(",");
            final Set<String> desiredColumns = new HashSet<>(Arrays.asList(selectedColumnsArray));

            reportColumnFilter.setUserDnVisible(desiredColumns.contains("userDN"));
            reportColumnFilter.setLdapProfileVisible(desiredColumns.contains("ldapProfile"));
            reportColumnFilter.setUsernameVisible(desiredColumns.contains("username"));
            reportColumnFilter.setEmailVisible(desiredColumns.contains("email"));
            reportColumnFilter.setUserGuidVisible(desiredColumns.contains("userGUID"));
            reportColumnFilter.setAccountExpirationTimeVisible(desiredColumns.contains("accountExpirationTime"));
            reportColumnFilter.setLastLoginTimeVisible(desiredColumns.contains("lastLoginTime"));
            reportColumnFilter.setPasswordExpirationTimeVisible(desiredColumns.contains("passwordExpirationTime"));
            reportColumnFilter.setPasswordChangeTimeVisible(desiredColumns.contains("passwordChangeTime"));
            reportColumnFilter.setResponseSetTimeVisible(desiredColumns.contains("responseSetTime"));
            reportColumnFilter.setHasResponsesVisible(desiredColumns.contains("hasResponses"));
            reportColumnFilter.setHasHelpdeskResponsesVisible(desiredColumns.contains("hasHelpdeskResponses"));
            reportColumnFilter.setResponseStorageMethodVisible(desiredColumns.contains("responseStorageMethod"));
            reportColumnFilter.setResponseFormatTypeVisible(desiredColumns.contains("responseFormatType"));
            reportColumnFilter.setPasswordStatusExpiredVisible(desiredColumns.contains("passwordStatusExpired"));
            reportColumnFilter.setPasswordStatusPreExpiredVisible(desiredColumns.contains("passwordStatusPreExpired"));
            reportColumnFilter.setPasswordStatusViolatesPolicyVisible(desiredColumns.contains("passwordStatusViolatesPolicy"));
            reportColumnFilter.setPasswordStatusWarnPeriodVisible(desiredColumns.contains("passwordStatusWarnPeriod"));
            reportColumnFilter.setRequiresPasswordUpdateVisible(desiredColumns.contains("requiresPasswordUpdate"));
            reportColumnFilter.setRequiresResponseUpdateVisible(desiredColumns.contains("requiresResponseUpdate"));
            reportColumnFilter.setRequiresProfileUpdateVisible(desiredColumns.contains("requiresProfileUpdate"));
            reportColumnFilter.setCacheTimestampVisible(desiredColumns.contains("cacheTimestamp"));
        }

        return reportColumnFilter;
    }
}
