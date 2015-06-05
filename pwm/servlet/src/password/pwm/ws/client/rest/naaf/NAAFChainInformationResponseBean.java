package password.pwm.ws.client.rest.naaf;

import java.io.Serializable;
import java.util.List;

class NAAFChainInformationResponseBean implements Serializable {
    private List<NAAFChainBean> chains;

    public List<NAAFChainBean> getChains() {
        return chains;
    }

    public void setChains(List<NAAFChainBean> chains) {
        this.chains = chains;
    }
}
