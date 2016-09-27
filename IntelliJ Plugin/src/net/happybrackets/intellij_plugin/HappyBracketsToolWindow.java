package net.happybrackets.intellij_plugin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import net.happybrackets.controller.http.FileServer;
import net.happybrackets.controller.network.ControllerAdvertiser;
import net.happybrackets.controller.network.DeviceConnection;
import net.happybrackets.core.BroadcastManager;
import net.happybrackets.core.Device;
import net.happybrackets.core.Synchronizer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Created by ollie on 22/04/2016.
 *
 * Performs initialisation of static settings and components (eg {@link IntelliJControllerConfig} and
 * {@link ControllerAdvertiser}) and then launches the GUI {@link IntelliJPluginGUIManager}
 *
 * Thank god for this site: http://alblue.bandlem.com/2011/08/intellij-plugin-development.html
 * Not much else out there about setting up an IntelliJ gui tool!
 *
 * TODO:
 *    * redesign gui, perhaps with FXML.
 *    * deal with network connection issues, including being on two networks at the same time.
 *    * reload when moved (e.g., when moved from pinned to floating mode, the content currently disappears).
 *    * deal with finding the config dolder, and also setting it (thus know where the plugin folder lives).
 *    * deal with finding the compositions folder. It is possible we can make this context aware -- i.e., it looks at the build folder for the current project.
 *
 */
@SuppressWarnings("ALL")
public class HappyBracketsToolWindow implements ToolWindowFactory {

    static boolean staticSetup = false;
    static String currentConfigString;
    static IntelliJPluginSettings settings;
    static DeviceConnection deviceConnection;
    static Synchronizer synchronizer;                               //runs independently, no interaction needed
    static private FileServer httpServer;
    static protected IntelliJControllerConfig config;
    static protected ControllerAdvertiser controllerAdvertiser;     //runs independently, no interaction needed
    static protected BroadcastManager broadcastManager;
    private JFXPanel jfxp;
    private Scene scene;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        System.out.println("*** HappyBrackets IntelliJ Plugin launching ***");
        Device.getInstance();   //forces Device's network init to happen
        Platform.setImplicitExit(false);    //<-- essential voodoo (http://stackoverflow.com/questions/17092607/use-javafx-to-develop-intellij-idea-plugin-ui)

        if(!staticSetup) {          //only run this stuff once per JVM
            System.out.println("Running static setup (first instance of HappyBrackets)");
            String projectDir = project.getBaseDir().getCanonicalPath();

            System.out.println("Loading plugin settings from " + IntelliJPluginSettings.getDefaultSettingsLocation());
            settings = IntelliJPluginSettings.load();

            //TODO this is still buggy. We are doing this statically meaning it works only for the first loaded project.
            String configFilePath = settings.getString("controllerConfigPath");
            if (configFilePath == null) {
                configFilePath = getDefaultControllerConfigPath();
                settings.set("controllerConfigPath", configFilePath);
            }

            //String configFilePath = projectDir + "/config/controller-config.json";
            if(new File(configFilePath).exists()) System.out.println("Found config file.");
            //all of the below concerns the set up of singletons

            try {
                setConfigFromFile(configFilePath);

                // Save settings on exit.
                Runtime.getRuntime().addShutdownHook(new Thread()  {
                    public void run() {
                        settings.save();
                    }
                });
            }
            catch (IOException e) {
                System.err.println("Could not read the configuration file at " + configFilePath);
                config = new IntelliJControllerConfig();
            }

            //test code: you can create a test pi if you don't have a real pi...
    	    deviceConnection.createTestDevice();
    	    deviceConnection.createTestDevice();
            //using synchronizer is optional, TODO: switch to control this, leave it on for now
            synchronizer = Synchronizer.getInstance();
            staticSetup = true;
        } else {
            System.out.println("HappyBrackets static setup already completed previously.");
        }

