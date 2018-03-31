import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.*;
import javax.net.ssl.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.math.*;
import com.sun.net.ssl.*;
import com.sun.net.ssl.internal.ssl.Provider;

public class Connection implements Runnable {

    public static SSLSocket cs;
    public static SSLSocket cs2;
    public static SSLSocket cs3;
    public static SSLSocket cs4;

    public static DataInputStream dis;
    public static FileOutputStream fos;
    public static DataOutputStream dos;
    public static FileInputStream fis;
    public static BufferedReader reader;
    public static PrintWriter write;
    public static String userDir = "";
    public static String activeDirectory = System.getProperty("user.dir");
    public static boolean pullPush;
    public static boolean pushPull;
    public static boolean syncFailed = false;

    public Connection(SSLSocket cs4) { 
        this.cs4 = cs4;
    }

    // Send file contents from the server to the client ("download")
    public static void servDownload(String path, String file, int size, SSLSocket ccs) throws IOException {
        dos = new DataOutputStream(ccs.getOutputStream());
        fis = new FileInputStream(path + file);
        byte[] buffer = new byte[size];

        while (fis.read(buffer) > 0) {
            dos.write(buffer);
        }
    }

    // Receive file contents from the client ("upload")
    public static void servUpload(String path, String file, int size, boolean verbose, SSLSocket ccs) throws IOException {
        dis = new DataInputStream(ccs.getInputStream());
        fos = new FileOutputStream(path + file);
        byte[] buffer = new byte[size];
        int bytesRead = 0;

        // Read data from socket input stream into the byte array 
        bytesRead = dis.read(buffer, 0, Math.min(buffer.length, 25000));

        // Write data from the byte array into the file
        fos.write(buffer, 0, bytesRead);

        if (verbose) {
            System.out.println("Uploading " + file + " to the server");
            System.out.println("Writing " + bytesRead + " bytes to the server");
            System.out.println("File " + file + " successfully uploaded.");
        }

    }

    // Determine if the username/password combination is that of a valid user
    public boolean validUser(String user, String pass) throws IOException, FileNotFoundException {
        BufferedReader read = new BufferedReader(new FileReader("server/users.txt"));
        String line;
        String username = "";
        String password = "";

        // Read in data from the users server database 
        while ((line = read.readLine()) != null) {
            username = "";
            int ii;
            for (ii = 0; ii < line.length(); ii++) {
                if (line.charAt(ii) != '=')
                    username += line.charAt(ii);
                else
                    break;
            }
            password = "";
            for (int jj = ii + 1; jj < line.length(); jj++)
                password += line.charAt(jj);

            // Valid login attempt if the server database entry matches the user login information
            if (username.equals(user) && password.equals(pass))
                return true;
        }

        return false;

    }

    // Return an arraylist of valid users of the server
    public ArrayList < String > users() throws IOException {
        BufferedReader read = new BufferedReader(new FileReader("server/users.txt"));
        String line;
        String username = "";
        ArrayList < String > users = new ArrayList < String > ();


        while ((line = read.readLine()) != null) {
            username = "";
            int ii;
            for (ii = 0; ii < line.length(); ii++) {
                if (line.charAt(ii) != '=')
                    username += line.charAt(ii);
                else
                    break;
            }
            users.add(username);
        }

        return users;

    }

