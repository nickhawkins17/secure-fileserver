import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.cert.*;
import java.math.*;
import com.sun.net.ssl.*;
import com.sun.net.ssl.internal.ssl.Provider;

class Seconds {
    private final Date createdDate = new java.util.Date();

    public int getAgeInSeconds() {
        java.util.Date now = new java.util.Date();
        return (int)((now.getTime() - this.createdDate.getTime()) / 1000);
    }
}

class AESException extends Exception {

    public AESException() {}
    public AESException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

class AESUtils {

    public static void encrypt(String key, File in , File out) throws AESException {
        AESCrypto(Cipher.ENCRYPT_MODE, key, in, out);
    }

    public static void decrypt(String key, File in , File out) throws AESException {
        AESCrypto(Cipher.DECRYPT_MODE, key, in, out);
    }

    private static void AESCrypto(int cipherMode, String key, File in, File out) throws AESException {
        try {
            Key useKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(cipherMode, useKey);

            FileInputStream inputStream = new FileInputStream( in );
            byte[] inputBytes = new byte[(int) in .length()];
            inputStream.read(inputBytes);

            byte[] outputBytes = cipher.doFinal(inputBytes);

            FileOutputStream outputStream = new FileOutputStream(out);
            outputStream.write(outputBytes);

            inputStream.close();
            outputStream.close();

        } catch (NoSuchPaddingException | IOException | InvalidKeyException |
            IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException crypt) {
            throw new AESException("Error encrypting/decrypting file.", crypt);
        }
    }
}


public class FileClient {

    public static SSLSocket s;
    public static SSLSocketFactory ssf;
    public static SSLSocket s2;
    public static SSLSocket s3;
    public static SSLSocket s4;

    public static DataOutputStream dos;
    public static FileInputStream fis;
    public static DataInputStream dis;
    public static FileOutputStream fos;
    public static Scanner read;
    public static String user;
    public static String currentUser;
    public static String activeDirectory = System.getProperty("user.dir");
    public static File directory = new File(activeDirectory + "/client");
    public static boolean pullPush;
    public static boolean pushPull;
    public static boolean syncFailed = false;
    public static Seconds time;
    public static int portConnections;
    public static ArrayList < File > mostRecent;


    // Upload the file from the client to the server
    public static void upload(String file, int size, SSLSocket so) throws IOException {
        dos = new DataOutputStream(so.getOutputStream());
        fis = new FileInputStream("client/" + file);
        byte[] buffer = new byte[size];

        while (fis.read(buffer) > 0) {
            dos.write(buffer);
        }
    }

    // Download the file from the server to the client
    public static void download(String file, int size, boolean verbose, SSLSocket so) throws IOException {
        dis = new DataInputStream(so.getInputStream());
        fos = new FileOutputStream("client/" + file);
        byte[] buffer = new byte[size];
        int bytesRead = 0;
        // Read data from socket input stream into the byte array 
        bytesRead = dis.read(buffer, 0, Math.min(buffer.length, 25000));
        // Write data from the byte array to the file in client
        fos.write(buffer, 0, bytesRead);

        if (verbose) {
            System.out.println("Writing " + bytesRead + " bytes to the client");
            System.out.println("File " + file + " successfully downloaded.\n");
        }

    }

    // Monitor changes in the client
    public static boolean changeOccurred() {
        ArrayList < File > currentFiles = eligibleToPush();

        Collections.sort(currentFiles);
        Collections.sort(mostRecent);
        return !currentFiles.equals(mostRecent);
    }

    public static ArrayList < File > eligibleToPush() {
        ArrayList < File > names = new ArrayList < File > ();
        new File("client/.DS_Store").delete();
        new File("client/.DS_Store.encrypted").delete();

        for (File f: directory.listFiles()) {
            if (f.getName().length() >= 10) {
                if (!(f.getName().substring(0, 10).equals("FileClient") || f.getName().substring(0, 10).equals("Seconds.cl") ||
                        (f.getName().substring(0, 10).equals("AESExcepti")) || (f.getName().substring(0, 10).equals("AESUtils.c")))) {
                    names.add(f);
                }
            } else {
                names.add(f);
            }
        }

        return names;
    }

