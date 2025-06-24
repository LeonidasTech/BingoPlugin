package wzd.bingo;

/**
 * Represents a user's signup status for a bingo event
 */
public class SignupStatus
{
    private final boolean signedUp;
    private final boolean accepted;
    private final String message;
    
    public SignupStatus(boolean signedUp, boolean accepted, String message)
    {
        this.signedUp = signedUp;
        this.accepted = accepted;
        this.message = message;
    }
    
    public boolean isSignedUp()
    {
        return signedUp;
    }
    
    public boolean isAccepted()
    {
        return accepted;
    }
    
    public String getMessage()
    {
        return message;
    }
    
    @Override
    public String toString()
    {
        return String.format("SignupStatus{signedUp=%s, accepted=%s, message='%s'}", 
            signedUp, accepted, message);
    }
} 