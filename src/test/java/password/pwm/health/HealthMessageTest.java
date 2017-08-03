package password.pwm.health;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class HealthMessageTest {

    @Test
    public void testHealthMessageUniqueKeys() {
        final Set<String> seenKeys = new HashSet<>();
        for (final HealthMessage healthMessage : HealthMessage.values()) {
            Assert.assertTrue(!seenKeys.contains(healthMessage.getKey())); // duplicate key foud
            seenKeys.add(healthMessage.getKey());
        }
    }

    @Test
    public void testHealthMessageDescription() throws PwmUnrecoverableException {
        final Configuration configuration = new Configuration(StoredConfigurationImpl.newStoredConfiguration());
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        for (final HealthMessage healthMessage : HealthMessage.values()) {
            final String msg = healthMessage.getDescription(locale, configuration, new String[]{"field1", "field2"});
            System.out.println(msg);
        }
    }
}