    public static void pushSync(BufferedReader br, PrintWriter w) throws IOException, NoSuchAlgorithmException {
        int numToUpload;
        String nameToUpload;
        String nameEncrypted;
        int sizeOfUpload;
        ArrayList < File > names = eligibleToPush();

        // Push number of files to sync 
        w.println(Integer.toString(names.size()));

        // Iterate through all files
        for (int ii = 0; ii < names.size(); ii++) {
            // Push file name
            w.println(names.get(ii).getName());

            // Push file size
            w.println(names.get(ii).length());
        }

        numToUpload = Integer.parseInt(br.readLine());

        // Encrypt and upload each file to server for synchronization
        for (int ii = 0; ii < numToUpload; ii++) {
            nameToUpload = br.readLine();
            File fileEncrypted = new File("client/" + nameToUpload + ".encrypted");

            try {
                AESUtils.encrypt(createKey(nameToUpload), new File("client/" + nameToUpload), fileEncrypted);
            } catch (AESException cry) {
                System.out.println("Error encrypting file.");
            }

            sizeOfUpload = (int) fileEncrypted.length();
            w.println(sizeOfUpload);
            upload(nameToUpload + ".encrypted", sizeOfUpload, s2);
            fileEncrypted.delete();
        }
    }

    public static void pullSync(BufferedReader br, PrintWriter w) throws IOException, NoSuchAlgorithmException {
        int totalFiles;
        String name;
        int size;
        int sizeOfDownload;
        File directory = new File("client/");
        ArrayList < String > toDownload = new ArrayList < String > ();
        ArrayList < String > currentNames = new ArrayList < String > ();
        ArrayList < String > mostRecentFiles = new ArrayList < String > ();

        ArrayList < File > currentFiles = eligibleToPush();

        for (int ii = 0; ii < currentFiles.size(); ii++) {
            currentNames.add(currentFiles.get(ii).getName());
        }

        try {
        	String cleanup = "";
            // Pull number of files 
            if (portConnections > 1)
            	cleanup = br.readLine();

            totalFiles = Integer.parseInt(br.readLine());
            if (totalFiles > 0) {
                // Replace files in client directory
                for (int ii = 0; ii < totalFiles; ii++) {
                    // Get file name
                    name = br.readLine();
                    mostRecentFiles.add(name);
                    // Get file size
                    sizeOfDownload = Integer.parseInt(br.readLine());

                    if (!Arrays.asList(currentNames).contains(name))
                        toDownload.add(name);
                    else if (sizeOfDownload != (int) new File("client/" + name).length())
                        toDownload.add(name);
                }

                // Remove files from client directory unless FileClient source code/class
                for (File f: currentFiles) {
                    if (!mostRecentFiles.contains(f.getName()))
                        f.delete();
                }

                w.println(toDownload.size());

                for (int ii = 0; ii < toDownload.size(); ii++) {
                    w.println(toDownload.get(ii));
                    sizeOfDownload = Integer.parseInt(br.readLine());
                    download(toDownload.get(ii) + ".encrypted", sizeOfDownload, false, s2);

                    File encryptedFile = new File("client/" + toDownload.get(ii) + ".encrypted");
                    File decryptedFile = new File("client/" + toDownload.get(ii));

                    try {

                        AESUtils.decrypt(getKey(toDownload.get(ii), currentUser), encryptedFile, decryptedFile);
                    } catch (AESException crye) {
                        System.out.println("Error decrypting the file.");
                    }

                    encryptedFile.delete();
                }
            }
        } catch (NumberFormatException n) {
            n.printStackTrace();
        }
    }

    // Give the user of a shared file the key for the encrypted file
    public static void syncKey(String fileName, String user) throws IOException {
        Writer addToFile = new BufferedWriter(new FileWriter(user + "_fileEncryptionKeys.txt", true));
        String key = getKey(fileName, currentUser);

        addToFile.append(fileName + "=" + key + "\n");
        addToFile.close();
    }

