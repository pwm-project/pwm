package password.pwm.svc.wordlist;

import lombok.Getter;

@Getter
public enum WordlistSourceType
{
    BuiltIn( "Built-In" ),
    AutoImport( "Import from configured URL" ),
    User( "Uploaded" ),;

    private final String label;

    WordlistSourceType( final String label )
    {
        this.label = label;
    }
}
