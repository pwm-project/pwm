package password.pwm.ws.server;

import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Value;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.WebServiceUsage;

import java.io.Serializable;
import java.util.Set;

@Value
public class RestAuthentication implements Serializable {
    private RestAuthenticationType type;
    private String namedSecretName;
    private UserIdentity ldapIdentity;
    private Set<WebServiceUsage> usages;
    private boolean thirdPartyEnabled;
    private transient ChaiProvider chaiProvider;
}
