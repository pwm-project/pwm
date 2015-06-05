package password.pwm.config;

import org.jdom2.Attribute;
import org.jdom2.Element;
import password.pwm.i18n.ConfigEditor;
import password.pwm.i18n.LocaleHelper;

import java.util.*;

public enum PwmSettingTemplate {
    NOVL,
    AD,
    ORACLE_DS,
    DEFAULT,
    NOVL_IDM,

    ;

    public String getLabel(final Locale locale) {
        final String key = "Template_Label_" + this.toString();
        return LocaleHelper.getLocalizedMessage(locale, key, null, ConfigEditor.class);
    }

    public boolean isHidden() {
        final Element templateElement = readTemplateElement(this);
        final Attribute requiredAttribute = templateElement.getAttribute("hidden");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    private static Element readTemplateElement(PwmSettingTemplate pwmSettingTemplate) {
        final Element element = PwmSettingXml.readTemplateXml(pwmSettingTemplate);
        if (element == null) {
            throw new IllegalStateException("missing PwmSetting.xml template element for " + pwmSettingTemplate);
        }
        return element;
    }

    public static List<PwmSettingTemplate> sortedValues(final Locale locale) {
        final Map<String,PwmSettingTemplate> sortedValues = new TreeMap<>();

        for (final PwmSettingTemplate pwmSettingTemplate : values()) {
            sortedValues.put(pwmSettingTemplate.getLabel(locale), pwmSettingTemplate);
        }

        return Collections.unmodifiableList(new ArrayList<>(sortedValues.values()));
    }
}
