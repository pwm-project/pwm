/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config;

import com.google.gson.GsonBuilder;
import org.jdom2.*;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.BCrypt;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Jason D. Rivard
 */
public class StoredConfiguration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    public enum ConfigProperty {
        PROPERTY_KEY_SETTING_CHECKSUM("settingsChecksum"),
        PROPERTY_KEY_CONFIG_IS_EDITABLE("configIsEditable"),
        PROPERTY_KEY_CONFIG_EPOCH("configEpoch"),
        PROPERTY_KEY_TEMPLATE("configTemplate"),
        PROPERTY_KEY_NOTES("notes"),
        PROPERTY_KEY_PASSWORD_HASH("configPasswordHash"),
        ;

        private final String key;

        private ConfigProperty(String key)
        {
            this.key = key;
        }

        public String getKey()
        {
            return key;
        }
    }

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StoredConfiguration.class);
    private static final DateFormat CONFIG_ATTR_DATETIME_FORMAT;
    private static final String XML_FORMAT_VERSION = "3";

    private static final String XML_ELEMENT_ROOT = "PwmConfiguration";
    private static final String XML_ELEMENT_PROPERTIES = "properties";
    private static final String XML_ELEMENT_PROPERTY = "property";
    private static final String XML_ATTRIBUTE_TYPE = "type";
    private static final String XML_ATTRIBUTE_KEY = "key";
    private static final String XML_ATTRIBUTE_VALUE_APP = "app";
    private static final String XML_ATTRIBUTE_VALUE_CONFIG = "config";

    private Date createTime = new Date();
    private Date modifyTime = new Date();

    private Document document = new Document(new Element(XML_ELEMENT_ROOT));
    private ChangeLog changeLog = new ChangeLog();

    private boolean locked = false;
    private boolean setting_writeLabels = false;
    private final ReentrantReadWriteLock domModifyLock = new ReentrantReadWriteLock();

    static {
        CONFIG_ATTR_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        CONFIG_ATTR_DATETIME_FORMAT.setTimeZone(TimeZone.getTimeZone("Zulu"));
    }

// -------------------------- STATIC METHODS --------------------------

    public static StoredConfiguration getDefaultConfiguration() {
        return new StoredConfiguration();
    }

    public static StoredConfiguration fromXml(final String xmlData)
            throws PwmUnrecoverableException
    {
        validateXmlSchema(xmlData);

        final SAXBuilder builder = new SAXBuilder();
        final Document inputDocument;
        try {
            inputDocument = builder.build(new StringReader(xmlData));
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"error parsing xml data: " + e.getMessage()));
        }

        final StoredConfiguration newConfiguration = StoredConfiguration.getDefaultConfiguration();
        try {
            final Element rootElement = inputDocument.getRootElement();
            newConfiguration.document = inputDocument;
            final String createTimeString = rootElement.getAttributeValue("createTime");
            final String modifyTimeString = rootElement.getAttributeValue("modifyTime");
            if (createTimeString == null) {
                throw new IllegalArgumentException("missing createTime timestamp");
            }
            if (modifyTimeString == null) {
                throw new IllegalArgumentException("missing modifyTime timestamp");
            }
            newConfiguration.createTime = CONFIG_ATTR_DATETIME_FORMAT.parse(createTimeString);
            newConfiguration.modifyTime = CONFIG_ATTR_DATETIME_FORMAT.parse(modifyTimeString);
            fixupMandatoryElements(inputDocument, newConfiguration);
            newConfiguration.validateValues();
        } catch (Exception e) {
            final String errorMsg = "error reading configuration file format, error=" + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            throw new PwmUnrecoverableException(errorInfo);
        }

        LOGGER.debug("successfully loaded configuration");
        return newConfiguration;
    }

    public StoredConfiguration()
    {
        fixupMandatoryElements(document, this);
    }

    // --------------------- GETTER / SETTER METHODS ---------------------

    public Date getModifyTime() {
        return modifyTime;
    }

// ------------------------ CANONICAL METHODS ------------------------