    public static String createKey(String name) throws IOException {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        Writer addToFile = new BufferedWriter(new FileWriter(currentUser + "_fileEncryptionKeys.txt", true));

        if (getKey(name, currentUser).length() > 0) {
            return getKey(name, currentUser);
        } else {
            while (salt.length() < 16) { // length of the random string.
                int index = (int)(rnd.nextFloat() * chars.length());
                salt.append(chars.charAt(index));
            }
            String saltStr = salt.toString();

            addToFile.append(name + "=" + saltStr + "\n");
            addToFile.close();
            return saltStr;
        }
    }

    // Get the key for the specific user 
    public static String getKey(String name, String encryptionUser) throws IOException {
        String line;
        String file = "";
        String key = "";

        try {
            BufferedReader read = new BufferedReader(new FileReader(encryptionUser + "_fileEncryptionKeys.txt"));

            // Read in data from the users server database 
            while ((line = read.readLine()) != null) {
                int ii;
                file = "";
                for (ii = 0; ii < line.length(); ii++) {
                    if (line.charAt(ii) != '=')
                        file += line.charAt(ii);
                    else
                        break;
                }

                if (file.equals(name)) {
                    for (int jj = ii + 1; jj < line.length(); jj++)
                        key += line.charAt(jj);

                    return key;
                }
            }
        } catch (FileNotFoundException fnfe) {}

        return key;
    }