    // Pull synchronization changes from the client
    public static void pullSync(BufferedReader br, PrintWriter w) throws IOException, NumberFormatException {
        int totalFiles;
        String name;
        int size;
        int uploadSize;
        File directory = new File("server/" + userDir + "SYNC");
        File[] currentFiles = directory.listFiles();
        String[] currentNames = new String[currentFiles.length];
        ArrayList < String > toUpload = new ArrayList < String > ();
        ArrayList < String > mostRecentFiles = new ArrayList < String > ();

        // String filenames of each file in the directory
        for (int ii = 0; ii < currentFiles.length; ii++) {
            currentNames[ii] = currentFiles[ii].getName();
        }

        // Get total number of files from client
        try {
            totalFiles = Integer.parseInt(br.readLine());
        } catch (NumberFormatException nff) {
            totalFiles = 0;
        }

        if (totalFiles > 0) {
            // Update information in data storage
            for (int ii = 0; ii < totalFiles; ii++) {
                name = br.readLine();
                size = Integer.parseInt(br.readLine());
                mostRecentFiles.add(name);

                // Determine which files need to be uploaded to the server for synchronization
                if (!Arrays.asList(currentNames).contains(name))
                    toUpload.add(name);
                else if (size != (int) new File("server/" + userDir + "SYNC/" + name).length())
                    toUpload.add(name);
            }

            // Delete any files that have been deleted on the client
            for (File f: currentFiles) {
                if (!mostRecentFiles.contains(f.getName()))
                    f.delete();
            }

            // Send client the number of files to be uploaded
            w.println(toUpload.size());

            // Upload files to make necessary synchronizations
            for (int ii = 0; ii < toUpload.size(); ii++) {
                w.println(toUpload.get(ii));
                uploadSize = Integer.parseInt(br.readLine());
                servUpload("server/" + userDir + "SYNC/", toUpload.get(ii), uploadSize, false, cs2);
            }

            // Synchronize shared files in the server with other users
            syncSharedFiles();
        }

    }

    public static void share(String user, String userDir, String shareFile) throws IOException {
        Writer addToFile = new BufferedWriter(new FileWriter("server/sharedFiles.txt", true));

        Path from = Paths.get("server/" + userDir + "/" + shareFile);
        Path to = Paths.get("server/" + user + "/" + shareFile);
        CopyOption[] options = new CopyOption[] {
            StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        };

        Files.copy(from, to, options);

        addToFile.append(userDir + "/" + shareFile + "=" + user + "/" + shareFile + "\n");
        addToFile.close();
    }

    // Document and synchronize files that have been shared with other users within the server
    public static void syncSharedFiles() throws IOException {
        String line;
        String fileFrom = "";
        String fileTo = "";
        Path fromPath;
        Path toPath;
        CopyOption[] options = new CopyOption[] {
            StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        };

        try {
            BufferedReader read = new BufferedReader(new FileReader("server/sharedFiles.txt"));

            // Get the source of the shared file
            while ((line = read.readLine()) != null) {
                int ii;
                fileFrom = "";
                fileTo = "";
                for (ii = 0; ii < line.length(); ii++) {
                    if (line.charAt(ii) != '=')
                        fileFrom += line.charAt(ii);
                    else
                        break;
                }

                // Get the destination of the shared file 
                for (int jj = ii + 1; jj < line.length(); jj++)
                    fileTo += line.charAt(jj);

                fromPath = Paths.get("server/" + fileFrom);
                toPath = Paths.get("server/" + fileTo);

                // Synchronize files
                Files.copy(fromPath, toPath, options);
            }
        } catch (FileNotFoundException fnfe) {} catch (NoSuchFileException nsfe) {}
    }

    // Push synchronization changes form the server to the client
    public static void pushSync(BufferedReader br, PrintWriter w) throws IOException, NoSuchAlgorithmException {
        String nameToDownload;
        int numToDownload;
        int sizeOfDownload;
        // Create the sync directory
        File directory = new File("server/" + userDir + "SYNC");
        if (!directory.exists())
            directory.mkdir();

        new File("server/" + userDir + "SYNC/.DS_Store").delete();

        File[] list = directory.listFiles();

        if ((list != null) && (list.length > 0)) {
            String[] names = new String[list.length];
            // Push number of files in the synchronization folder 			
            w.println(list.length);
            if (list.length > 0) {
                for (int ii = 0; ii < list.length; ii++) {
                    // Push file name
                    w.println(list[ii].getName());
                    // Push file size
                    w.println(list[ii].length());
                }

                // Number of files to download to the client
                try {
                    numToDownload = Integer.parseInt(br.readLine());
                } catch (NumberFormatException num) {
                    numToDownload = 0;
                }
                // Download necessary files to client for synchronization purposes
                for (int ii = 0; ii < numToDownload; ii++) {
                    nameToDownload = br.readLine();
                    sizeOfDownload = (int) new File("server/" + userDir + "SYNC/" + nameToDownload).length();
                    w.println(sizeOfDownload);
                    servDownload("server/" + userDir + "SYNC/", nameToDownload, sizeOfDownload, cs2);
                }
            }
        } else
        {
            w.println(0);
        }
    }

