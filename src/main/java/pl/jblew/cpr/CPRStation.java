package pl.jblew.cpr;

import pl.jblew.cpr.bootstrap.Bootstrap;

/**
 * Hello world!
 *
 */
public class CPRStation 
{
    public static void main( String[] args )
    {
        Bootstrap b = new Bootstrap();
        b.synchronousStart();
    }
}