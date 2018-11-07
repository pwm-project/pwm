package password.pwm.svc.wordlist;

import lombok.Value;

import java.io.Serializable;

@Value
public class WordlistSourceInfo implements Serializable
{
    private String checksum;
    private long bytes;
}