    public void run() {

        String username = "";
        String password = "";
        boolean authenticated;
        int count;
        System.out.println("Hawkins server is running.");

        try {
            // Instantiate sockets for user input/output between client and server and file synchronization
            SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket ss = (SSLServerSocket) ssf.createServerSocket(1988 + (10 * (FileServer.iii - 1)));
            SSLServerSocket ss2 = (SSLServerSocket) ssf.createServerSocket(1990 + (10 * (FileServer.iii - 1)));
            SSLServerSocket ss3 = (SSLServerSocket) ssf.createServerSocket(1992 + (10 * (FileServer.iii - 1)));
            // Reader and writer for authentication
            BufferedReader reader4 = new BufferedReader(new InputStreamReader(cs4.getInputStream()));
            PrintWriter write4 = new PrintWriter(cs4.getOutputStream(), true);

            authenticated = false;
            count = 0;

            // Nested Class sync syncronizes different clients of the same user
            // *********************************************************
            class Sync extends Thread {
                public void run() {
                    try {
                        while (true) {
                            BufferedReader reader2 = new BufferedReader(new InputStreamReader(cs2.getInputStream()));
                            PrintWriter write2 = new PrintWriter(cs2.getOutputStream(), true);

                            if (pushPull) {
                                pushSync(reader2, write2);
                                pullSync(reader2, write2);
                                pullPush = true;
                                pushPull = false;
                            } else if (pullPush) {
                                String received = reader2.readLine();

                                if (received.equals("change")) {
                                    System.out.println("change server side");
                                    pullSync(reader2, write2);
                                }

                                pushSync(reader2, write2);
                            }

                        }
                    } catch (Exception e) {
                        System.out.println("Sync failed. Please restart client/server.");
                        syncFailed = true;
                        e.printStackTrace();
                    }
                }
            }
            // *********************************************************


            // Authentication of the client (username and password)
            while (!authenticated) {
                write4.println("Enter username: \r");
                username = reader4.readLine().trim();
                write4.println("Enter password: \r");
                password = reader4.readLine().trim();
                if (validUser(username, password))
                    break;
                count++;

                // Limited number of login attempts
                if (count >= 5) {
                    write4.println("Too many attempts. Closing.");
                    return;
                }
                write4.println("Incorrect username or password. Try again.\r");
            }

            // Alert client that it has successfully been authenticated 
            write4.println("authenticated");
            // Send the client the number of connections so it can generate unique ports
            write4.println(FileServer.iii);

            cs = (SSLSocket) ss.accept();
            cs2 = (SSLSocket) ss2.accept();
            cs3 = (SSLSocket) ss3.accept();

            // Global variable of active useraccount logged in. 
            userDir = username;

            // Reader and writer of client/server communication 
            reader = new BufferedReader(new InputStreamReader(cs.getInputStream()));
            write = new PrintWriter(cs.getOutputStream(), true);

            // Directory of storage that only this specific user can access on the cloud
            if (!(new File("server/" + userDir).exists())) {
                new File("server/" + userDir).mkdir();
            }

            pushPull = true;
            pullPush = false;

            // Begin synchronization process
            Sync sync = new Sync();
            sync.start();


            write.println("\n***************************************************************\r");
            write.println("* Connected to the Hawkins Server                             *\r");
            write.println("***************************************************************\n\r");

            while (true) {

                write.println("* Type 'upload' to upload file to the server               *\r");
                write.println("* Type 'download' to download file from the server         *\r");
                write.println("* Type 'share' to share access to a file with another user *\r");
                write.println("* Type 'close' to end the session                          *\r\n");

                // Get client input
                String command = reader.readLine().trim();

                // User wants to download from the server to the client
                if (command.equals("download")) {
                    pushPull = false;
                    pullPush = false;
                    String activeDirectory = System.getProperty("user.dir");
                    File directory = new File(activeDirectory + "/server/" + userDir);
                    new File("server/" + userDir + "SYNC/.DS_Store").delete();

                    File[] list = directory.listFiles();
                    String[] names = new String[list.length];

                    if (list.length > 0) {
                        // Get the files available for download in the server directory
                        write.println("\nThe following files are available for download: \r\n");

                        for (int ii = 0; ii < list.length; ii++) {
                            names[ii] = list[ii].getName();
                            write.println(names[ii] + "\r");
                        }

                        write.println("\nType a file to download then hit Enter:");

                        String filename = reader.readLine().trim();
                        // The file being downloaded exists in the directory
                        if (Arrays.asList(names).contains(filename)) {

                            File size2 = new File("server/" + userDir + "/" + filename);
                            int fileLength = (int) size2.length();
                            write.println("File is available to download.\r\n");
                            servDownload("server/" + userDir + "/", filename, fileLength, cs3);
                        }

                        // File does not exist
                        else {
                            write.println("\nERROR: File " + filename + " does not exist.\r\n");
                        }
                    }
                    // No files available for download
                    else {
                        write.println("\nNo files available to download.\r\n");
                    }
                } else if (command.equals("upload")) {
                    // Get the file name the user inputs in the client module
                    pushPull = false;
                    pullPush = false;
                    String output = reader.readLine().trim();
                    if (output.equals("exists")) {
                        String file = reader.readLine().trim();
                        int size = Integer.parseInt(reader.readLine().trim());
                        // Server receives the file from the client
                        servUpload("server/" + userDir + "/", file, size, true, cs3);
                    }
                } else if (command.equals("share")) {
                    pushPull = false;
                    pullPush = false;
                    String shareFile;
                    String activeDirectory = System.getProperty("user.dir");
                    File directory = new File(activeDirectory + "/server/" + userDir);
                    File[] list = directory.listFiles();
                    String[] names = new String[list.length];
                    ArrayList < String > userList = users();
                    String user = "";

                    if (list.length > 0) {
                        // Get the files available for download in the server directory
                        write.println("\nThe following files are available to share: \r\n");

                        for (int ii = 0; ii < list.length; ii++) {
                            names[ii] = list[ii].getName();
                            write.println(names[ii] + "\r");
                        }

                        write.println("\nType a file to share then hit Enter:");
                        shareFile = reader.readLine().trim();

                        // The file being shared exists in the directory
                        if (Arrays.asList(names).contains(shareFile)) {

                            write.println("File is available to share.\r\n");
                            write.println("\nThe following users are available: \r\n");
                            for (int ii = 0; ii < userList.size(); ii++) {
                                write.println(userList.get(ii) + "\r");
                            }

                            write.println("\nType the user with whom to share the file " + shareFile + ".\r\n");

                            user = reader.readLine().trim();

                            if (userList.contains(user)) {
                                share(user, userDir, shareFile);
                            } else {
                                write.println("User does not exist.");
                            }
                        }
                        // File does not exist
                        else {
                            write.println("\nERROR: File " + shareFile + " does not exist.\r\n");
                        }
                    }
                    // No files available for download
                    else {
                        write.println("\nNo files available to share.\r\n");
                    }


                } else if (command.equals("close")) {
                    return;
                }

                pullPush = true;

            }


        } catch (IOException e) {
            System.out.println("Client thread has been closed.");
        } catch (NullPointerException n) {
            System.out.println("Client thread has been closed.");
        } catch (NumberFormatException nf) {
            System.out.println("Client thread has been closed.");
        } catch (Exception ee) {
            System.out.println("Client thread has been closed.");
        }

    }

}