package password.pwm.http.tag;

import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;

public class CurrentUrlTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(CurrentUrlTag.class);

    @Override
    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
            final String currentUrl = pwmRequest.getURLwithoutQueryString();
            pageContext.getOut().write(StringUtil.escapeHtml(currentUrl));
        } catch (Exception e) {
            try {
                pageContext.getOut().write("errorGeneratingPwmFormID");
            } catch (IOException e1) {
                /* ignore */
            }
            LOGGER.error("error during pwmFormIDTag output of pwmFormID: " + e.getMessage());
        }
        return EVAL_PAGE;
    }
}
