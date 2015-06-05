package password.pwm.ws.client.rest.naaf;

import java.io.Serializable;
import java.util.List;

public class NAAFErrorResponseBean implements Serializable {
    private String status;
    private List<ErrorData> errors;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ErrorData> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorData> errors) {
        this.errors = errors;
    }

    public static class ErrorData {
        private String description;
        private String name;
        private String location;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }
}
