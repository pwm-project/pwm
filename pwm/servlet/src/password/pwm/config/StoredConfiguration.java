/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import org.jdom.CDATA;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import password.pwm.PwmConstants;
import password.pwm.util.Base64Util;
import password.pwm.util.PwmLogger;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jason D. Rivard
 */
public class StoredConfiguration implements Serializable {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StoredConfiguration.class);

    private static final DateFormat STORED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private static final String XML_FORMAT_VERSION = "2";

    private Date createTime = new Date();
    private Date modifyTime = new Date();
    private Map<PwmSetting,String> settingMap = new HashMap<PwmSetting,String>();
    private boolean locked = false;

    static {
        STORED_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Zulu"));
    }

    public void lock() {
        locked = true;
        settingMap = Collections.unmodifiableMap(settingMap);
    }

    public Date getModifyTime() {
        return modifyTime;
    }

    public static StoredConfiguration getDefaultConfiguration() {
        final StoredConfiguration config = new StoredConfiguration();
        for (final PwmSetting loopSetting : PwmSetting.values() ) {
            final String defaultValue = loopSetting.getDefaultValue();
            switch (loopSetting.getSyntax()) {
                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA:
                    config.writeLocalizedSetting(loopSetting, Collections.singletonMap("",defaultValue));
                    break;

                case LOCALIZED_STRING_ARRAY:
                    config.writeLocalizedStringArraySetting(loopSetting, Collections.singletonMap("",Collections.singletonList(defaultValue)));
                    if (defaultValue != null) {
                        final String[] values = defaultValue.split(";;;");
                        final List<String> valuesAsList = Arrays.asList(values);
                        config.writeLocalizedStringArraySetting(loopSetting,Collections.singletonMap("",valuesAsList));
                    }
                    break;

                case STRING_ARRAY:
                    config.writeStringArraySetting(loopSetting,Collections.singletonList(defaultValue));
                    if (defaultValue != null) {
                        final String[] values = defaultValue.split(";;;");
                        final List<String> valuesAsList = Arrays.asList(values);
                        config.writeStringArraySetting(loopSetting,valuesAsList);
                    }
                    break;

                default:
                    config.writeSetting(loopSetting, loopSetting.getDefaultValue());
            }
        }
        return config;
    }

    public String readSetting(final PwmSetting setting) {
        switch (setting.getSyntax()) {
            case STRING:
            case BOOLEAN:
            case NUMERIC:
            case PASSWORD:
                break;

            default:
                throw new IllegalArgumentException("may not read setting as string: " + setting.toString());
        }
        return settingMap.get(setting);
    }

    public void writeSetting(final PwmSetting setting, final String value) {
        checkModifyability();
        switch (setting.getSyntax()) {
            case STRING:
            case BOOLEAN:
            case NUMERIC:
            case PASSWORD:
                break;

            default:
                throw new IllegalArgumentException("may not write setting as string: " + setting.toString());
        }
        settingMap.put(setting, value);
    }

    public Map<String,String> readLocalizedStringSetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.LOCALIZED_STRING != setting.getSyntax() && PwmSetting.Syntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting: " + setting.toString());
        }

        final String stringValue = settingMap.get(setting);
        return JSONConversions.stringToMap(stringValue);
    }

    public void writeLocalizedSetting(final PwmSetting setting, final Map<String,String> values) {
        checkModifyability();
        if (PwmSetting.Syntax.LOCALIZED_STRING != setting.getSyntax() && PwmSetting.Syntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write value to non-LOCALIZED_STRING or LOCALIZED_TEXT_AREA setting: " + setting.toString());
        }

        settingMap.put(setting, JSONConversions.mapToString(values));
    }

    public List<String> readStringArraySetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read STRING_ARRAY value for setting: " + setting.toString());
        }

        final String stringValue = settingMap.get(setting);
        return JSONConversions.stringToList(stringValue);
    }

    public void writeStringArraySetting(final PwmSetting setting, final List<String> values) {
        checkModifyability();
        if (PwmSetting.Syntax.STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write STRING_ARRAY value to setting: " + setting.toString());
        }

        settingMap.put(setting, JSONConversions.listToString(values));
    }

    public Map<String,List<String>> readLocalizedStringArraySetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString());
        }

        final String stringValue = settingMap.get(setting);
        return JSONConversions.stringToNestedList(stringValue);
    }

    public void writeLocalizedStringArraySetting(final PwmSetting setting, final Map<String,List<String>> values) {
        checkModifyability();
        if (PwmSetting.Syntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write LOCALIZED_STRING_ARRAY value to setting: " + setting.toString());
        }

        settingMap.put(setting, JSONConversions.nestedListToString(values));
    }

    public String toXml()
            throws IOException
    {
        final Element settingsElement = new Element("settings");
        final Map<PwmSetting.Category, List<PwmSetting>> valuesByCategory = PwmSetting.valuesByCategory();
        for (final PwmSetting.Category category: valuesByCategory.keySet()) {
            for (final PwmSetting setting : valuesByCategory.get(category)) {
                final Element settingElement = new Element("setting");
                settingElement.setAttribute("key", setting.getKey());
                settingElement.setAttribute("syntax", setting.getSyntax().toString());
                {
                    final Element labelElement = new Element("label");
                    labelElement.addContent(setting.getLabel(Locale.getDefault()));
                    settingElement.addContent(labelElement);
                }

                switch (setting.getSyntax()) {
                    case LOCALIZED_STRING:
                    case LOCALIZED_TEXT_AREA:
                    {
                        final Map<String,String> localizedSettings = this.readLocalizedStringSetting(setting);
                        for (final String locale : localizedSettings.keySet()) {
                            final String value = localizedSettings.get(locale);
                            final Element valueElement = new Element("value");
                            valueElement.addContent(new CDATA(value));
                            if (locale != null && locale.length() > 0) {
                                valueElement.setAttribute("locale",locale);
                            }
                            settingElement.addContent(valueElement);
                        }
                    }
                    break;

                    case LOCALIZED_STRING_ARRAY:
                    {
                        final Map<String,List<String>> localizedSettings = this.readLocalizedStringArraySetting(setting);
                        for (final String locale : localizedSettings.keySet()) {
                            for (final String value : localizedSettings.get(locale)) {
                                final Element valueElement = new Element("value");
                                valueElement.addContent(new CDATA(value));
                                if (locale != null && locale.length() > 0) {
                                    valueElement.setAttribute("locale",locale);
                                }
                                settingElement.addContent(valueElement);
                            }
                        }
                    }
                    break;

                    case STRING_ARRAY:
                    {
                        final List<String> values = this.readStringArraySetting(setting);
                        for (final String value : values) {
                            final Element valueElement = new Element("value");
                            valueElement.addContent(new CDATA(value));
                            settingElement.addContent(valueElement);
                        }
                    }
                    break;

                    case PASSWORD:
                    {
                        final Element valueElement = new Element("value");
                        settingElement.addContent(valueElement);
                        try {
                            final String key = STORED_DATE_FORMAT.format(createTime) + this.getClass().getSimpleName();
                            final String encodedValue = TextConversations.encryptValue(settingMap.get(setting), key);
                            valueElement.addContent(encodedValue);
                        } catch (Exception e) {
                            valueElement.addContent("");
                            throw new RuntimeException("missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage());
                        }
                    }
                    break;


                    default:
                        final Element valueElement = new Element("value");
                        valueElement.addContent(new CDATA(settingMap.get(setting)));
                        settingElement.addContent(valueElement);
                }
                settingsElement.addContent(settingElement);
            }
        }

        final Element pwmConfigElement = new Element("PwmConfiguration");
        pwmConfigElement.addContent(new Comment("Configuration file generated for PWM Password Self Service"));
        pwmConfigElement.addContent(new Comment("WARNING: This configuration file contains sensitive security information, please handle with care!"));
        pwmConfigElement.addContent(new Comment("NOTICE: This file is encoded as UTF-8.  Do not save or edit this file with an editor that does not support UTF-8.  Specifically, do not use Windows Notepad to save or edit this file."));
        pwmConfigElement.addContent(settingsElement);
        pwmConfigElement.setAttribute("pwmVersion", PwmConstants.PWM_VERSION);
        pwmConfigElement.setAttribute("pwmBuild", PwmConstants.BUILD_NUMBER);
        pwmConfigElement.setAttribute("createTime", STORED_DATE_FORMAT.format(createTime));
        pwmConfigElement.setAttribute("modifyTime", STORED_DATE_FORMAT.format(modifyTime));
        pwmConfigElement.setAttribute("xmlVersion", XML_FORMAT_VERSION);

        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        return outputter.outputString(new Document(pwmConfigElement));
    }

    public static StoredConfiguration fromXml(final String xmlData, final boolean readPasswordSettings)
            throws Exception
    {
        final SAXBuilder builder = new SAXBuilder();
        final Reader in = new StringReader(xmlData);
        final Document inputDocument;
        try {
            inputDocument = builder.build(in);
        } catch (Exception e) {
            throw new Exception("error parsing xml data: " + e.getMessage());
        }

        final Set<PwmSetting> seenSettings = new HashSet<PwmSetting>();

        final StoredConfiguration newConfiguration = StoredConfiguration.getDefaultConfiguration();
        try {
            final Element rootElement = inputDocument.getRootElement();
            final String createTimeString = rootElement.getAttributeValue("createTime");
            if (createTimeString == null) {
                throw new IllegalArgumentException("missing createTime timestamp");
            }
            final String modifyTimeString = rootElement.getAttributeValue("modifyTime");
            newConfiguration.createTime = STORED_DATE_FORMAT.parse(createTimeString);
            final Element settingsElement = rootElement.getChild("settings");
            final List settingElements = settingsElement.getChildren("setting");
            for (final Object loopSetting : settingElements) {
                final Element settingElement = (Element)loopSetting;
                final String keyName = settingElement.getAttributeValue("key");
                final PwmSetting pwmSetting = PwmSetting.forKey(keyName);
                seenSettings.add(pwmSetting);

                if (pwmSetting == null) {
                    LOGGER.info("unknown setting key while parsing input configuration: " + keyName);
                } else {
                    switch (pwmSetting.getSyntax()) {
                        case LOCALIZED_STRING:
                        case LOCALIZED_TEXT_AREA:
                        {
                            final List valueElements = settingElement.getChildren("value");
                            final Map<String,String> values = new TreeMap<String,String>();
                            for (final Object loopValue : valueElements) {
                                final Element loopValueElement = (Element)loopValue;
                                final String localeString = loopValueElement.getAttributeValue("locale");
                                final String value = loopValueElement.getText();
                                values.put(localeString == null ? "" : localeString, value);
                            }
                            newConfiguration.writeLocalizedSetting(pwmSetting, values);
                        }
                        break;

                        case STRING_ARRAY:
                        {
                            final List valueElements = settingElement.getChildren("value");
                            final List<String> values = new ArrayList<String>();
                            for (final Object loopValue : valueElements) {
                                final Element loopValueElement = (Element)loopValue;
                                final String value = loopValueElement.getText();
                                values.add(value);
                            }
                            newConfiguration.writeStringArraySetting(pwmSetting, values);
                        }
                        break;

                        case LOCALIZED_STRING_ARRAY:
                        {
                            final List valueElements = settingElement.getChildren("value");
                            final Map<String,List<String>> values = new TreeMap<String,List<String>>();
                            for (final Object loopValue : valueElements) {
                                final Element loopValueElement = (Element)loopValue;
                                final String localeString = loopValueElement.getAttributeValue("locale") == null ? "" : loopValueElement.getAttributeValue("locale");
                                final String value = loopValueElement.getText();
                                List<String> valueList = values.get(localeString);
                                if (valueList == null) {
                                    valueList = new ArrayList<String>();
                                    values.put(localeString, valueList);
                                }
                                valueList.add(value);
                            }
                            newConfiguration.writeLocalizedStringArraySetting(pwmSetting, values);
                        }
                        break;

                        case PASSWORD:
                        {
                            if (readPasswordSettings) {
                                final Element valueElement = settingElement.getChild("value");
                                final String encodedValue = valueElement.getText();
                                try {
                                    final String key = STORED_DATE_FORMAT.format(newConfiguration.createTime) + StoredConfiguration.class.getSimpleName();
                                    final String decodedValue = TextConversations.decryptValue(encodedValue, key);
                                    newConfiguration.writeSetting(pwmSetting, decodedValue);
                                } catch (Exception e) {
                                    newConfiguration.writeSetting(pwmSetting, "");
                                    throw new RuntimeException("unable to decode value: " + e.getMessage());
                                }
                            } else {
                                newConfiguration.writeSetting(pwmSetting, "");
                            }

                        }
                        break;

                        default:
                            final Element valueElement = settingElement.getChild("value");
                            final String value = valueElement.getText();
                            newConfiguration.writeSetting(pwmSetting, value);
                    }
                }
            }
            if (modifyTimeString == null) {
                throw new IllegalArgumentException("missing modifyTime timestamp");
            }
            newConfiguration.modifyTime = STORED_DATE_FORMAT.parse(modifyTimeString);

            for (final PwmSetting setting : PwmSetting.values()) {
                if (!seenSettings.contains(setting)) {
                    LOGGER.info("missing setting key while parsing input configuration: " + setting.getKey() + ", will use default value");
                }
            }

        } catch (Exception e) {
            throw new Exception("Error reading configuration file format: " + e.getMessage());
        }

        LOGGER.debug("successfully loaded configuration with " + newConfiguration.settingMap.size() + " setting values");
        return newConfiguration;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final PwmSetting setting : PwmSetting.values()) {
            sb.append(setting.getKey());
            sb.append("=");
            if (setting.isConfidential()) {
                sb.append("**removed**");
            } else {
                sb.append(settingMap.get(setting));
            }
            sb.append(", ");
        }
        return sb.toString();
    }

    public String checkValuesForErrors() {

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            final StringBuilder errorString = new StringBuilder();
            errorString.append(loopSetting.getCategory().getLabel(Locale.getDefault())).append("-").append(loopSetting.getLabel(Locale.getDefault())).append(" ");

            final Pattern loopPattern = loopSetting.getRegExPattern();

            switch (loopSetting.getSyntax()) {
                case NUMERIC:
                {
                    final String value = this.readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append(" missing required value");
                        return errorString.toString();
                    }

                    try { Integer.parseInt(value); } catch (Exception e) {
                        errorString.append(" can not parse integer value:").append(e.getMessage()); return errorString.toString();
                    }
                }
                break;

                case BOOLEAN:
                {
                    final String value = this.readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append(" missing required value");
                        return errorString.toString();
                    }

                    try { Boolean.parseBoolean(value); } catch (Exception e) {
                        errorString.append(" can not parse boolean  value:").append(e.getMessage()); return errorString.toString();
                    }
                }
                break;

                case STRING_ARRAY:
                {
                    final List<String> values = this.readStringArraySetting(loopSetting);
                    for (final String value : values) {
                        if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                            errorString.append(" missing required value");
                            return errorString.toString();
                        }

                        final Matcher matcher = loopPattern.matcher(value);
                        if (value != null && value.length() > 0 && !matcher.matches()) {
                            errorString.append(" incorrect value format for value: ").append(value);
                            return errorString.toString();
                        }
                    }
                }
                break;

                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA:
                {
                    final Map<String,String> values = this.readLocalizedStringSetting(loopSetting);
                    for (final String locale : values.keySet()) {
                        final String value = values.get(locale);
                        if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                            errorString.append(" missing required value");
                            return errorString.toString();
                        }

                        if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING) {
                            final Matcher matcher = loopPattern.matcher(value);
                            if (value != null && value.length() > 0 && !matcher.matches()) {
                                errorString.append(" incorrect value format for locale '").append(locale).append("': ").append(value);
                                return errorString.toString();
                            }
                        }
                    }
                }
                break;

                case LOCALIZED_STRING_ARRAY:
                {
                    final Map<String,List<String>> values = this.readLocalizedStringArraySetting(loopSetting);
                    for (final String locale : values.keySet()) {
                        for (final String value : values.get(locale)) {
                            if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                                errorString.append(" missing required value");
                                return errorString.toString();
                            }

                            final Matcher matcher = loopPattern.matcher(value);
                            if (value != null && value.length() > 0 && !matcher.matches()) {
                                errorString.append(" incorrect value format for locale '").append(locale).append("': ").append(value);
                                return errorString.toString();
                            }
                        }
                    }
                }
                break;

                default:
                {
                    final String value = readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append(" missing required value");
                        return errorString.toString();
                    }
                    final Matcher matcher = loopPattern.matcher(value);
                    if (value != null && value.length() > 0 && !matcher.matches()) {
                        errorString.append(" incorrect value format for value: '").append(value);
                        return errorString.toString();
                    }
                }
            }
        }

        return null;
    }

    private void checkModifyability() {
        if (locked) {
            throw new IllegalStateException("configuration is locked, can not be modified");
        }
        modifyTime = new Date();
    }

    private static class TextConversations {
        private static String encryptValue(final String value, final String key)
                throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException
        {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey sks = makeKey(key);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters());
            final byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64Util.encodeBytes(encrypted);
        }

        private static String decryptValue(final String value, final String key)
                throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException
        {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey sks = makeKey(key);
            final byte[] decoded = Base64Util.decode(value);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, sks);
            final byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        }

        private static SecretKey makeKey(final String text)
                throws NoSuchAlgorithmException, UnsupportedEncodingException
        {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            final byte[] key = Arrays.copyOf(md.digest(),16);
            return  new SecretKeySpec(key,"AES");
        }

    }

    private static class JSONConversions {
        public static Map<String,String> stringToMap(final String input) {
            if (input == null) {
                return Collections.emptyMap();
            }

            final JSONObject srcMap = (JSONObject) JSONValue.parse(input);
            final Map<String,String> returnMap = new TreeMap<String,String>();
            for (final Object key : srcMap.keySet()) {
                returnMap.put(key.toString(), srcMap.get(key).toString());
            }
            return returnMap;
        }

        public static String mapToString(final Map<String,String> input) {
            if (input == null) {
                return JSONObject.toJSONString(Collections.emptyMap());
            }
            return JSONObject.toJSONString(input);
        }

        public static List<String> stringToList(final String input) {
            if (input == null) {
                return Collections.emptyList();
            }

            final JSONArray srcList = (JSONArray) JSONValue.parse(input);
            final List<String> returnList = new ArrayList<String>();
            for (final Object item : srcList) {
                returnList.add(item.toString());
            }
            return returnList;
        }

        public static String listToString(final List<String> input) {
            if (input == null) {
                return JSONArray.toJSONString(Collections.emptyList());
            }
            return JSONArray.toJSONString(input);
        }

        public static Map<String,List<String>> stringToNestedList(final String input) {
            if (input == null) {
                return Collections.emptyMap();
            }

            final JSONObject srcMap = (JSONObject) JSONValue.parse(input);
            final Map<String,List<String>> returnMap = new TreeMap<String,List<String>>();
            for (final Object key : srcMap.keySet()) {
                final List<String> returnList = new ArrayList<String>();
                final JSONArray srcList = (JSONArray)srcMap.get(key);
                for (final Object item : srcList) {
                    returnList.add(item.toString());
                }
                returnMap.put(key.toString(), returnList);
            }
            return returnMap;
        }

        public static String nestedListToString(final Map<String,List<String>> input) {
            if (input == null) {
                return JSONObject.toJSONString(Collections.emptyMap());
            }
            return JSONObject.toJSONString(input);
        }
    }
}