        IntelliJPluginGUIManager guiManager = new IntelliJPluginGUIManager(project);
        jfxp = new JFXPanel();
        scene = guiManager.setupGUI();
        jfxp.setScene(scene);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(jfxp, "", false);
        toolWindow.getContentManager().addContent(content);
    }


    /**
     * Loads the specified configuration file, resets the statically stored
     * configuration to the newly loaded config, and recreates or resets the
     * relevant components (DeviceConnection, FileServer, ControllerAdvertiser).
     *
     * TODO This will not update the IntelliJPluginGUIManager(s), need to
     * determine how to handle this (do we support multiple GUIs? Should they
     * have project specific or global settings?) At the moment we assume the
     * IntelliJPluginGUIManager initiated loading the new config and will
     * reset itself accordingly.
     *
     * @param configFilePath Path to the config file.
     *
     * @throws IOException If there is an error reading the given config file.
     */
    static void setConfigFromFile(String configFilePath) throws IOException {
        File newConfigFile = new File(configFilePath);
        if (!newConfigFile.isFile() || !newConfigFile.canRead()) {
            throw new IllegalArgumentException("Specified configuration file does not exist or is not readable, path is: " + configFilePath);
        }

        String configJSON = new Scanner(newConfigFile).useDelimiter("\\Z").next();
        System.out.println("Loaded config:\n" + configJSON);
        setConfig(configJSON, newConfigFile.getParent());
    }


    /**
     * Resets the statically stored configuration to the provided config, and
     * recreates or resets the relevant components (DeviceConnection,
     * FileServer, ControllerAdvertiser).
     *
     * TODO This will not update the IntelliJPluginGUIManager(s), need to
     * determine how to handle this (do we support multiple GUIs? Should they
     * have project specific or global settings?) At the moment we assume the
     * IntelliJPluginGUIManager initiated loading the new config and will
     * reset itself accordingly.
     *
     * @param configJSON The new configuration, in JSON format.
     * @param configDir The folder containing the configuration, if the configuration was loaded from a file (null otherwise).
     *
     * @throws IllegalArgumentException If there is an error loading or parsing the given config file.
     */
    static void setConfig(String configJSON, String configDir) {
        try {
            config = IntelliJControllerConfig.loadFromString(configJSON);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Could not parse specified configuration.", e);
        }

        currentConfigString = configJSON;

        config.setConfigDir(configDir);

        // If a custom known devices path has been loaded/saved previously, reload it and apply/override that possibly specified in the controller config file.
        String knownDevicesPath = HappyBracketsToolWindow.getSettings().getString("knownDevicesPath");
        if (knownDevicesPath == null) {
            // Otherwise get default known devices file.
            knownDevicesPath = HappyBracketsToolWindow.getDefaultKnownDevicesPath();
        }
        config.setKnownDevicesFile(knownDevicesPath);

        // Dispose of previous components if necessary.
        if (httpServer != null) {
            System.out.println("Stopping FileServer.");
            httpServer.stop();
        }
        if (controllerAdvertiser != null) {
            System.out.println("Stopping ControllerAdvertiser");
            controllerAdvertiser.stop();
        }
        if (broadcastManager != null) {
            System.out.println("Disposing of BroadcastManager");
            broadcastManager.dispose();
        }
        if (deviceConnection != null) {
            System.out.println("Disposing of DeviceConnection");
            deviceConnection.dispose();
        }

        System.out.println(config.getCompositionsPath());

        //set up config relevant directories
        deviceConnection = new DeviceConnection(config);

        //setup controller broadcast
        System.out.println("Starting ControllerAdvertiser");
        broadcastManager = new BroadcastManager(config);
        controllerAdvertiser = new ControllerAdvertiser(broadcastManager, config.getMyHostName());;
        controllerAdvertiser.start();

        //setup http httpServer
        try {
            httpServer = new FileServer(config);
        } catch (IOException e) {
            System.err.println("Unable to start http httpServer!");
            e.printStackTrace();
        }
    }


    public static IntelliJPluginSettings getSettings() {
        return settings;
    }


    /**
     * Returns the JSON String used to create the current {@link IntelliJControllerConfig} for this HappyBracketsToolWindow.
     */
    public static String getCurrentConfigString() {
        return currentConfigString;
    }

    /**
     * Returns the absolute path to the plugin folder.
     */
    public static String getPluginLocation() {
        return PluginManager.getPlugin(
                PluginId.getId("net.happybrackets.intellij_plugin.HappyBracketsToolWindow")
        ).getPath().getAbsolutePath().toString();
    }

    public static String getDefaultControllerConfigPath() {
        return getPluginLocation() + "/classes/config/controller_config.json";
    }

    public static String getDefaultKnownDevicesPath() {
        return getPluginLocation() + "/classes/config/known_devices";
    }
}
