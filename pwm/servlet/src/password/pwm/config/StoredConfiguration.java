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

import com.google.gson.Gson;
import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.BCrypt;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.*;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class StoredConfiguration implements Serializable, Cloneable {
// ------------------------------ FIELDS ------------------------------

    public static final String PROPERTY_KEY_SETTING_CHECKSUM = "settingsChecksum";
    public static final String PROPERTY_KEY_CONFIG_IS_EDITABLE = "configIsEditable";
    public static final String PROPERTY_KEY_CONFIG_EPOCH = "configEpoch";
    public static final String PROPERTY_KEY_TEMPLATE = "configTemplate";
    public static final String PROPERTY_KEY_NOTES = "notes";
    public static final String PROPERTY_KEY_PASSWORD_HASH = "configPasswordHash";

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StoredConfiguration.class);
    private static final String XML_FORMAT_VERSION = "2";

    private Date createTime = new Date();
    private Date modifyTime = new Date();
    private Map<PwmSetting, StoredValue> settingMap = new LinkedHashMap<PwmSetting, StoredValue>();
    private Map<String, String> propertyMap = new LinkedHashMap<String, String>();
    private Map<String, Map<String,Map<String,String>>> localizationMap = new LinkedHashMap<String, Map<String,Map<String, String>>>();

    private boolean locked = false;