// -------------------------- OTHER METHODS --------------------------

    public String readConfigProperty(final ConfigProperty propertyName) {
        final XPathExpression xp = XPathBuilder.xpathForConfigProperty(propertyName);
        final Element propertyElement = (Element)xp.evaluateFirst(document);
        return propertyElement == null ? null : propertyElement.getText();
    }

    public void writeConfigProperty(
            final ConfigProperty propertyName,
            final String value
    ) {
        preModifyActions();

        domModifyLock.writeLock().lock();
        try {

            final XPathExpression xp = XPathBuilder.xpathForConfigProperty(propertyName);
            final List<Element> propertyElements = xp.evaluate(document);
            for (final Element propertyElement : propertyElements) {
                propertyElement.detach();
            }

            final Element propertyElement = new Element(XML_ELEMENT_PROPERTY);
            propertyElement.setAttribute(new Attribute(XML_ATTRIBUTE_KEY,propertyName.getKey()));
            propertyElement.setContent(new Text(value));

            if (null == XPathBuilder.xpathForConfigProperties().evaluateFirst(document)) {
                Element configProperties = new Element(XML_ELEMENT_PROPERTIES);
                configProperties.setAttribute(new Attribute(XML_ATTRIBUTE_TYPE,XML_ATTRIBUTE_VALUE_CONFIG));
                document.getRootElement().addContent(configProperties);
            }

            final XPathExpression xp2 = XPathBuilder.xpathForConfigProperties();
            final Element propertiesElement = (Element)xp2.evaluateFirst(document);
            propertiesElement.addContent(propertyElement);
        } finally {
            domModifyLock.writeLock().unlock();
        }
    }

    public String readAppProperty(final AppProperty propertyName) {
        domModifyLock.readLock().lock();
        try {
            final XPathExpression xp = XPathBuilder.xpathForAppProperty(propertyName);
            final Element propertyElement = (Element)xp.evaluateFirst(document);
            return propertyElement == null ? null : propertyElement.getText();
        } finally {
            domModifyLock.readLock().unlock();
        }
    }

    public void writeAppProperty(final AppProperty appProperty, final String value) {
        preModifyActions();
        changeLog.updateChangeLog(appProperty, value);

        domModifyLock.writeLock().lock();
        try {
            final XPathExpression xp = XPathBuilder.xpathForAppProperty(appProperty);
            final List<Element> propertyElements = xp.evaluate(document);
            for (final Element properElement : propertyElements) {
                properElement.detach();
            }

            final Element propertyElement = new Element(XML_ELEMENT_PROPERTY);
            propertyElement.setAttribute(new Attribute(XML_ATTRIBUTE_KEY,appProperty.getKey()));
            propertyElement.setContent(new Text(value));

            if (null == XPathBuilder.xpathForAppProperties().evaluateFirst(document)) {
                Element configProperties = new Element(XML_ELEMENT_PROPERTIES);
                configProperties.setAttribute(new Attribute(XML_ATTRIBUTE_TYPE,XML_ATTRIBUTE_VALUE_APP));
                document.getRootElement().addContent(configProperties);
            }

            final XPathExpression xp2 = XPathBuilder.xpathForAppProperties();
            final Element propertiesElement = (Element)xp2.evaluateFirst(document);
            propertiesElement.addContent(propertyElement);
        } finally {
            domModifyLock.writeLock().unlock();
        }
    }

    public void lock() {
        locked = true;
    }

    public Map<String,String> readLocaleBundleMap(final String bundleName, final String keyName) {
        domModifyLock.readLock().lock();
        try {
            final XPathExpression xp = XPathBuilder.xpathForLocaleBundleSetting(bundleName, keyName);
            final Element localeBundleElement = (Element)xp.evaluateFirst(document);
            if (localeBundleElement != null) {
                final Map<String,String> bundleMap = new LinkedHashMap<String, String>();
                for (final Element valueElement : localeBundleElement.getChildren("value")) {
                    final String localeStrValue = valueElement.getAttributeValue("locale");
                    bundleMap.put(localeStrValue == null ? "" : localeStrValue, valueElement.getText());
                }
                if (!bundleMap.isEmpty()) {
                    return bundleMap;
                }
            }
        } finally {
            domModifyLock.readLock().unlock();
        }
        return Collections.emptyMap();
    }

    public void resetLocaleBundleMap(final String bundleName, final String keyName) {
        preModifyActions();
        domModifyLock.writeLock().lock();
        try {
            final XPathExpression xp = XPathBuilder.xpathForLocaleBundleSetting(bundleName, keyName);
            final List<Element> oldBundleElements = xp.evaluate(document);
            if (oldBundleElements != null) {
                for (final Element element : oldBundleElements) {
                    element.detach();
                }
            }
        } finally {
            domModifyLock.writeLock().unlock();
        }
    }

    public void resetSetting(final PwmSetting setting) {
        resetSetting(setting, null);
    }

    public void resetSetting(final PwmSetting setting, final String profileID) {
        changeLog.updateChangeLog(setting, profileID, defaultValue(setting, this.getTemplate()));
        resetSettingInternal(setting,profileID);
    }

    protected void resetSettingInternal(final PwmSetting setting, final String profileID) {
        domModifyLock.writeLock().lock();
        preModifyActions();
        try {
            final XPathExpression xp = XPathBuilder.xpathForSetting(setting, profileID);
            final List<Element> oldSettingElements = xp.evaluate(document);
            if (oldSettingElements != null) {
                for (final Element settingElement : oldSettingElements) {
                    final Element parent = settingElement.getParentElement();
                    parent.removeContent(settingElement);
                }
            }
        } finally {
            domModifyLock.writeLock().unlock();
        }
    }

    public boolean isDefaultValue(final PwmSetting setting) {
        return isDefaultValue(setting, null);
    }

    public boolean isDefaultValue(final PwmSetting setting, final String profileID) {
        domModifyLock.readLock().lock();
        try {
            final StoredValue currentValue = readSetting(setting, profileID);
            final StoredValue defaultValue = defaultValue(setting, this.getTemplate());
            return currentValue.toDebugString().equals(defaultValue.toDebugString());
        } finally {
            domModifyLock.readLock().unlock();
        }
    }

    private static StoredValue defaultValue(final PwmSetting pwmSetting, final PwmSetting.Template template)
    {
        try {
            return pwmSetting.getDefaultValue(template);
        } catch (PwmOperationalException e) {
            final String errorMsg = "error reading default value for setting " + pwmSetting.toString() + ", error: " + e.getErrorInformation().toDebugStr();
            LOGGER.error(errorMsg,e);
            throw new IllegalStateException(errorMsg);
        }
    }

    public PwmSetting.Template getTemplate() {
        final String propertyValue = readConfigProperty(ConfigProperty.PROPERTY_KEY_TEMPLATE);
        try {
            return PwmSetting.Template.valueOf(propertyValue);
        } catch (IllegalArgumentException e) {
            return PwmSetting.Template.DEFAULT;
        } catch (NullPointerException e) {
            return PwmSetting.Template.DEFAULT;
        }
    }

    public void setTemplate(PwmSetting.Template template) {
        writeConfigProperty(ConfigProperty.PROPERTY_KEY_TEMPLATE, template.toString());
    }

    public String toString() {
        return toString(false);
    }

    public String toString(final PwmSetting setting, final String profileID ) {
        final StoredValue storedValue = readSetting(setting, profileID);
        return setting.getKey() + "=" + storedValue.toDebugString();
    }

    public String toString(final boolean linebreaks) {
        domModifyLock.readLock().lock();
        try {
            final TreeMap<String,Object> outputObject = new TreeMap<String,Object>();

            for (final PwmSetting setting : PwmSetting.values()) {
                if (setting.getSyntax() != PwmSettingSyntax.PROFILE && setting.getCategory().getType() != PwmSetting.Category.Type.PROFILE) {
                    if (!isDefaultValue(setting,null)) {
                        final StoredValue value = readSetting(setting);
                        outputObject.put(setting.getKey(), value.toDebugString());
                    }
                }
            }

            for (final PwmSetting.Category category : PwmSetting.Category.values()) {
                if (category.getType() == PwmSetting.Category.Type.PROFILE) {
                    final TreeMap<String,Object> profiles = new TreeMap<String,Object>();
                    for (final String profileID : this.profilesForSetting(category.getProfileSetting())) {
                        final TreeMap<String,String> profileObject = new TreeMap<String,String>();
                        for (final PwmSetting profileSetting : PwmSetting.getSettings(category)) {
                            if (!isDefaultValue(profileSetting, profileID)) {
                                final StoredValue value = readSetting(profileSetting, profileID);
                                profileObject.put(profileSetting.getKey(), value.toDebugString());
                            }
                        }
                        final String key = "".equals(profileID) ? "default" : profileID;
                        profiles.put(key,profileObject);
                    }
                    outputObject.put(category.getProfileSetting().getKey(),profiles);
                }
            }

            final GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.disableHtmlEscaping();
            if (linebreaks) {
                gsonBuilder.setPrettyPrinting();
            }
            return Helper.getGson(gsonBuilder).toJson(outputObject);
        } finally {
            domModifyLock.readLock().unlock();
        }
    }

    public String toXml()
            throws IOException, PwmUnrecoverableException
    {
        fixupMandatoryElements(document, this);

        final Format format = Format.getPrettyFormat();
        format.setEncoding("UTF-8");
        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(format);
        final String outputString = outputter.outputString(document);
        validateXmlSchema(outputString);
        return outputString;
    }

    public List<String> profilesForSetting(final PwmSetting pwmSetting) {
        if (pwmSetting.getCategory().getType() != PwmSetting.Category.Type.PROFILE && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE) {
            throw new IllegalArgumentException("cannot build profile list for non-profile setting " + pwmSetting.toString());
        }

        final PwmSetting profileSetting;
        if (pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE) {
            profileSetting = pwmSetting;
        } else {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        final List<String> settingValues = (List<String>)readSetting(profileSetting).toNativeObject();
        final LinkedList<String> profiles = new LinkedList<String>();
        profiles.addAll(settingValues);
        for (Iterator<String> iterator = profiles.iterator(); iterator.hasNext();) {
            final String profile = iterator.next();
            if (profile == null || profile.length() < 1) {
                iterator.remove();
            }
        }
        profiles.addFirst("");
        return Collections.unmodifiableList(profiles);
    }

    public List<String> validateValues() {
        final List<String> errorStrings = new ArrayList<String>();

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            final StringBuilder errorPrefix = new StringBuilder();
            errorPrefix.append(loopSetting.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
            errorPrefix.append("-");
            errorPrefix.append(loopSetting.getLabel(PwmConstants.DEFAULT_LOCALE));

            if (loopSetting.getCategory().getType() == PwmSetting.Category.Type.PROFILE) {
                errorPrefix.append("-");
                for (final String profile : profilesForSetting(loopSetting)) {
                    final String errorAppend = "".equals(profile) ? "Default" : profile;
                    final StoredValue loopValue = readSetting(loopSetting,profile);

                    try {
                        final List<String> errors = loopValue.validateValue(loopSetting);
                        for (final String loopError : errors) {
                            errorStrings.add(errorPrefix + errorAppend + " " + loopError);
                        }
                    } catch (Exception e) {
                        LOGGER.error("unexpected error during validate value for " + errorPrefix + errorAppend + ", error: " + e.getMessage(),e);
                    }
                }
            } else {
                errorPrefix.append(" ");
                final StoredValue loopValue = readSetting(loopSetting);

                try {
                    final List<String> errors = loopValue.validateValue(loopSetting);
                    for (final String loopError : errors) {
                        errorStrings.add(errorPrefix + loopError);
                    }
                } catch (Exception e) {
                    LOGGER.error("unexpected error during validate value for " + errorPrefix + ", error: " + e.getMessage(),e);
                }
            }
        }

        return errorStrings;
    }

    public StoredValue readSetting(final PwmSetting setting) {
        if (setting.getCategory().getType() == PwmSetting.Category.Type.PROFILE) {
            throw new IllegalStateException("cannot read setting key " + setting.getKey() + " as non-group setting");
        }

        return readSetting(setting,null);
    }

    public StoredValue readSetting(final PwmSetting setting, final String profileID) {
        domModifyLock.readLock().lock();
        try {
            final XPathExpression xp = XPathBuilder.xpathForSetting(setting, profileID);
            final Element settingElement = (Element)xp.evaluateFirst(document);

            if (settingElement == null) {
                return defaultValue(setting, getTemplate());
            }

            if (settingElement.getChild("default") != null) {
                return defaultValue(setting, getTemplate());
            }

            try {
                return ValueFactory.fromXmlValues(setting, settingElement, getKey());
            } catch (PwmOperationalException e) {
                final String errorMsg = "unexpected error reading setting '" + setting.getKey() + "' profile '" + profileID + "', error: " + e.getMessage();
                throw new IllegalStateException(errorMsg);
            }
        } finally {
            domModifyLock.readLock().unlock();
        }
    }

    public void writeLocaleBundleMap(final String bundleName, final String keyName, final Map<String,String> localeMap) {
        ResourceBundle theBundle = null;
        for (final PwmConstants.EDITABLE_LOCALE_BUNDLES bundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) {
            if (bundle.getTheClass().getName().equals(bundleName)) {
                theBundle = ResourceBundle.getBundle(bundleName);
            }
        }

        if (theBundle == null) {
            LOGGER.info("ignoring unknown locale bundle for bundle=" + bundleName + ", key=" + keyName);
            return;
        }

        if (theBundle.getString(keyName) == null) {
            LOGGER.info("ignoring unknown key for bundle=" + bundleName + ", key=" + keyName);
            return;
        }


        resetLocaleBundleMap(bundleName, keyName);
        if (localeMap == null || localeMap.isEmpty()) {
            LOGGER.info("cleared locale bundle map for bundle=" + bundleName + ", key=" + keyName);
            return;
        }

        preModifyActions();
        changeLog.updateChangeLog(bundleName, keyName, localeMap);
        domModifyLock.writeLock().lock();
        final Element localeBundleElement = new Element("localeBundle");
        localeBundleElement.setAttribute("bundle",bundleName);
        localeBundleElement.setAttribute("key",keyName);
        for (final String locale : localeMap.keySet()) {
            final Element valueElement = new Element("value");
            if (locale != null && locale.length() > 0) {
                valueElement.setAttribute("locale",locale);
            }
            valueElement.setContent(new CDATA(localeMap.get(locale)));
            localeBundleElement.addContent(valueElement);
        }
        document.getRootElement().addContent(localeBundleElement);
    }


    public void writeSetting(final PwmSetting setting, final StoredValue value) {
        writeSetting(setting, null, value);
    }

    public void writeSetting(
            final PwmSetting setting,
            final String profileID,
            final StoredValue value
    ) {
        final Class correctClass = setting.getSyntax().getStoredValueImpl();

        if (profileID != null && setting.getCategory().getType() != PwmSetting.Category.Type.PROFILE) {
            throw new IllegalArgumentException("cannot specify profile for non-profile setting");
        }

        if (!correctClass.equals(value.getClass())) {
            throw new IllegalArgumentException("value must be of class " + correctClass.getName() + " for setting " + setting.toString());
        }

        preModifyActions();
        changeLog.updateChangeLog(setting, profileID, value);
        resetSettingInternal(setting, profileID);
        domModifyLock.writeLock().lock();
        try {
            final Element settingElement = new Element("setting");
            settingElement.setAttribute("key", setting.getKey());
            settingElement.setAttribute("syntax", setting.getSyntax().toString());
            if (profileID != null && profileID.length() > 0) {
                settingElement.setAttribute("profile", profileID);
            }

            if (setting_writeLabels) {
                final Element labelElement = new Element("label");
                labelElement.addContent(setting.getLabel(PwmConstants.DEFAULT_LOCALE));
                settingElement.addContent(labelElement);
            }

            if (setting.getSyntax() == PwmSettingSyntax.PASSWORD) {
                final List<Element> valueElements = ((PasswordValue)value).toXmlValues("value", getKey());
                settingElement.addContent(new Comment("Note: This value is encrypted and can not be edited directly."));
                settingElement.addContent(new Comment("Please use the Configuration Manager GUI to modify this value."));
                settingElement.addContent(valueElements);
            } else {
                settingElement.addContent(value.toXmlValues("value"));
            }

            document.getRootElement().addContent(settingElement);
        } finally {
            domModifyLock.writeLock().unlock();
        }
    }

    public String settingChecksum() throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("PwmSettingsChecksum");

        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getSyntax() != PwmSettingSyntax.PROFILE && setting.getCategory().getType() != PwmSetting.Category.Type.PROFILE) {
                if (!isDefaultValue(setting,null)) {
                    final StoredValue value = readSetting(setting);
                    sb.append(setting.getKey()).append(Helper.getGson().toJson(value));
                }
            }
        }

        for (final PwmSetting.Category category : PwmSetting.Category.values()) {
            if (category.getType() == PwmSetting.Category.Type.PROFILE) {
                for (final String profileID : this.profilesForSetting(category.getProfileSetting())) {
                    for (final PwmSetting profileSetting : PwmSetting.getSettings(category)) {
                        if (!isDefaultValue(profileSetting, profileID)) {
                            final StoredValue value = readSetting(profileSetting, profileID);
                            sb.append(profileSetting.getKey()).append(profileID).append(Helper.getGson().toJson(value));
                        }
                    }
                }
            }
        }

        sb.append(modifyTime);
        sb.append(createTime);

        final InputStream is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        return Helper.md5sum(is);
    }


    private void preModifyActions() {
        if (locked) {
            throw new UnsupportedOperationException("StoredConfiguration is locked and cannot be modifed");
        }
        modifyTime = new Date();
    }

    public String getKey() {
        return CONFIG_ATTR_DATETIME_FORMAT.format(createTime) + StoredConfiguration.class.getSimpleName();
    }
