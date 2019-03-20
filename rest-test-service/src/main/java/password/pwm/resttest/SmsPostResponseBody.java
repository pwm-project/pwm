package password.pwm.resttest;

import java.util.Date;

public class SmsPostResponseBody {
    private String messageContent;
    private Date date;

    public SmsPostResponseBody(String message, Date date) {
        String[] strings = message.split("&");
        this.messageContent = strings[strings.length - 1];
        this.date = date;
    }

    public SmsPostResponseBody(String message) {
        String[] strings = message.split("&");
        this.messageContent = strings[strings.length - 1];
    }

    public SmsPostResponseBody(Date date) {
        this.date = date;
        this.messageContent = "";
    }

    public SmsPostResponseBody(){}

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
