package password.pwm.http.tag.value;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

import javax.servlet.jsp.PageContext;

public interface ValueOutput {
    String valueOutput(
            final PwmRequest pwmRequest,
            final PageContext pageContext)
            throws ChaiUnavailableException, PwmUnrecoverableException;
}