// -------------------------- INNER CLASSES --------------------------

    public void setPassword(final String password) {
        final String salt = BCrypt.gensalt();
        final String passwordHash = BCrypt.hashpw(password,salt);
        this.writeConfigProperty(ConfigProperty.PROPERTY_KEY_PASSWORD_HASH, passwordHash);
    }

    public boolean verifyPassword(final String password) {
        if (!hasPassword()) {
            return false;
        }
        final String passwordHash = this.readConfigProperty(ConfigProperty.PROPERTY_KEY_PASSWORD_HASH);
        return BCrypt.checkpw(password,passwordHash);
    }

    public boolean hasPassword() {
        final String passwordHash = this.readConfigProperty(ConfigProperty.PROPERTY_KEY_PASSWORD_HASH);
        return passwordHash != null && passwordHash.length() > 0;
    }

    private static abstract class XPathBuilder {
        private static XPathExpression xpathForLocaleBundleSetting(final String bundleName, final String keyName) {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//localeBundle[@bundle=\"" + bundleName + "\"][@key=\"" + keyName + "\"]";
            return xpfac.compile(xpathString);
        }

        private static XPathExpression xpathForSetting(final PwmSetting setting, final String profileID) {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            if (profileID == null || profileID.length() < 1) {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][(not (@profile)) or @profile=\"\"]";
            } else {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][@profile=\"" + profileID + "\"]";
            }

            return xpfac.compile(xpathString);
        }

        private static XPathExpression xpathForAppProperty(final AppProperty appProperty) {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + appProperty.getKey() + "\"]";
            return xpfac.compile(xpathString);
        }

        private static XPathExpression xpathForAppProperties() {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]";
            return xpfac.compile(xpathString);
        }

        private static XPathExpression xpathForConfigProperty(final ConfigProperty configProperty) {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\" or (not (@" + XML_ATTRIBUTE_TYPE + "))]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + configProperty.getKey() + "\"]";
            return xpfac.compile(xpathString);
        }

        private static XPathExpression xpathForConfigProperties() {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
            return xpfac.compile(xpathString);
        }
    }

    private static void fixupMandatoryElements(final Document document, final StoredConfiguration storedConfiguration) {
        final Element rootElement = document.getRootElement();

        {
            final XPathExpression commentXPath = XPathFactory.instance().compile("//comment()[1]");
            final Comment existingComment = (Comment)commentXPath.evaluateFirst(rootElement);
            if (existingComment != null) {
                existingComment.detach();
            }
            final Comment comment = new Comment(generateCommentText());
            rootElement.addContent(0,comment);
        }

        rootElement.setAttribute("pwmVersion", PwmConstants.BUILD_VERSION);
        rootElement.setAttribute("pwmBuild", PwmConstants.BUILD_NUMBER);
        rootElement.setAttribute("pwmBuildType", PwmConstants.BUILD_TYPE);
        rootElement.setAttribute("createTime", CONFIG_ATTR_DATETIME_FORMAT.format(storedConfiguration.createTime));
        rootElement.setAttribute("modifyTime", CONFIG_ATTR_DATETIME_FORMAT.format(storedConfiguration.modifyTime));
        rootElement.setAttribute("xmlVersion", XML_FORMAT_VERSION);
    }

    private static String generateCommentText() {
        final StringBuilder commentText = new StringBuilder();
        commentText.append("\t\t").append(" ").append("\n");
        commentText.append("\t\t").append("This configuration file has been auto-generated by the Password Self Service application.").append("\n");
        commentText.append("\t\t").append("").append("\n");
        commentText.append("\t\t").append("WARNING: This configuration file contains sensitive security information, please handle with care!").append("\n");
        commentText.append("\t\t").append("").append("\n");
        commentText.append("\t\t").append("NOTICE: This file is encoded as UTF-8.  Do not save or edit this file with an editor that does not").append("\n");
        commentText.append("\t\t").append("        support UTF-8 encoding.").append("\n");
        commentText.append("\t\t").append("").append("\n");
        commentText.append("\t\t").append("To edit this file:").append("\n");
        commentText.append("\t\t").append("   or 1. Edit this file directly by hand, syntax is mostly self-explanatory.").append("\n");
        commentText.append("\t\t").append("   or 2. Set the property 'configIsEditable' to 'true', note that anyone with access to ").append("\n");
        commentText.append("\t\t").append("         the application url will be able to edit the configuration while this property is true.").append("\n");
        commentText.append("\t\t").append("").append("\n");
        return commentText.toString();
    }


    private static class ChangeRecord {
        private RecordType recordType;
        private Object recordID;
        private String profileID;

        enum RecordType {
            SETTING,
            APP_PROPERTY,
            LOCALE_BUNDLE,
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ChangeRecord that = (ChangeRecord) o;

            if (profileID != null ? !profileID.equals(that.profileID) : that.profileID != null) return false;
            if (recordID != null ? !recordID.equals(that.recordID) : that.recordID != null) return false;
            if (recordType != that.recordType) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = recordType != null ? recordType.hashCode() : 0;
            result = 31 * result + (recordID != null ? recordID.hashCode() : 0);
            result = 31 * result + (profileID != null ? profileID.hashCode() : 0);
            return result;
        }
    }

    public String changeLogAsDebugString(final Locale locale) {
        return changeLog.changeLogAsDebugString(locale);
    }

    public boolean isModified() {
        return changeLog.isModified();
    }

    private class ChangeLog {
        /* values contain the _original_ toJson version of the value. */
        private Map<ChangeRecord,String> changeLog = new LinkedHashMap<ChangeRecord, String>();

        public boolean isModified() {
            return !changeLog.isEmpty();
        }

        public String changeLogAsDebugString(final Locale locale) {
            final Map<String,String> outputMap = new TreeMap<String,String>();
            for (final ChangeRecord changeRecord : changeLog.keySet()) {
                switch (changeRecord.recordType) {
                    case SETTING: {
                        final StoredValue currentValue = readSetting((PwmSetting)changeRecord.recordID, changeRecord.profileID);
                        final PwmSetting pwmSetting = (PwmSetting)changeRecord.recordID;
                        final StringBuilder keyName = new StringBuilder();
                        keyName.append(pwmSetting.getCategory().getLabel(locale));
                        keyName.append(" -> ");
                        keyName.append(pwmSetting.getLabel(locale));
                        if (changeRecord.profileID != null && changeRecord.profileID.length() > 0) {
                            keyName.append(" -> ");
                            keyName.append(changeRecord.profileID);
                        }
                        final String debugValue = currentValue.toDebugString();
                        outputMap.put(keyName.toString(),debugValue);
                    }
                    break;

                    case LOCALE_BUNDLE: {
                        final String key = (String)changeRecord.recordID;
                        final String bundleName = key.split("!")[0];
                        final String keys = key.split("!")[1];
                        final Map<String,String> currentValue = readLocaleBundleMap(bundleName,keys);
                        final String debugValue = Helper.getGson().toJson(currentValue);
                        outputMap.put("LocaleBundle" + " -> " + bundleName + " " + keys,debugValue);
                    }
                    break;

                    case APP_PROPERTY: {
                        final AppProperty appProperty = (AppProperty)changeRecord.recordID;
                        final String debugValue = readAppProperty(appProperty);
                        outputMap.put("AppProperty" + " -> " + appProperty.getKey(),debugValue);
                    }
                    break;
                }
            }
            final StringBuilder output = new StringBuilder();
            if (outputMap.isEmpty()) {
                output.append("No changes.");
            } else {
                for (final String keyName : outputMap.keySet()) {
                    final String value = outputMap.get(keyName);
                    output.append(keyName);
                    output.append("\n");
                    output.append(" Value: ");
                    output.append(value);
                    output.append("\n");
                }
            }
            return output.toString();
        }

        public void updateChangeLog(final AppProperty appProperty, final String newValue) {
            final String currentJsonValue = readAppProperty(appProperty);
            final ChangeRecord changeRecord = new ChangeRecord();
            changeRecord.profileID = null;
            changeRecord.recordType = ChangeRecord.RecordType.APP_PROPERTY;
            changeRecord.recordID = appProperty;
            updateChangeLog(changeRecord,currentJsonValue,newValue);
        }

        public void updateChangeLog(final String bundleName, final String keyName, final Map<String,String> localeValueMap) {
            final String key = bundleName + "!" + keyName;
            final Map<String,String> currentValue = readLocaleBundleMap(bundleName, keyName);
            final String currentJsonValue = Helper.getGson().toJson(currentValue);
            final String newJsonValue = Helper.getGson().toJson(localeValueMap);
            final ChangeRecord changeRecord = new ChangeRecord();
            changeRecord.profileID = null;
            changeRecord.recordType = ChangeRecord.RecordType.LOCALE_BUNDLE;
            changeRecord.recordID = key;
            updateChangeLog(changeRecord,currentJsonValue,newJsonValue);
        }

        public void updateChangeLog(final PwmSetting setting, final String profileID, final StoredValue newValue) {
            final StoredValue currentValue = readSetting(setting, profileID);
            final String currentJsonValue = Helper.getGson().toJson(currentValue);
            final String newJsonValue = Helper.getGson().toJson(newValue);
            final ChangeRecord changeRecord = new ChangeRecord();
            changeRecord.profileID = profileID;
            changeRecord.recordType = ChangeRecord.RecordType.SETTING;
            changeRecord.recordID = setting;
            updateChangeLog(changeRecord,currentJsonValue,newJsonValue);
        }

        public void updateChangeLog(final ChangeRecord changeRecord, final String currentValueString, final String newValueString) {
            if (changeLog.containsKey(changeRecord)) {
                final String currentRecord = changeLog.get(changeRecord);

                if (currentRecord == null && newValueString == null) {
                    changeLog.remove(changeRecord);
                } else if (currentRecord != null && currentRecord.equals(newValueString)) {
                    changeLog.remove(changeRecord);
                }
            } else {
                changeLog.put(changeRecord,currentValueString);
            }
        }
    }

    public static void validateXmlSchema(final String xmlDocument)
            throws PwmUnrecoverableException
    {
        return;
        /*
        try {
            final InputStream xsdInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/StoredConfiguration.xsd");
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(new StreamSource(xsdInputStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlDocument)));
        } catch (Exception e) {
            final String errorMsg = "error while validating setting file schema definition: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg));
        }
        */
    }
}
