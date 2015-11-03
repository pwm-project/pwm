package password.pwm.config.stored;

import org.jdom2.Document;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
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

class NGStoredConfiguration implements StoredConfiguration,  StoredConfigurationProvider {
    final static private PwmLogger LOGGER = PwmLogger.forClass(NGStoredConfiguration.class);

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
    public static final String XML_ATTRIBUTE_SYNTAX_VERSION = "syntaxVersion";

    final Map<StoredConfigReference, ValueWrapper> values = new HashMap<>();
    static class ValueWrapper {
        private final Date modifyDate;
        private final UserIdentity modifyUser;
        private final StoredValue storedValue;

        public ValueWrapper(Date modifyDate, UserIdentity modifyUser, StoredValue storedValue) {
            this.modifyDate = modifyDate;
            this.modifyUser = modifyUser;
            this.storedValue = storedValue;
        }

        public Date getModifyDate() {
            return modifyDate;
        }

        public UserIdentity getModifyUser() {
            return modifyUser;
        }

        public StoredValue getStoredValue() {
            return storedValue;
        }
    }

    @Override
    public StoredConfiguration fromXml(InputStream inputStream) throws PwmUnrecoverableException {
        return fromXmlImpl(inputStream);
    }


    public static StoredConfiguration fromXmlImpl(InputStream inputStream) throws PwmUnrecoverableException {
        final NGStoredConfiguration config = new NGStoredConfiguration();
        final Document inputDocument = XmlUtil.parseXml(inputStream);
        final Element rootElement = inputDocument.getRootElement();
        final Element settingsElement = rootElement.getChild("settings");
        for (final Element settingElement : settingsElement.getChildren("setting")) {
            try {
                final String key = settingElement.getAttributeValue("key");
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
                    if (settingElement.getChild(XML_ELEMENT_DEFAULT) != null) {
                        if (!pwmSetting.isConfidential()) { //@todo temporary
                            storedValue = ValueFactory.fromXmlValues(pwmSetting, settingElement, null);
                        }
                    }
                    config.values.put(
                            new StoredConfigReferenceBean(StoredConfigReference.Type.Setting, key, profileID),
                            new ValueWrapper(modifyDate, null, storedValue)
                    );
                }
            } catch (PwmOperationalException | ParseException e) {
                e.printStackTrace();
            }
        }
        return config;
    }

    @Override
    public void toXml(OutputStream outputStream) {

    }

    @Override
    public void resetSetting(PwmSetting setting, String profileID, UserIdentity userIdentity) {

    }

    @Override
    public boolean isDefaultValue(PwmSetting setting) {
        return false;
    }

    @Override
    public boolean isDefaultValue(PwmSetting setting, String profileID) {
        return false;
    }

    @Override
    public StoredValue readSetting(PwmSetting setting) {
        return null;
    }

    @Override
    public StoredValue readSetting(PwmSetting setting, String profileID) {
        return null;
    }

    @Override
    public void copyProfileID(PwmSettingCategory category, String sourceID, String destinationID, UserIdentity userIdentity) throws PwmUnrecoverableException {

    }

    @Override
    public void writeSetting(PwmSetting setting, StoredValue value, UserIdentity userIdentity) throws PwmUnrecoverableException {

    }

    @Override
    public void writeSetting(PwmSetting setting, String profileID, StoredValue value, UserIdentity userIdentity) throws PwmUnrecoverableException {

    }
}
