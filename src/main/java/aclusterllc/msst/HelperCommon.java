package aclusterllc.msst;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;

public class HelperCommon {
    public static String getStackTraceString(Exception ex){
        StringWriter errors = new StringWriter();
        ex.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }
    public static int bytesToInt(byte[] bytes)
    {
        return new BigInteger(bytes).intValue();
    }
    public static long bytesToLong(byte[] bytes)
    {
        return new BigInteger(bytes).longValue();
    }
}