    public static void main(String[] args) {

        int usernameCount = 0;

        System.setProperty("javax.net.ssl.trustStore", "client.truststore"); {
            Security.addProvider(new Provider());
        }
        try {

            // Instantiate SSL Socket on the Client
            ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            s4 = (SSLSocket) ssf.createSocket("localhost", 1989);

            // Define for user input/output between client and server
            BufferedReader reader4 = new BufferedReader(new InputStreamReader(s4.getInputStream()));
            PrintWriter write4 = new PrintWriter(s4.getOutputStream(), true);

            // Additional variables
            String in ;
            String msg;
            read = new Scanner(System.in);



            // Nested Class sync syncronizes different clients of the same user
            // *********************************************************
            class Sync extends Thread {
                public void run() {
                    try {
                        time = new Seconds();
                        while (true) {
                            BufferedReader reader2 = new BufferedReader(new InputStreamReader(s2.getInputStream()));
                            PrintWriter write2 = new PrintWriter(s2.getOutputStream(), true);
                            // First pulls changes then pushes changes 
                            if (pullPush) {
                                pullSync(reader2, write2);
                                pushSync(reader2, write2);
                                pullPush = false;
                                pushPull = true;
                                mostRecent = eligibleToPush();
                              // Push changes every 4 seconds
                            } else if (pushPull && time.getAgeInSeconds() >= 4) {
                            	// Only push if changes are made
                                if (changeOccurred()) {
                                    System.out.println("change occurred");

                                    write2.println("change");
                                    pushSync(reader2, write2);
                                } else
                                    write2.println("nochange");

                                pullSync(reader2, write2);
                                time = new Seconds();
                                mostRecent = eligibleToPush();
                            }

                        }
                    } catch (Exception e) {
                        syncFailed = true;
                        System.out.println("Error: Sync failed. Please restart client/server.");
                        e.printStackTrace();
                    }
                }
            }
            // *********************************************************


            while (true) {
                for (int ii = 0; ii < 2; ii++) {
                    System.out.println(reader4.readLine()); in = read.nextLine();
                    write4.println( in );

                    if (ii == 0)
                        currentUser = in ;
                }

                if ((msg = reader4.readLine()).equals("authenticated"))
                    break;
                else
                    System.out.println(msg);
            }


            portConnections = Integer.parseInt(reader4.readLine());
            s = (SSLSocket) ssf.createSocket("localhost", 1988 + ((portConnections - 1) * 10));
            s2 = (SSLSocket) ssf.createSocket("localhost", 1990 + ((portConnections - 1) * 10));
            s3 = (SSLSocket) ssf.createSocket("localhost", 1992 + ((portConnections - 1) * 10));
            pullPush = true;
            pushPull = false;
            mostRecent = eligibleToPush();
            // Define for user input/output between client and server
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            //BufferedReader reader2 = new BufferedReader(new InputStreamReader(s2.getInputStream()));
            PrintWriter write = new PrintWriter(s.getOutputStream(), true);


            // Nested Class Readline reads data written from the server
            // *********************************************************
            class Readall extends Thread {
                public void run() {
                    try {
                        String serverMsg;
                        while ((serverMsg = reader.readLine()) != null)
                            System.out.println(serverMsg);
                    } catch (Exception e) {}
                }
            }
            // *********************************************************



            // Begin file synchronization
            Sync sync = new Sync();
            sync.start();

            // Client continuously listens for socket output and
            // sends responses to the server until the socket is closed
            while (true) { in = "";
                // Read buffer output from the Server
                Readall t = new Readall();
                t.start();
                // Get user input
                in = read.nextLine();

                // User input is written to the buffer of the server
                write.println( in );

                // User specifies downloading file from the server
                if ( in .equals("download")) {
                    pushPull = false;
                    pullPush = false;
                    String next = reader.readLine();

                    if (next.equals("The following files are available for download: ")) {
                        System.out.println(next);
                        String downloadFileName = read.nextLine();
                        new File("client/" + downloadFileName).delete();
                        // File name sent to the server
                        write.println(downloadFileName);
                        download(downloadFileName + ".encrypted", 25000, true, s3);


                        try {
                            File encryptedFile = new File("client/" + downloadFileName + ".encrypted");
                            File decryptedFile = new File("client/" + downloadFileName);
                            AESUtils.decrypt(getKey(downloadFileName, currentUser), encryptedFile, decryptedFile);
                            encryptedFile.delete();
                        } catch (AESException exc2) {
                            System.out.println(exc2.getMessage());
                        }
                    } else
                        System.out.println(next);

                }
                // User specifies uploading file to the server
                else if ( in .equals("upload")) {
                    pushPull = false;
                    pullPush = false;
                    ArrayList < File > list = eligibleToPush();
                    String[] names = new String[list.size()];

                    // Listing the available files in the client directory
                    System.out.println("\nThe following files are available for upload:");
                    for (int ii = 0; ii < list.size(); ii++) {
                        names[ii] = list.get(ii).getName();
                        System.out.println(names[ii]);
                    }

                    // User inputs the name of the file to upload
                    System.out.println("\nType a file to upload then hit Enter: \n");
                    String filename = read.nextLine();

                    // Checking that the file exists
                    if (Arrays.asList(names).contains(filename)) {
                        String encFileName = filename + ".encrypted";
                        File encryptedFileUpload = new File("client/" + encFileName);

                        try {
                            AESUtils.encrypt(createKey(filename), new File("client/" + filename), encryptedFileUpload);
                        } catch (AESException exc) {
                            System.out.println("Error encrypting.");
                            //exc.printStackTrace();
                        }

                        // Upload the file to the server
                        write.println("exists");
                        // Send filename to server
                        write.println(filename);

                        // Calculate and send the file size to the server
                        write.println(encryptedFileUpload.length());

                        upload(encFileName, (int) encryptedFileUpload.length(), s3);
                        encryptedFileUpload.delete();
                        System.out.println("\nFile " + filename + " successfully uploaded.\n");
                    }
                    // File chosen to upload does not exist
                    else {
                        write.println("notExists");
                        System.out.println("\nERROR: File " + filename + " does not exist\n");
                    }
                } else if ( in .equals("share")) {
                    pushPull = false;
                    pullPush = false;
                    String next = reader.readLine();
                    String shareFile = "";
                    String user = "";

                    if (next.equals("The following files are available to share: ")) {
                        System.out.println(next);
                        shareFile = read.nextLine();
                        write.println(shareFile);
                        user = read.nextLine();
                        write.println(user);
                    } else
                        System.out.println(next);

                    syncKey(shareFile, user);
                }
                // User specifies closing the client connection
                else if ( in .equals("close")) {
                    fis.close();
                    dos.close();
                    return;
                }

                pushPull = true;

            }

        } catch (IOException e) {
            //e.printStackTrace();
            System.out.println("\nClient connection has closed.");
        } catch (NullPointerException n) {
            //n.printStackTrace();
            System.out.println("\nClient connection has closed.");
        } catch (Exception ee) {
            //ee.printStackTrace();
            System.out.println("\nClient connection has closed.");
        }

        return;

    }

}