// -------------------------- STATIC METHODS --------------------------

    public static StoredConfiguration getDefaultConfiguration() {
        return new StoredConfiguration();
    }

    public static StoredConfiguration fromXml(final String xmlData)
            throws PwmUnrecoverableException
    {
        return XmlConverter.fromXml(xmlData);
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public Date getModifyTime() {
        return modifyTime;
    }

// ------------------------ CANONICAL METHODS ------------------------

    @Override
    public Object clone() throws CloneNotSupportedException {
        final StoredConfiguration clonedConfig = (StoredConfiguration) super.clone();
        clonedConfig.createTime = this.createTime;
        clonedConfig.modifyTime = this.modifyTime;
        clonedConfig.settingMap = new LinkedHashMap<PwmSetting, StoredValue>();
        clonedConfig.settingMap.putAll(this.settingMap);
        clonedConfig.propertyMap = new LinkedHashMap<String, String>();
        clonedConfig.propertyMap.putAll(this.propertyMap);
        clonedConfig.localizationMap = new LinkedHashMap<String, Map<String,Map<String, String>>>();
        for (final String middleKey : this.localizationMap.keySet()) { //deep copy of nested map, oy vey
            final Map<String,Map<String,String>> newMiddleMap = new LinkedHashMap<String,Map<String,String>>();
            for (final String bottomKey : this.localizationMap.get(middleKey).keySet()) {
                final Map<String,String> newBottomMap = new LinkedHashMap<String,String>();
                newBottomMap.putAll(this.localizationMap.get(middleKey).get(bottomKey));
                newMiddleMap.put(bottomKey,newBottomMap);
            }
            clonedConfig.localizationMap.put(middleKey,newMiddleMap);
        }
        clonedConfig.locked = false;
        return clonedConfig;
    }

    public String toString() {
        return toString(false);
    }

// -------------------------- OTHER METHODS --------------------------

    public boolean hasBeenModified() {
        boolean hasBeenModified = false;
        if (!settingMap.isEmpty()) {
            hasBeenModified = true;
        }
        if (!localizationMap.isEmpty()) {
            hasBeenModified = true;
        }

        final String notes = this.readProperty(StoredConfiguration.PROPERTY_KEY_NOTES);
        if (notes != null && notes.length() > 0) {
            hasBeenModified = true;
        }

        return hasBeenModified;
    }

    public String readProperty(final String propertyName) {
        return propertyMap.get(propertyName);
    }

    public void lock() {
        settingMap = Collections.unmodifiableMap(settingMap);
        propertyMap = Collections.unmodifiableMap(propertyMap);
        localizationMap = Collections.unmodifiableMap(localizationMap);
        locked = true;
    }

    public Map<String,String> readLocaleBundleMap(final String bundleName, final String keyName) {
        final Map<String, Map<String,String>> keyMap = localizationMap.get(bundleName);
        if (keyMap == null) {
            return Collections.emptyMap();
        }
        if (keyMap.get(keyName) == null) {
            return Collections.emptyMap();
        }
        return keyMap.get(keyName);
    }

    public Set<String> readPropertyKeys() {
        return Collections.unmodifiableSet(propertyMap.keySet());
    }

    public void resetLocaleBundleMap(final String bundleName, final String keyName) {
        final Map<String, Map<String,String>> keyMap = localizationMap.get(bundleName);
        if (keyMap == null) {
            return;
        }
        keyMap.remove(keyName);
    }

    public void resetSetting(final PwmSetting setting) {
        preModifyActions();
        settingMap.remove(setting);
    }

    public String settingChecksum() throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("PwmSettingsChecksum");

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            if (!isDefaultValue(loopSetting)) {
                sb.append(loopSetting.getKey());
                sb.append("=");
                sb.append(settingMap.get(loopSetting));
            }
        }

        sb.append(modifyTime);
        sb.append(createTime);

        final InputStream is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        return Helper.md5sum(is);
    }

    public boolean isDefaultValue(final PwmSetting setting) {
        if (!settingMap.containsKey(setting)) {
            return true;
        }

        final StoredValue defaultValue = defaultValue(setting, getTemplate());
        if (defaultValue == null) {
            return settingMap.get(setting) == null;
        }


        final String defaultJson = new Gson().toJson(defaultValue.toNativeObject());
        final String currentJson = new Gson().toJson(settingMap.get(setting).toNativeObject());
        return defaultJson.equals(currentJson);
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
        final String propertyValue = propertyMap.get(PROPERTY_KEY_TEMPLATE);
        try {
            return PwmSetting.Template.valueOf(propertyValue);
        } catch (IllegalArgumentException e) {
            return PwmSetting.Template.DEFAULT;
        } catch (NullPointerException e) {
            return PwmSetting.Template.DEFAULT;
        }
    }

    public void setTemplate(PwmSetting.Template template) {
        propertyMap.put(PROPERTY_KEY_TEMPLATE,template.toString());
    }

    public String toString(final PwmSetting setting) {
        final StringBuilder outputString = new StringBuilder();
        outputString.append(setting.getKey()).append("=");

        outputString.append(settingMap.get(setting).toDebugString());
        return outputString.toString();
    }

    public String toString(final boolean linebreaks) {
        final StringBuilder outputString = new StringBuilder();

        final Set<PwmSetting> unmodifiedSettings = new HashSet<PwmSetting>(Arrays.asList(PwmSetting.values()));

        for (final Iterator<PwmSetting> settingIter = this.settingMap.keySet().iterator(); settingIter.hasNext();) {
            final PwmSetting setting = settingIter.next();
            outputString.append(toString(setting));
            outputString.append(settingIter.hasNext() ? linebreaks ? "\n" : ", " : "");
            unmodifiedSettings.remove(setting);
        }

        return outputString.toString();
    }

    public String toXml()
            throws IOException
    {
        return XmlConverter.toXml(this);
    }

    public List<String> validateValues() {
        final List<String> errorStrings = new ArrayList<String>();

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            final StringBuilder errorPrefix = new StringBuilder();
            errorPrefix.append(loopSetting.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
            errorPrefix.append("-");
            errorPrefix.append(loopSetting.getLabel(PwmConstants.DEFAULT_LOCALE));
            errorPrefix.append(" ");

            final StoredValue loopValue = readSetting(loopSetting);
            final List<String> errors;
            try {
                errors = loopValue.validateValue(loopSetting);
                for (final String loopError : errors) {
                    errorStrings.add(errorPrefix + loopError);
                }
            } catch (Exception e) {
                LOGGER.error("unexpected error during validate value for " + errorPrefix + ", error: " + e.getMessage(),e);
            }
        }

        return errorStrings;
    }

    public StoredValue readSetting(final PwmSetting setting) {
        return settingMap.containsKey(setting) ? settingMap.get(setting) : defaultValue(setting, getTemplate());
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

        Map<String, Map<String,String>> keyMap = localizationMap.get(bundleName);
        if (keyMap == null) {
            keyMap = new LinkedHashMap<String, Map<String, String>>();
            localizationMap.put(bundleName,keyMap);
        }
        keyMap.put(keyName,new LinkedHashMap<String, String>(localeMap));
    }

    public void writeLocalizedSetting(final PwmSetting setting, final Map<String, String> values) {
        preModifyActions();
        if (PwmSettingSyntax.LOCALIZED_STRING != setting.getSyntax() && PwmSettingSyntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write value to non-LOCALIZED_STRING or LOCALIZED_TEXT_AREA setting: " + setting.toString());
        }

        settingMap.put(setting, new LocalizedStringValue(values));
    }

    public void writeProperty(final String propertyName, final String propertyValue) {
        preModifyActions();
        if (propertyValue == null) {
            propertyMap.remove(propertyName);
        } else {
            propertyMap.put(propertyName, propertyValue);
        }
    }

    public void writeSetting(final PwmSetting setting, final StoredValue value) {
        preModifyActions();
        final Class correctClass = setting.getSyntax().getStoredValueImpl();
        if (!correctClass.equals(value.getClass())) {
            throw new IllegalArgumentException("value must be of class " + correctClass.getName() + " for setting " + setting.toString());
        }
        settingMap.put(setting,value);
    }


    private void preModifyActions() {
        if (locked) {
            throw new UnsupportedOperationException("StoredConfiguration is locked and cannot be modifed");
        }
        modifyTime = new Date();
    }


