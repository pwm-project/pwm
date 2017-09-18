package password.pwm.ws.server.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import password.pwm.PwmConstants;
import password.pwm.http.HttpMethod;
import password.pwm.ws.server.PwmRestServlet;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.StandaloneRestRequestBean;

import javax.servlet.annotation.WebServlet;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;

@WebServlet(
        name="RestPingServer",
        urlPatterns={
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/ping",
        }
)

public class RestPingServer extends PwmRestServlet {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PingResponse implements Serializable {
        private Instant time;
        private String runtimeNonce;
    }

    @Override
    public RestResultBean invokeWebService(final StandaloneRestRequestBean standaloneRestRequestBean) {
        final PingResponse pingResponse = new PingResponse();
        pingResponse.setTime(Instant.now());
        pingResponse.setRuntimeNonce(standaloneRestRequestBean.getPwmApplication().getRuntimeNonce());
        return new RestResultBean(pingResponse);
    }

    @Override
    public PwmRestServlet.ServiceInfo getServiceInfo() {
        return new ServiceInfo(Collections.singleton(HttpMethod.GET));
    }

}
