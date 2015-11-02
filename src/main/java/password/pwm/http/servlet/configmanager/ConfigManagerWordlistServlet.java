/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.ServletHelper;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.svc.wordlist.StoredWordlistDataBean;
import password.pwm.svc.wordlist.WordlistType;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(
        name = "ConfigManagerWordlistServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/wordlists",
        }
)
public class ConfigManagerWordlistServlet extends AbstractPwmServlet {
    final static private PwmLogger LOGGER = PwmLogger.forClass(ConfigManagerWordlistServlet.class);

    public enum ConfigManagerAction implements ProcessAction {
        clearWordlist(HttpMethod.POST),
        uploadWordlist(HttpMethod.POST),
        readWordlistData(HttpMethod.POST),

        ;

        private final HttpMethod method;

        ConfigManagerAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected ConfigManagerAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ConfigManagerAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {

        final ConfigManagerAction processAction = readProcessAction(pwmRequest);
        if (processAction != null) {
            switch (processAction) {
                case uploadWordlist:
                    restUploadWordlist(pwmRequest);
                    return;

                case clearWordlist:
                    restClearWordlist(pwmRequest);
                    return;

                case readWordlistData:
                    restReadWordlistData(pwmRequest);
                    return;
            }
            return;
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_WORDLISTS);
    }

    void restUploadWordlist(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String wordlistTypeParam = pwmRequest.readParameterAsString("wordlist");
        final WordlistType wordlistType = WordlistType.valueOf(wordlistTypeParam);

        if (wordlistType == null) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown wordlist type: " + wordlistTypeParam);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during import: " + errorInformation.toDebugStr());
            return;
        }

        if (!ServletFileUpload.isMultipartContent(req)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"no file found in upload");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during import: " + errorInformation.toDebugStr());
            return;
        }

        final InputStream inputStream = ServletHelper.readFileUpload(pwmRequest.getHttpServletRequest(),"uploadFile");

        try {
            wordlistType.forType(pwmApplication).populate(inputStream);
        } catch (PwmUnrecoverableException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmRequest, errorInfo.toDebugStr());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
    }

    void restClearWordlist(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException
    {
        final String wordlistTypeParam = pwmRequest.readParameterAsString("wordlist");
        final WordlistType wordlistType = WordlistType.valueOf(wordlistTypeParam);

        if (wordlistType == null) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown wordlist type: " + wordlistTypeParam);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during clear: " + errorInformation.toDebugStr());
            return;
        }

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    wordlistType.forType(pwmRequest.getPwmApplication()).populateBuiltIn();
                } catch (Exception e) {
                    LOGGER.error("error clearing wordlist " + wordlistType + ", error: " + e.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.setName(Helper.makeThreadName(pwmRequest.getPwmApplication(), ConfigManagerWordlistServlet.class) + "-" + wordlistType + "-Clear");
        thread.start();

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
    }

    void restReadWordlistData(final PwmRequest pwmRequest)
            throws IOException
    {
        final LinkedHashMap<WordlistType,WordlistDataBean> outputData = new LinkedHashMap<>();
        for (WordlistType wordlistType : WordlistType.values()) {

            final StoredWordlistDataBean storedWordlistDataBean = wordlistType.forType(pwmRequest.getPwmApplication()).readMetadata();

            final Map<String,String> presentableValues = new LinkedHashMap<>();
            presentableValues.put("Population Status", storedWordlistDataBean.isCompleted() ? "Completed" : "In-Progress");
            if (storedWordlistDataBean.isCompleted()) {
                presentableValues.put("Wordlist Type", storedWordlistDataBean.isBuiltin() ? "Built-In" : "Uploaded");
                presentableValues.put("Word Count", String.valueOf(storedWordlistDataBean.getSize()));
                if (!storedWordlistDataBean.isBuiltin()) {
                    presentableValues.put("Population Timestamp", PwmConstants.DEFAULT_DATETIME_FORMAT.format(storedWordlistDataBean.getStoreDate()));
                }
                presentableValues.put("SHA1 Checksum Hash", storedWordlistDataBean.getSha1hash());
            }

            final WordlistDataBean wordlistDataBean = new WordlistDataBean();
            wordlistDataBean.getPresentableData().putAll(presentableValues);
            wordlistDataBean.setCompleted(storedWordlistDataBean.isCompleted());
            wordlistDataBean.setBuiltIn(storedWordlistDataBean.isBuiltin());
            outputData.put(wordlistType,wordlistDataBean);
        }
        pwmRequest.outputJsonResult(new RestResultBean(outputData));
    }

    public static class WordlistDataBean implements Serializable {
        private Map<String,String> presentableData = new LinkedHashMap<>();
        private boolean completed;
        private boolean builtIn;

        public Map<String, String> getPresentableData() {
            return presentableData;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public boolean isBuiltIn() {
            return builtIn;
        }

        public void setBuiltIn(boolean builtIn) {
            this.builtIn = builtIn;
        }
    }
}