// -------------------------- INNER CLASSES --------------------------

    private static class XmlConverter {
        private static String toXml(final StoredConfiguration storedConfiguration)
                throws IOException {
            final Element pwmConfigElement = new Element("PwmConfiguration");
            final StringBuilder commentText = new StringBuilder();
            commentText.append("\t\t").append(" ").append("\n");
            commentText.append("\t\t").append("This configuration file has been auto-generated by the Password Self Service application.").append("\n");
            commentText.append("\t\t").append("").append("\n");
            commentText.append("\t\t").append("WARNING: This configuration file contains sensitive security information, please handle with care!").append("\n");
            commentText.append("\t\t").append("NOTICE: This file is encoded as UTF-8.  Do not save or edit this file with an editor that does not").append("\n");
            commentText.append("\t\t").append("        support UTF-8 encoding.").append("\n");
            commentText.append("\t\t").append(" ").append("\n");
            commentText.append("\t\t").append("To edit this file:").append("\n");
            commentText.append("\t\t").append("   or 1. Edit this file directly by hand, syntax is mostly self-explanatory.").append("\n");
            commentText.append("\t\t").append("   or 2. Set the property 'configIsEditable' to 'true', note that anyone with access to ").append("\n");
            commentText.append("\t\t").append("         the application url will be able to edit the configuration while this property is true.").append("\n");
            commentText.append("\t\t").append(" ").append("\n");
            pwmConfigElement.addContent(new Comment(commentText.toString()));

            { // write properties section
                final Element propertiesElement = new Element("properties");
                storedConfiguration.propertyMap.put(PROPERTY_KEY_SETTING_CHECKSUM, storedConfiguration.settingChecksum());
                for (final String key : storedConfiguration.propertyMap.keySet()) {
                    final Element propertyElement = new Element("property");
                    propertyElement.setAttribute("key", key);
                    propertyElement.addContent(storedConfiguration.propertyMap.get(key));
                    propertiesElement.addContent(propertyElement);
                }
                pwmConfigElement.addContent(propertiesElement);
            }

            final Element settingsElement = new Element("settings");
            for (final PwmSetting setting : PwmSetting.values()) {
                final Element settingElement = new Element("setting");
                settingElement.setAttribute("key", setting.getKey());
                settingElement.setAttribute("syntax", setting.getSyntax().toString());

                {
                    final Element labelElement = new Element("label");
                    labelElement.addContent(setting.getLabel(PwmConstants.DEFAULT_LOCALE));
                    settingElement.addContent(labelElement);
                }

                if (storedConfiguration.isDefaultValue(setting)) {
                    settingElement.addContent(new Element("default"));
                } else {
                    final List<Element> valueElements;
                    if (setting.getSyntax() == PwmSettingSyntax.PASSWORD) {
                        final String key = PwmConstants.DEFAULT_DATETIME_FORMAT.format(storedConfiguration.createTime) + StoredConfiguration.class.getSimpleName();
                        valueElements = ((PasswordValue) storedConfiguration.settingMap.get(setting)).toXmlValues("value", key);
                        settingElement.addContent(new Comment("Note: This value is encrypted and can not be edited directly."));
                        settingElement.addContent(new Comment("Please use the Configuration Manager GUI to modify this value."));
                    } else {
                        valueElements = storedConfiguration.settingMap.get(setting).toXmlValues("value");
                    }
                    for (final Element loopValueElement : valueElements) {
                        settingElement.addContent(loopValueElement);
                    }
                }

                settingsElement.addContent(settingElement);
            }
            pwmConfigElement.addContent(settingsElement);

            if (!storedConfiguration.localizationMap.isEmpty()) {  // write localizedStrings
                for (final String bundleKey : storedConfiguration.localizationMap.keySet()) {
                    for (final String keyName : storedConfiguration.localizationMap.get(bundleKey).keySet()) {
                        final Map<String,String> localeMap = storedConfiguration.localizationMap.get(bundleKey).get(keyName);
                        if (!localeMap.isEmpty()) {
                            final Element localeBundleElement = new Element("localeBundle");
                            localeBundleElement.setAttribute("bundle",bundleKey);
                            localeBundleElement.setAttribute("key",keyName);
                            for (final String locale : localeMap.keySet()) {
                                final Element valueElement = new Element("value");
                                if (locale != null && locale.length() > 0) {
                                    valueElement.setAttribute("locale",locale);
                                }
                                valueElement.setContent(new CDATA(localeMap.get(locale)));
                                localeBundleElement.addContent(valueElement);
                            }
                            pwmConfigElement.addContent(localeBundleElement);
                        }
                    }
                }
            }

            pwmConfigElement.setAttribute("pwmVersion", PwmConstants.PWM_VERSION);
            pwmConfigElement.setAttribute("pwmBuild", PwmConstants.BUILD_NUMBER);
            pwmConfigElement.setAttribute("pwmBuildType", PwmConstants.BUILD_TYPE);
            pwmConfigElement.setAttribute("createTime", PwmConstants.DEFAULT_DATETIME_FORMAT.format(storedConfiguration.createTime));
            pwmConfigElement.setAttribute("modifyTime", PwmConstants.DEFAULT_DATETIME_FORMAT.format(storedConfiguration.modifyTime));
            pwmConfigElement.setAttribute("xmlVersion", XML_FORMAT_VERSION);

            final Format format = Format.getPrettyFormat();
            format.setEncoding("UTF-8");
            final XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(format);
            return outputter.outputString(new Document(pwmConfigElement));
        }

        private static StoredConfiguration fromXml(final String xmlData)
                throws PwmUnrecoverableException
        {
            final SAXBuilder builder = new SAXBuilder();
            final Reader in = new StringReader(xmlData);
            final Document inputDocument;
            try {
                inputDocument = builder.build(in);
            } catch (Exception e) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"error parsing xml data: " + e.getMessage()));
            }

            final Set<PwmSetting> seenSettings = new HashSet<PwmSetting>();

            final StoredConfiguration newConfiguration = StoredConfiguration.getDefaultConfiguration();
            String currentSettingName = "";
            try {
                final Element rootElement = inputDocument.getRootElement();
                final String createTimeString = rootElement.getAttributeValue("createTime");
                if (createTimeString == null) {
                    throw new IllegalArgumentException("missing createTime timestamp");
                }
                final String modifyTimeString = rootElement.getAttributeValue("modifyTime");
                newConfiguration.createTime = PwmConstants.DEFAULT_DATETIME_FORMAT.parse(createTimeString);
                final Element settingsElement = rootElement.getChild("settings");
                final List<Element> settingElements = settingsElement.getChildren("setting");
                for (final Element settingElement : settingElements) {
                    final String keyName = settingElement.getAttributeValue("key");
                    currentSettingName = keyName;
                    final PwmSetting pwmSetting = PwmSetting.forKey(keyName);
                    seenSettings.add(pwmSetting);

                    if (pwmSetting == null) {
                        LOGGER.info("unknown setting key while parsing input configuration: " + keyName);
                    } else {
                        if (settingElement.getChild("default") == null) {
                            final String key = PwmConstants.DEFAULT_DATETIME_FORMAT.format(newConfiguration.createTime) + StoredConfiguration.class.getSimpleName();
                            final StoredValue storedValue = ValueFactory.fromXmlValues(pwmSetting, settingElement, key);
                            newConfiguration.writeSetting(pwmSetting, storedValue);
                        }
                    }
                }

                final Element propertiesElement = rootElement.getChild("properties");
                if (propertiesElement != null) {
                    for (final Element element : propertiesElement.getChildren("property")) {
                        final String key = element.getAttributeValue("key");
                        final String value = element.getText();
                        newConfiguration.propertyMap.put(key, value);
                    }
                }

                for (final Object loopElementObj : rootElement.getChildren("localeBundle")) {
                    final Element localeBundleElement = (Element) loopElementObj;
                    final String bundle = localeBundleElement.getAttributeValue("bundle");
                    final String key = localeBundleElement.getAttributeValue("key");
                    final Map<String,String> bundleMap = new LinkedHashMap<String, String>();
                    for (final Element valueElement : localeBundleElement.getChildren("value")) {
                        final String localeStrValue = valueElement.getAttributeValue("locale");
                        bundleMap.put(localeStrValue == null ? "" : localeStrValue, valueElement.getText());
                    }
                    if (!bundleMap.isEmpty()) {
                        newConfiguration.writeLocaleBundleMap(bundle,key,bundleMap);
                    }
                }

                if (modifyTimeString == null) {
                    throw new IllegalArgumentException("missing modifyTime timestamp");
                }
                newConfiguration.modifyTime = PwmConstants.DEFAULT_DATETIME_FORMAT.parse(modifyTimeString);

                for (final PwmSetting setting : PwmSetting.values()) {
                    if (!seenSettings.contains(setting)) {
                        LOGGER.info("missing setting key while parsing input configuration: " + setting.getKey() + ", will use default value");
                    }
                    if (setting.getSyntax() == PwmSettingSyntax.EMAIL) { // for reading old email config values.
                        Element stubSettingElement = new Element("setting");
                        settingsElement.addContent(stubSettingElement);
                        stubSettingElement.setAttribute("key",setting.getKey());
                        final StoredValue storedValue = ValueFactory.fromXmlValues(setting, stubSettingElement, null);
                        if (storedValue.toNativeObject() != null && !((Map<String,EmailItemBean>)storedValue.toNativeObject()).isEmpty()) {
                            newConfiguration.writeSetting(setting, storedValue);
                        }
                    }
                }
            } catch (PwmOperationalException e) {
                final String errorMsg = "error reading configuration file format, setting=" + currentSettingName + ", error=" + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
                throw new PwmUnrecoverableException(errorInfo);
            } catch (Exception e) {
                final String errorMsg = "error reading configuration file format, setting=" + currentSettingName + ", error=" + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
                throw new PwmUnrecoverableException(errorInfo);
            }

            LOGGER.debug("successfully loaded configuration with " + newConfiguration.settingMap.size() + " setting values, epoch " + newConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
            return newConfiguration;
        }
    }

    public void setPassword(final String password) {
        final String salt = BCrypt.gensalt();
        final String passwordHash = BCrypt.hashpw(password,salt);
        this.writeProperty(StoredConfiguration.PROPERTY_KEY_PASSWORD_HASH,passwordHash);
    }

    public boolean verifyPassword(final String password) {
        if (!hasPassword()) {
            return false;
        }
        final String passwordHash = this.readProperty(StoredConfiguration.PROPERTY_KEY_PASSWORD_HASH);
        return BCrypt.checkpw(password,passwordHash);
    }

    public boolean hasPassword() {
        final String passwordHash = this.readProperty(StoredConfiguration.PROPERTY_KEY_PASSWORD_HASH);
        return passwordHash != null && passwordHash.length() > 0;
    }
}
