package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class PwmSettingPropertyTest {

    @Test
    public void testForMissingSettings() {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle(password.pwm.i18n.PwmSetting.class.getName(), PwmConstants.DEFAULT_LOCALE);

        final Set<String> expectedKeys = new HashSet<>();

        for (final PwmSetting pwmSetting : PwmSetting.values()) {
            final String[] keys = new String[]{
                    password.pwm.i18n.PwmSetting.SETTING_DESCRIPTION_PREFIX + pwmSetting.getKey(),
                    password.pwm.i18n.PwmSetting.SETTING_LABEL_PREFIX + pwmSetting.getKey(),
            };
            for (final String key : keys) {
                expectedKeys.add(key);
                Assert.assertTrue(
                        "PwmSettings.properties missing record for " + key,
                        resourceBundle.containsKey(key));
            }
        }

        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            final String[] keys = new String[]{
                    password.pwm.i18n.PwmSetting.CATEGORY_DESCRIPTION_PREFIX + category.getKey(),
                    password.pwm.i18n.PwmSetting.CATEGORY_LABEL_PREFIX + category.getKey(),
            };
            for (final String key : keys) {
                expectedKeys.add(key);
                Assert.assertTrue(
                        "PwmSettings.properties missing record for " + key,
                        resourceBundle.containsKey(key));
            }
        }

        final Set<String> extraKeys = new HashSet<>(resourceBundle.keySet());
        extraKeys.removeAll(expectedKeys);

        if (!extraKeys.isEmpty()) {
            Assert.fail("unexpected key in PwmSetting.properties file: " + extraKeys.iterator().next());
        }
    }
}
