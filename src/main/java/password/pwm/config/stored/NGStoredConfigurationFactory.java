package password.pwm.config.stored;

import org.jdom2.Document;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.XmlUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static password.pwm.config.stored.StoredConfiguration.*;

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

        static NGStoredConfiguration fromXmlImpl(InputStream inputStream)
                throws PwmUnrecoverableException
        {
            final Map<StoredConfigReference, StoredValue> values = new LinkedHashMap<>();
            final Map<StoredConfigReference, ValueMetaData> metaData = new LinkedHashMap<>();

            final Document inputDocument = XmlUtil.parseXml(inputStream);
            final Element rootElement = inputDocument.getRootElement();

            final PwmSecurityKey pwmSecurityKey = readSecurityKey(rootElement);

            final Element settingsElement = rootElement.getChild(XML_ELEMENT_SETTINGS);

            for (final Element loopElement : settingsElement.getChildren()) {
                if (XML_ELEMENT_PROPERTIES.equals(loopElement.getName())) {
                    for (final Element propertyElement : loopElement.getChildren(XML_ELEMENT_PROPERTY)) {
                        readInterestingElement(propertyElement, pwmSecurityKey, values, metaData);
                    }
                } else {
                    readInterestingElement(loopElement, pwmSecurityKey, values, metaData);
                }
            }
            return new NGStoredConfiguration(values, metaData, readSecurityKey(rootElement));
        }

        static void readInterestingElement(
                final Element loopElement,
                final PwmSecurityKey pwmSecurityKey,
                final Map<StoredConfigReference, StoredValue> values,
                final Map<StoredConfigReference, ValueMetaData> metaData
        )
        {
            final StoredConfigReference reference = referenceForElement(loopElement);
            if (reference != null) {
                switch (reference.getRecordType()) {
                    case SETTING:
                    {
                        final StoredValue storedValue = readSettingValue(reference, loopElement, pwmSecurityKey);
                        values.put(reference, storedValue);
                    }
                    break;

                    case PROPERTY:
                    {
                        final StoredValue storedValue = readPropertyValue(reference, loopElement);
                    }
                    break;

                    default:
                        throw new IllegalArgumentException("unimplemented setting recordtype in reader");
                }
                final ValueMetaData valueMetaData = readValueMetaData(loopElement);
                if (valueMetaData != null) {
                    metaData.put(reference, valueMetaData);
                }
            }
        }

        static PwmSecurityKey readSecurityKey(final Element rootElement)
                throws PwmUnrecoverableException
        {
            final String createTime = rootElement.getAttributeValue(XML_ATTRIBUTE_CREATE_TIME);
            return new PwmSecurityKey(createTime + "StoredConfiguration");
        }

        static StoredValue readSettingValue(
                final StoredConfigReference storedConfigReference,
                final Element settingElement,
                final PwmSecurityKey pwmSecurityKey
        )
        {
            final String key = storedConfigReference.getRecordID();
            final PwmSetting pwmSetting = PwmSetting.forKey(key);

            if (pwmSetting == null) {
                LOGGER.debug("ignoring setting for unknown key: " + key);
            } else {
                LOGGER.trace("parsing setting key=" + key + ", profile=" + storedConfigReference.getProfileID());
                if (settingElement.getChild(XML_ELEMENT_DEFAULT) != null) {
                    try {
                        return ValueFactory.fromXmlValues(pwmSetting, settingElement, pwmSecurityKey);
                    } catch (PwmException e) {
                        LOGGER.error("error parsing configuration setting " + storedConfigReference + ", error: " + e.getMessage());
                    }
                }
            }
            return null;
        }

        static StoredValue readPropertyValue(
                final StoredConfigReference storedConfigReference,
                final Element settingElement
        )
        {
            final String key = storedConfigReference.getRecordID();
            final ConfigurationProperty configProperty = ConfigurationProperty.valueOf(key);

            if (configProperty == null) {
                LOGGER.debug("ignoring property for unknown key: " + key);
            } else {
                LOGGER.trace("parsing property key=" + key + ", profile=" + storedConfigReference.getProfileID());
                if (settingElement.getChild(XML_ELEMENT_DEFAULT) != null) {
                    return new StringValue(settingElement.getValue());
                }
            }
            return null;
        }

        static StoredConfigReference referenceForElement(final Element settingElement) {
            final String key = settingElement.getAttributeValue(XML_ATTRIBUTE_KEY);
            final String profileID = readProfileID(settingElement);
            final StoredConfigReference.RecordType recordType;
            switch (settingElement.getName()) {
                case XML_ELEMENT_SETTING:
                    recordType = StoredConfigReference.RecordType.SETTING;
                    break;

                case XML_ELEMENT_PROPERTY:
                    recordType = StoredConfigReference.RecordType.PROPERTY;
                    break;

                case XML_ELEMENT_LOCALEBUNDLE:
                    recordType = StoredConfigReference.RecordType.LOCALE_BUNDLE;
                    break;

                default:
                    LOGGER.warn("unrecognized xml element " + settingElement.getName() + " in configuration");
                    return null;
            }


            return new StoredConfigReferenceBean(
                    recordType,
                    key,
                    profileID
            );
        }

        static String readProfileID(final Element settingElement) {
            final String profileIDStr = settingElement.getAttributeValue(XML_ATTRIBUTE_PROFILE);
            return  profileIDStr != null && !profileIDStr.isEmpty() ? profileIDStr : null;
        }

        static ValueMetaData readValueMetaData(final Element element)
        {
            final String modifyDateStr = element.getAttributeValue(XML_ATTRIBUTE_MODIFY_TIME);
            Date modifyDate = null;
            try {
                modifyDate = modifyDateStr == null || modifyDateStr.isEmpty()
                        ? null
                        : PwmConstants.DEFAULT_DATETIME_FORMAT.parse(modifyDateStr);
            } catch (ParseException e) {
                LOGGER.warn("error parsing stored date: " +  e.getMessage());
            }
            final String modifyUser = element.getAttributeValue(XML_ATTRIBUTE_MODIFY_USER);
            final String modifyUserProfile = element.getAttributeValue(XML_ATTRIBUTE_MODIFY_USER_PROFILE);
            final UserIdentity userIdentity;
            userIdentity = modifyUser != null
                    ? new UserIdentity(modifyUser, modifyUserProfile)
                    : null;

            return new ValueMetaData(modifyDate, userIdentity);
        }
    }

}
