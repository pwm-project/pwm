package password.pwm.http.tag.url;

import password.pwm.http.servlet.resource.ResourceFileServlet;

public enum PwmThemeURL {
    THEME_URL(ResourceFileServlet.THEME_CSS_PATH),
    MOBILE_THEME_URL(ResourceFileServlet.THEME_CSS_MOBILE_PATH),
    CONFIG_THEME_URL(ResourceFileServlet.THEME_CSS_CONFIG_PATH),;

    PwmThemeURL(String cssName) {
        this.cssName = cssName;
    }

    private final String cssName;

    public String token() {
        return "%" + this.toString() + "%";
    }

    public String getCssName() {
        return cssName;
    }
}
