package password.pwm.svc.sessiontrack;

import com.blueconic.browscap.Capabilities;
import com.blueconic.browscap.ParseException;
import com.blueconic.browscap.UserAgentParser;
import com.blueconic.browscap.UserAgentService;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;

public class UserAgentUtils  {
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserAgentUtils.class );

    private static UserAgentParser cachedParser;

    private static UserAgentParser getUserAgentParser() throws PwmUnrecoverableException
    {
        if (cachedParser == null) {
            try {
                cachedParser = new UserAgentService().loadParser();
            }  catch ( IOException | ParseException e ) {
                final String msg = "error loading user-agent parser: " + e.getMessage();
                LOGGER.error( msg, e );
                throw new PwmUnrecoverableException( PwmError.ERROR_UNKNOWN, msg );
            }
        }
        return cachedParser;
    }

    public static void checkIfPreIE11( final PwmRequest pwmRequest) throws PwmUnrecoverableException  {
        final String userAgentString = pwmRequest.readHeaderValueAsString( HttpHeader.UserAgent);
        if ( StringUtil.isEmpty( userAgentString )) {
            return ;
        }

        boolean badBrowser = false;

        final UserAgentParser userAgentParser = getUserAgentParser();
        final Capabilities capabilities = userAgentParser.parse( userAgentString );
        final String browser = capabilities.getBrowser();
        final String browserMajorVersion = capabilities.getBrowserMajorVersion();

        if ("IE".equalsIgnoreCase( browser )) {
            try {
                final int majorVersionInt = Integer.parseInt( browserMajorVersion );
                if (majorVersionInt <= 10) {
                    badBrowser = true;
                }
            }  catch ( NumberFormatException e ) {
                LOGGER.error( "error parsing user-agent major version" + e.getMessage(), e );
            }
        }

        if (badBrowser) {
            final String errorMsg = "Internet Explorer version is not supported for this function.  Please use Internet Explorer 11 or higher or another web browser.";
            throw new PwmUnrecoverableException(new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg));
        }
    }
}
