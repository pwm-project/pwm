package password.pwm.resttest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SmsResponse {
    public static SmsResponse instance;

    Map<String, ArrayList<SmsPostResponseBody>> recentSmsMessages;

    public SmsResponse() {
        this.recentSmsMessages = new HashMap<String, ArrayList<SmsPostResponseBody>>();
    }

    /** Getters and Setters */
    public static synchronized SmsResponse getInstance() {
        if (instance == null){
            instance = new SmsResponse();
        }
        return instance;
    }

    Map<String, ArrayList<SmsPostResponseBody>> getRecentSmsMessages() {
        return recentSmsMessages;
    }

    public void setRecentSmsMessages(Map<String, ArrayList<SmsPostResponseBody>> recentSmsMessages) {
        this.recentSmsMessages = recentSmsMessages;
    }

    /** Helper Functions */
    public void addToMap(String username, SmsPostResponseBody responseBody){
        if (recentSmsMessages.containsKey(username)){
            recentSmsMessages.get(username).add(responseBody);
        } else {
            ArrayList<SmsPostResponseBody> arrayList = new ArrayList<>();
            arrayList.add(responseBody);
            recentSmsMessages.put(username, arrayList);
        }
    }

    public SmsPostResponseBody getRecentFromMap(String username) {
        SmsPostResponseBody responseBody = new SmsPostResponseBody();
        if (recentSmsMessages.containsKey(username)) {
            ArrayList<SmsPostResponseBody> userMessages = recentSmsMessages.get(username);
            int mostRecentIndex = 0;
            for (int i = 0; i < userMessages.size(); i++) {
                if (userMessages.get(i).getDate().getTime() > userMessages.get(mostRecentIndex).getDate().getTime()) {
                    mostRecentIndex = i;
                }
            }
            responseBody = userMessages.get(mostRecentIndex);
        }
        return responseBody;
    }
}
