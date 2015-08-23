package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet(
        name="UserInformationServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/account",
                PwmConstants.URL_PREFIX_PRIVATE + "/userinfo",
                PwmConstants.URL_PREFIX_PRIVATE + "/userinfo.jsp"
        }
)
public class AccountInformationServlet extends AbstractPwmServlet {
    @Override
    protected void processAction(PwmRequest pwmRequest) throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {

        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.ACCOUNT_INFORMATION_ENABLED)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            return;
        }


        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ACCOUNT_INFORMATION);
    }

    @Override
    protected ProcessAction readProcessAction(PwmRequest request) throws PwmUnrecoverableException {
        return null;
    }
}
