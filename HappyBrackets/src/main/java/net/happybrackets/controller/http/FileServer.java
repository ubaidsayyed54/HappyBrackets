package net.happybrackets.controller.http;

/**
 *  FileServer implements a simple http server daemon to allow attached PIs to request files from the controller.
 *
 *  TODO: Create an interface for adding files to our file server
 *
 * Created by Sam on 26/04/2016.
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import net.happybrackets.core.ControllerConfig;
import fi.iki.elonen.NanoHTTPD;

public class FileServer extends NanoHTTPD {

    protected static String readFile(String path, String encoding) {
        Scanner scanner = null;
        String text = null;
        try {
            scanner = new Scanner( new File(path), encoding );
            text = scanner.useDelimiter("\\A").next();
        }
        catch (FileNotFoundException e) {
            System.err.println("Unable to access: " + path);
            //e.printStackTrace();
        }

        if (scanner != null) {
            scanner.close();
        }

        return text;
    }
    protected static String readFile(String path, String encoding, String defaultSuffix) {
        String text = readFile(path, encoding);
        if (text == null) {
            System.out.println("Trying default: " + path + defaultSuffix);
            text = readFile(path + defaultSuffix, encoding);
        }

        return text;
    }

    private ControllerConfig config;
    //server status:
    private static Response.IStatus statusOK = new Response.IStatus() {
        @Override
        public String getDescription() { return "200 OK\n"; }
        @Override
        public int getRequestStatus() {  return 200; }
    };
    private static Response.IStatus statusError = new Response.IStatus() {
        @Override
        public String getDescription() { return "500 Error\n"; }
        @Override
        public int getRequestStatus() {  return 500; }
    };

    public FileServer(ControllerConfig config) throws IOException {
        super(config.getControllerHTTPPort());
        this.config = config;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browser to http://localhost:" + config.getControllerHTTPPort() + "/ \n");
    }

    public static void main(String[] args) {
        try {
            new FileServer(new ControllerConfig());
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
//        String msg = "<html><body><h1>Hello server</h1>\n";
//        Map<String, String> parms = session.getParms();
//        if (parms.get("username") == null) {
//            msg += "<form action='?' method='get'>\n  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n";
//        } else {
//            msg += "<p>Hello, " + parms.get("username") + "!</p>";
//        }
//        return newFixedLengthResponse(msg + "</body></html>\n");
        String deviceConfig = "config/device-config.json";
        String response = readFile(deviceConfig, "utf8", ".default");

        if (response != null) {
            System.out.println("Serving file: " + deviceConfig);
            return newFixedLengthResponse(statusOK, "text/json", response);
        }
        else {
            System.out.println("Serving 500 error");
            return newFixedLengthResponse(statusError, "text/plain; charset=UTF-8", "Unable to read file: " + deviceConfig + "\n");
        }
    }
}
