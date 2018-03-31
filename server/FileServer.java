import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import java.math.*;
import javax.net.ServerSocketFactory;
import com.sun.net.ssl.*;
import com.sun.net.ssl.internal.ssl.Provider;

public class FileServer {

    public static SSLServerSocket ss;
    public static SSLServerSocket ss2;
    public static SSLServerSocket ss3;
    public static SSLServerSocket ss4;

    public static SSLSocket cs;
    public static SSLSocket cs2;
    public static SSLSocket cs3;
    public static SSLSocket cs4;

    public static SSLServerSocketFactory ssf;
    public static int iii;

    public static final int maxClients = 10;
    public static final Thread[] threads = new Thread[maxClients];

    public static void main(String[] args) {

        {
            Security.addProvider(new Provider());
            System.setProperty("javax.net.ssl.keyStore", "server.keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", "jmu2017");
        }
        try {
            ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            ss4 = (SSLServerSocket) ssf.createServerSocket(1989);
        } catch (IOException io) {}

        while (true) {
            try {




                // Populate the thread arraylist with new client connections
                //int ii;
                for (iii = 0; iii < maxClients; iii++) {
                    if (threads[iii] == null) {
                        // Instantiate the Server Socket with SSL/TLS protocl
                        /* ss = (SSLServerSocket) ssf.createServerSocket(1988 + (10 * iii));
                         ss2 = (SSLServerSocket) ssf.createServerSocket(1990 + (10 * iii));
                         ss3 = (SSLServerSocket) ssf.createServerSocket(1992 + (10 * iii));
                         // Listen for new client connections
                         cs = (SSLSocket) ss.accept();
                         cs2 = (SSLSocket) ss2.accept();
                         cs3 = (SSLSocket) ss3.accept();*/
                        cs4 = (SSLSocket) ss4.accept();


                        (threads[iii] = new Thread(new Connection(cs4))).start();
                        break;
                    }
                }

                // Limit the total number of client connections 
                if (iii == maxClients) {
                    PrintStream ps = new PrintStream(cs.getOutputStream());
                    ps.println("Server is busy. Try again later.");
                    ps.close();
                    cs.close();

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

}