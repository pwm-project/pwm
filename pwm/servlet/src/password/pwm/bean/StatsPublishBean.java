package password.pwm.bean;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class StatsPublishBean implements Serializable {
    private String instanceID;
    private Date timestamp;
    private Map<String,String> totalStatistics;
    private List<String> configuredSettings;
    private String versionBuild;
    private String versionVersion;

    public StatsPublishBean() {
    }

    public StatsPublishBean(String instanceID, Date timestamp, Map<String, String> totalStatistics, List<String> configuredSettings, String versionBuild, String versionVersion) {
        this.instanceID = instanceID;
        this.timestamp = timestamp;
        this.totalStatistics = totalStatistics;
        this.configuredSettings = configuredSettings;
        this.versionBuild = versionBuild;
        this.versionVersion = versionVersion;
    }

    public String getInstanceID() {
        return instanceID;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getTotalStatistics() {
        return totalStatistics;
    }

    public List<String> getConfiguredSettings() {
        return configuredSettings;
    }

    public String getVersionBuild() {
        return versionBuild;
    }

    public String getVersionVersion() {
        return versionVersion;
    }
}
