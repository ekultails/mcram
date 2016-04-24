//package mcram;
import java.io.*;
import java.io.IOException; // I/O error exception handling
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption; // file copying
import java.util.Arrays;
import java.util.concurrent.TimeUnit; // TimeUnit sleep functions resides here
import java.util.Scanner;

class Mcramd {

    public static Process MinecraftServer = null;
    public static String java_binary = System.getProperty("java.home") +
                                       "/bin/java";
    public static boolean serverStarted = false;

    public static String readSock(String fileName) {
    
        File fileToRead = new File(fileName);
        FileReader fileReader = null;
        
        try 
        {
            fileReader = new FileReader(fileToRead);
        } catch (IOException e) 
        {
            e.printStackTrace();
        }
            
        BufferedReader fileBufferReader = new BufferedReader(fileReader);
        String returnSocket = null; // initlalize the variable before
                                    // we enter the try/catch statement
    
        try {
            // this will read the first/top line only
            returnSocket = fileBufferReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return returnSocket;
    }

    public static void mountRAMMac(String mountRAM, String destinationDir) {
        // convert from MB to block size;
        // more specifically, convert to kilobytes then bytes and finally
        // divide by bytes in 1 RAM sector (512) 
        // to get the total number of sectors
        int mountRAMSectors = Integer.parseInt(mountRAM) * 1024 * 1024 / 512;
        String mountCmd = ("hdiutil attach -nomount ram://" + 
                           Integer.toString(mountRAMSectors));
        File runDir = new File("/tmp");
        execCmd(mountCmd, runDir);
    }

    public static void mountRAMLinux(String mountRAM, String destinationDir) {
        String mountCmd = ("mount -t ramfs -o defaults,noatime,size=" +
                          mountRAM + "M ramfs " + destinationDir);
        File runDir = new File("/tmp");
        execCmd(mountCmd, runDir);
    }
    
    public static void mountRAMWindows() {
        System.out.println("Stub");
        
    }

    // return the contents of a directory
    public static File[] dir(File dirname) {
        File[] listOfFiles = dirname.listFiles();
        return listOfFiles;
    }

    // copy files and folders recursively
    public static void cp(File src_dir, File dst_dir) throws IOException {
        Path src_file = null;
        Path dst_file = null;    
        
        if ((! src_dir.isDirectory() && ! dst_dir.isDirectory())) {
            Files.copy(src_dir.toPath(), dst_dir.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } else if (src_dir.isDirectory())   {
            
            if (! Files.exists(dst_dir.toPath())) {
                Files.copy(src_dir.toPath(), dst_dir.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
            File[] listOfFiles = dir(src_dir);
   
            for(File filename : listOfFiles) {
                src_file = filename.toPath();
                dst_file = new File(dst_dir.getAbsolutePath() + "/" +  filename.getName()).toPath();

                if (filename.isDirectory()) {
                
                // copy the directory and it's attributes
                if (! Files.exists(dst_file)) {
                    Files.copy(src_file, dst_file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                
                File[] subDir = dir(filename);
                    
                    // copy the sub directories
                    for(File filetmp : subDir) {
                    
                        // create the new source and destination file names
                        // for the sub directories
                        File src_subFile = new File(src_dir.getAbsolutePath() + "/" + filename.getName() +
                                                "/" + filetmp.getName());
                        File dst_subFile = new File(dst_dir.getAbsolutePath() + "/" + filename.getName() + 
                                                    "/" + filetmp.getName());
                        // copy the sub directory and it's contents (inception!)
                        cp(src_subFile, dst_subFile);
                    } 
                } else {
                    Files.copy(src_file, dst_file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }       
            }
        }
    }
    
    // send standard input to a process
    public static void stdin(Process p, String command) {
        // a Scanner is used to push standard input to the process   
        Scanner stdin_scanner = new Scanner(System.in);
        PrintWriter stdin = new PrintWriter(MinecraftServer.getOutputStream());
        stdin.println(command);
        stdin.flush();  
    }

    // run a native CLI command
    public static Process execCmd(String runCmd, File runDir) 
    {
        Process p = null; /* a variable must be defined outside of the 
                             try/catch scope and be assigned a value of at least null */
    
        try {
            // we are not using any special environment variables
            String defaultEnv[] = {""}; 
            p = Runtime.getRuntime().exec(runCmd, defaultEnv, runDir);
        } catch(IOException e) {
            e.printStackTrace();
        }
        return p;
    }

    public static void startMinecraftServer(String runDir, String mcJar, String javaExecRAM) {
        
        // Minecraft needs to be run from the directory that the eula.txt is in
        // this is where the minecraft_server*.jar should exist
        File runDirFile = new File(runDir);
        // the "runCmd" variable should end up looking similar to this:
        // java -Xmx1024M -Xms1024M -jar minecraft_server.jar nogui
        String runCmd = (java_binary + " -Xmx" + javaExecRAM + "M -Xms" + 
                         javaExecRAM + "M -jar " + mcJar + " nogui");
        System.out.println("deubg - runCmd: " + runCmd);
        MinecraftServer = execCmd(runCmd, runDirFile);
        serverStarted = true;
    }

    public static void main(String[] args) {
        // we will be creating two background threads
        //
        // 1st thread
        // start the Minecraft server and 
        // sync it back to the disk over a specified amount of time
        Runnable startAndSync = new Runnable() {
            public void run() {
                // initalize the object from our class
                Mcramd mcramd = new Mcramd();
                    
                // first we want to start the Minecraft server (after mounting RAM)
               // File runDir = new File("C:/path/to/minecraft/server/");
               //  String runCmd = (java_prefix + "/bin/java" + 
                //                 " -jar C:/path/to/minecraft/server/minecraft_server.1.9.jar");
               
                    
                // this socket is valid for Linux and Mac only
                String socket = "/tmp/mcramd.sock"; 
                String socketText = mcramd.readSock(socket);
                String[] sockSplit = socketText.split(",");

                // read the socket until it recieves the start command
                while (sockSplit[0].contains("mcramd:start") == false) {
                    socketText = mcramd.readSock(socket);
                    sockSplit = socketText.split(",");
                    
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }                        
                }
                
                // full mcramd:start options:
                // (1) <mount_directory>, (2) <tmpfs_size_in_MB>, 
                // (3) <source_directory>, (4) <java_RAM_exec_size_in_MB>,
                // (5) <sync_time>, (6) <operating_system_name>
                String minecraftFullJarPath = sockSplit[3];
                String destinationDir = sockSplit[1];
                String[] syncTime = sockSplit[5].split(":");
                // extract the syncing time (in minutes) variable
                // and convert it to an integer
                int syncTimeInt = Integer.parseInt(syncTime[1]);
                
                if ( ! minecraftFullJarPath.endsWith(".jar")) {
                    System.out.println("No jar file provided.");
                    System.exit(1); 
                }
                
                String[] minecraftFullJarPathSplit = minecraftFullJarPath.split("/");
                // grab the last part of the split which should be
                // the Minecraft server jar file
                String minecraftJar = minecraftFullJarPathSplit[minecraftFullJarPathSplit.length - 1];
                String sourceDir = null;
                
                // get the full path to the Minecraft server's directory;
                // it should be each value in the array besides the last one
                for (int count = 0; count < minecraftFullJarPathSplit.length - 1; count++) {
                    if (count == 0) {
                        // the first entry should not start with
                        // a "/"
                        sourceDir = minecraftFullJarPathSplit[count];
                    } else {
                        sourceDir = sourceDir + "/" + minecraftFullJarPathSplit[count];
                    }
                }
                                    
                String mountRAM = sockSplit[2];
                String javaExecRAM = sockSplit[4];
                
                if (sockSplit[6].contains("linux")) {
                    mountRAMLinux(mountRAM, destinationDir);
                } else if (sockSplit[6].contains("mac")) {
                    System.out.println("Stub");
                    System.exit(1);
                } else if (sockSplit[6].contains("windows")) {
                    System.out.println("Stub");
                    System.exit(1);
                } else {
                    System.out.println("Unsupported operating system.");
                    System.exit(1);
                }
                
                // copy the files into the mounted RAM disk
                try {
                    cp(new File(sourceDir), new File(destinationDir));
                } catch(IOException e){
                    e.printStackTrace();
                }
                
                // finally, we start the Minecraft server
                mcramd.startMinecraftServer(destinationDir, minecraftJar, javaExecRAM);
            
                // 0 means MCRAM will not sync the server back to the disk
                if (syncTimeInt != 0) {
                    String command = null;
                    
                    while (true)
                    {
                        // save the server
                        stdin(MinecraftServer, "save all");
                        
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                        
                        // stop the saving to avoid corruption
                        stdin(MinecraftServer, "save-off");
                        
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                        
                        // copy the server back from RAM to the disk
                        try {
                            cp(new File(destinationDir), new File(sourceDir));
                        } catch(IOException e){
                            e.printStackTrace();
                        }
                        
                        // finally, turn saving back on again
                        stdin(MinecraftServer, "save-on");
                        stdin(MinecraftServer, "say debug saving works");
                
                        // wait this long before starting the loop again
                        try {
                            TimeUnit.MINUTES.sleep(syncTimeInt);
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }
            } 
        }; // end of the first Runnable thread method
        
        // 2nd thread
        // listen to a socket for further commands
        Runnable mcramListen = new Runnable() {
            public void run() {
                
                while (true) {
                    
                    if (serverStarted != true) {
                        
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    
                    } else {
                        break;  
                    }                                   
                }

                String command = "say world, thread 2";
                
                for (int count = 1; count < 5; count++) {
                    stdin(MinecraftServer, command);

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                        
                }
                         
            }
        }; 
                    
        // spawn MCRAMD off as a background thread
        Thread MCRAMStart = new Thread(startAndSync);
        MCRAMStart.start();
        Thread mcramListenStart = new Thread(mcramListen);
        mcramListenStart.start();
    }
}
