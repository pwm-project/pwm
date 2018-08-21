package password.pwm.i18n;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.util.java.StringUtil;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

public class NonLocalizedKeyTest {
    private static final String[] NON_LOCALIZED_KEYS = {
            "Title_Application",
            "Title_TitleBarAuthenticated",
            "Title_TitleBar"
    };

    @Test
    public void testNonLocalizedKeyTest() throws Exception {

        { // check default locales have value
            final ResourceBundle resourceBundle = ResourceBundle.getBundle(Display.class.getName(), PwmConstants.DEFAULT_LOCALE);
            for (final String key : NON_LOCALIZED_KEYS) {
                final String value = resourceBundle.getString(key);
                Assert.assertTrue(!StringUtil.isEmpty(value));
            }
        }

        { // check non-default locales do NOT have value
            final Configuration configuration = new Configuration(StoredConfigurationImpl.newStoredConfiguration());
            final List<Locale> locales = configuration.getKnownLocales();
            for (final Locale locale : locales) {
                if (!PwmConstants.DEFAULT_LOCALE.toLanguageTag().equals(locale.toLanguageTag())) {
                    final String resourceFileName = Display.class.getName().replace(".","/")
                            + "_" + locale.toLanguageTag().replace("-","_")
                            + ".properties";
                    final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(resourceFileName);
                    if (inputStream == null) {
                    } else {
                        final Properties props = new Properties();
                        props.load(inputStream);
                        for (final String key : NON_LOCALIZED_KEYS) {
                            final String value = props.getProperty(key);
                            final String msg = "Display bundle for locale '" + locale.toLanguageTag() + "' has key '"
                            + key + "'.  Only the default locale should have this key";
                            Assert.assertTrue(msg , StringUtil.isEmpty(value));
                        }
                    }
                }
            }
        }
    }
}

