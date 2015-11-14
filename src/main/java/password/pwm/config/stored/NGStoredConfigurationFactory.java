package password.pwm.config.stored;

import org.jdom2.Document;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.XmlUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class NGStoredConfigurationFactory {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NGStoredConfigurationFactory.class);

    //@Override
    public NGStoredConfiguration fromXml(InputStream inputStream) throws PwmUnrecoverableException {
        return XmlEngine.fromXmlImpl(inputStream);
    }

    //@Override
    public void toXml(final OutputStream outputStream) {
    }

    private static class XmlEngine {
        public static final String XML_ELEMENT_ROOT = "PwmConfiguration";
        public static final String XML_ELEMENT_PROPERTIES = "properties";
        public static final String XML_ELEMENT_PROPERTY = "property";
        public static final String XML_ELEMENT_SETTINGS = "settings";
        public static final String XML_ELEMENT_SETTING = "setting";
        public static final String XML_ELEMENT_DEFAULT = "default";

        public static final String XML_ATTRIBUTE_TYPE = "type";
        public static final String XML_ATTRIBUTE_KEY = "key";
        public static final String XML_ATTRIBUTE_SYNTAX = "syntax";
        public static final String XML_ATTRIBUTE_PROFILE = "profile";
        public static final String XML_ATTRIBUTE_VALUE_APP = "app";
        public static final String XML_ATTRIBUTE_VALUE_CONFIG = "config";
        public static final String XML_ATTRIBUTE_CREATE_TIME = "createTime";
        public static final String XML_ATTRIBUTE_MODIFY_TIME = "modifyTime";
        public static final String XML_ATTRIBUTE_MODIFY_USER = "modifyUser";
        public static final String XML_ATTRIBUTE_MODIFY_USER_PROFILE = "modifyUserProfile";
        public static final String XML_ATTRIBUTE_SYNTAX_VERSION = "syntaxVersion";

        static NGStoredConfiguration fromXmlImpl(InputStream inputStream)
                throws PwmUnrecoverableException
        {
            final Map<StoredConfigReference,ValueWrapper> values = new HashMap<>();
            final Document inputDocument = XmlUtil.parseXml(inputStream);
            final Element rootElement = inputDocument.getRootElement();
            final Element settingsElement = rootElement.getChild(XML_ELEMENT_SETTINGS);
            for (final Element settingElement : settingsElement.getChildren(XML_ELEMENT_SETTING)) {
                try {
                    final String key = settingElement.getAttributeValue(XML_ATTRIBUTE_KEY);
                    final PwmSetting pwmSetting = PwmSetting.forKey(key);

                    if (pwmSetting == null) {
                        LOGGER.debug("ignoring setting for unknown key: " + key);
                    } else {
                        final String profileID = settingElement.getAttributeValue(XML_ATTRIBUTE_PROFILE);
                        LOGGER.trace("parsing setting key=" + key + ", profile=" + profileID);
                        final String modifyDateStr = settingElement.getAttributeValue(XML_ATTRIBUTE_MODIFY_TIME);
                        final Date modifyDate = modifyDateStr == null || modifyDateStr.isEmpty()
                                ? null
                                : PwmConstants.DEFAULT_DATETIME_FORMAT.parse(modifyDateStr);

                        StoredValue storedValue = null;
                        {
                            if (settingElement.getChild(XML_ELEMENT_DEFAULT) != null) {
                                if (!pwmSetting.isConfidential()) { //@todo temporary
                                    storedValue = ValueFactory.fromXmlValues(pwmSetting, settingElement, null);
                                }
                            }
                        }

                        final UserIdentity modifyUser = null;

                        ValueWrapper valueWrapper = new ValueWrapper(storedValue, new ValueMetaData(modifyDate, modifyUser));
                        values.put(
                                new StoredConfigReferenceBean(StoredConfigReference.RecordType.SETTING, key, profileID),
                                valueWrapper
                        );
                    }
                } catch (PwmOperationalException | ParseException e) {
                    e.printStackTrace();
                }
            }
            return new NGStoredConfiguration(values);
        }
    }

}
