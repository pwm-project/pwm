package password.pwm.health;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ApplianceStatusChecker implements HealthChecker {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ApplianceStatusChecker.class);

    private static final String DEFAULT_DOCKER_HOST = "172.17.0.1";
    private static final String TOKEN_FILE = "/config/token";
    private static final int APPLIANCE_PORT = 9443;

    private String applianceHost;
    private String applianceAccessToken;

    private static class UpdateStatus {
        boolean updatesReadyForInstall;
        boolean updateNowEnabled;
        boolean updateServiceConfigured;
    }

    @Override
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        final boolean isApplianceAvailable = pwmApplication.getPwmEnvironment().getFlags().contains(PwmEnvironment.ApplicationFlag.Appliance);
        if (isApplianceAvailable) {
            try {
                final List<HealthRecord> healthRecords = new ArrayList<>();

                final String applianceHost = getApplianceHost(pwmApplication);
                final String applianceAccessToken = getApplianceAccessToken();

                final HttpGet httpGet = new HttpGet(String.format("https://%s:%d/sspr/appliance-update-status", applianceHost, APPLIANCE_PORT));
                httpGet.setHeader(PwmConstants.HttpHeader.SsprAuthorizationToken.getHttpName(), applianceAccessToken);

                final PwmHttpClientConfiguration.Builder builder = new PwmHttpClientConfiguration.Builder();
                builder.setPromiscuous(true);

                final HttpResponse httpResponse = PwmHttpClient.getHttpClient(pwmApplication.getConfig(), builder.create()).execute(httpGet);
                final String jsonString = EntityUtils.toString(httpResponse.getEntity());
                LOGGER.debug("Response from /sspr/appliance-update-status: " + jsonString);

                final UpdateStatus updateStatus = JsonUtil.deserialize(jsonString, UpdateStatus.class);

                if (updateStatus.updatesReadyForInstall) {
                    healthRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Appliance, "Appliance security updates are pending installation."));
                }

                if (!updateStatus.updateNowEnabled) {
                    healthRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Appliance, "Appliance auto-updates are not enabled."));
                }

                if (!updateStatus.updateServiceConfigured) {
                    healthRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Appliance, "Appliance update service has not been configured."));
                }

                return Collections.unmodifiableList(healthRecords);
            } catch (Exception e) {
                LOGGER.error("An error occurred checking appliance status: " + e.getMessage(), e);
                return Arrays.asList(new HealthRecord(HealthStatus.WARN, HealthTopic.Appliance, "An error occurred checking appliance update status: " + e.getMessage()));
            }
        }

        return Collections.emptyList();
    }

    private String getApplianceAccessToken() throws IOException {
        if (applianceAccessToken == null) {
            final String fileInput = FileUtils.readFileToString(new File(TOKEN_FILE));
            if (fileInput != null) {
                applianceAccessToken = fileInput.trim();
            }
        }

        return applianceAccessToken;
    }

    private String getApplianceHost(final PwmApplication pwmApplication) {
        if (applianceHost == null) {
            try {
                // The file: /usr/local/tomcat/webapps/sspr/WEB-INF/appliance-host gets created during the docker startup command (see Docker/startup.sh in the SSPR project)
                final File applianceHostFile = FileSystemUtility.figureFilepath("appliance-host", pwmApplication.getPwmEnvironment().getApplicationPath());
                applianceHost = FileUtils.readFileToString(applianceHostFile);
            } catch (IOException e) {
                LOGGER.error("Unable to read the hostname for the docker host, using default: " + DEFAULT_DOCKER_HOST, e);
                applianceHost = DEFAULT_DOCKER_HOST;
            }
        }

        return applianceHost;
    }

}
