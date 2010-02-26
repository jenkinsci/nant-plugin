package hudson.plugins.nant;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import net.sf.json.JSONObject;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link NantBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #nantName})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 * 
 * @author kyle.sweeney@valtech.com
 * @author Justin Holzer (jsholzer@gmail.com)
 *
 */
public class NantBuilder extends Builder {

	/**
	 * A whitespace separated list of nant targets to be run
	 */
    private final String targets;
    
    /**
     * The location of the nant build file to run
     */
	private final String nantBuildFile;
	
    /**
     * Identifies {@link NantInstallation} to be used.
     */
    private final String nantName;
    
    /**
     * The properties to pass to the NAnt build 
     */
    private final String properties;
    
	/**
	 * When this builder is created in the project configuration step,
	 * the builder object will be created from the strings below.
	 * @param nantBuildFile	The name/location of the nant build fild
	 * @param targets Whitespace separated list of nant targets to run
	 * @param properties property definitions (in Java properties format)
	 */
    @DataBoundConstructor
    public NantBuilder(String nantBuildFile,String nantName, String targets, String properties) {
    	super();
    	if(nantBuildFile==null || nantBuildFile.trim().length()==0)
    		this.nantBuildFile = "";
    	else
    		this.nantBuildFile = nantBuildFile;
    	
    	this.nantName = nantName;
    	
    	if(targets == null || targets.trim().length()==0)
    		this.targets = "";
    	else
    		this.targets = targets;	
    	
    	this.properties = Util.fixEmptyAndTrim(properties);
    }
    
    /**
     * Gets the NAnt to invoke,
     * or null to invoke the default one.
     */
    public NantInstallation getNant() {
        for( NantInstallation i : DESCRIPTOR.getInstallations() ) {
            if(nantName!=null && i.getName().equals(nantName))
                return i;
        }
        return null;
    }

    /**
     * We'll use these from the <tt>config.jelly</tt>.
     */
    public String getTargets() {
        return targets;
    }
    public String getNantBuildFile(){
    	return nantBuildFile;
    }
    public String getNantName(){
    	return nantName;
    }
    
    public String getProperties()
    {
    	return this.properties;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        
        VariableResolver<String> vr = build.getBuildVariableResolver();
        
        String execName;
        if(launcher.isUnix())
            execName = "nant";
        else
            execName = "NAnt.exe";

        //Get the path to the nant installation
        NantInstallation ni = getNant();
        if(ni==null) {
            args.add(execName);
        } else {
            args.add(ni.getExecutable(launcher));
        }
        
        //If a nant build file is specified, then add it as an argument, otherwise
        //nant will search for any file that ends in .build
        if(nantBuildFile != null && nantBuildFile.trim().length() > 0){
        	args.add("-buildfile:"+nantBuildFile);
        }
        
        args.addKeyValuePairsFromPropertyString("-D:", properties, vr);
        
        //Remove all tabs, carriage returns, and newlines and replace them with
        //whitespaces, so that we can add them as parameters to the executable
        String normalizedTarget = targets.replaceAll("[\t\r\n]+"," ");
        if(normalizedTarget.trim().length()>0)
        	args.addTokenized(normalizedTarget);
        
        //According to the Ant builder source code, in order to launch a program 
        //from the command line in windows, we must wrap it into cmd.exe.  This 
        //way the return code can be used to determine whether or not the build failed.
        if(!launcher.isUnix()) {
            args.add("&&","exit","%%ERRORLEVEL%%");
            
            // From hudson.tasks.Ant:
            //
            // on Windows, proper double quote handling requires extra surrounding quote.
            // so we need to convert the entire argument list once into a string,
            // then build the new list so that by the time JVM invokes CreateProcess win32 API,
            // it puts additional double-quote. See issue #1007
            // the 'addQuoted' is necessary because Process implementation for Windows (at least in Sun JVM)
            // is too clever to avoid putting a quote around it if the argument begins with "
            // see "cmd /?" for more about how cmd.exe handles quotation.
            args = new ArgumentListBuilder().add("cmd.exe", "/C").addQuoted(args.toStringWithQuote());
        }

        //Try to execute the command
    	listener.getLogger().println("Executing command: "+args.toString());
    	Map<String,String> env = build.getEnvironment(listener);
        try {
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(build.getModuleRoot()).join();
            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace( listener.fatalError("command execution failed") );
            return false;
        }
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Descriptor for {@link NantBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    public static final class DescriptorImpl extends Descriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
    	public static String PARAMETERNAME_PATH_TO_NANT = "pathToNant";
    	
    	@CopyOnWrite
        private volatile NantInstallation[] installations = new NantInstallation[0];
        
        

    	private DescriptorImpl() {
            super(NantBuilder.class);
            load();
        }
    	
    	protected void convert(Map<String,Object> oldPropertyBag) {
            if(oldPropertyBag.containsKey("installations"))
                installations = (NantInstallation[]) oldPropertyBag.get("installations");
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.NantBuilder_DisplayName();
        }
        
        public NantInstallation[] getInstallations() {
            return installations;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException{
        	// to persist global configuration information,
            // set that to properties and call save().
            
            int i;
            String[] names = req.getParameterValues("nant_name");
            String[] homes = req.getParameterValues("nant_home");
            int len;
            if(names!=null && homes!=null)
                len = Math.min(names.length,homes.length);
            else
                len = 0;
            NantInstallation[] insts = new NantInstallation[len];

            for( i=0; i<len; i++ ) {
                if(names[i].length()==0 || homes[i].length()==0)    continue;
                insts[i] = new NantInstallation(names[i],homes[i]);
            }

            this.installations = insts;
            
            save();
            return true;
        }

        //
        // web methods
        //
        
        /**
         * Checks if the NANT_HOME is valid.
         */
        public FormValidation doCheckNantHome(@QueryParameter final String value) {
            // this can be used to check the existence of a file on the server, so needs to be protected
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) return FormValidation.ok();
            File f = new File(Util.fixNull(value));
            if(!f.isDirectory()) {
                return FormValidation.error(f+" is not a directory");
            }

            File nantExe = new File(f,"bin/NAnt.exe");
            if(!nantExe.exists()) {
                return FormValidation.error(f+" is not a NAnt installation directory.");
            }

            return FormValidation.ok();
        }
    }
    
    public static final class NantInstallation implements Serializable {
        private final String name;
        private final String nantHome;

        public NantInstallation(String name, String nantHome) {
            this.name = name;
            this.nantHome = nantHome;
        }

        /**
         * install directory.
         */
        public String getNantHome() {
            return nantHome;
        }

        /**
         * Human readable display name.
         */
        public String getName() {
            return name;
        }

        public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
            return launcher.getChannel().call(new Callable<String,IOException>() {
                public String call() throws IOException {
                    File exe = getExeFile();
                    if(exe.exists())
                        return exe.getPath();
                    throw new IOException(exe.getPath()+" doesn't exist");
                }
            });
        }

        private File getExeFile() {
            String execName;
            if(File.separatorChar=='\\')
                execName = "NAnt.exe";
            else
                execName = "NAnt";

            return new File(getNantHome(),"bin/"+execName);
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() throws IOException, InterruptedException {
            LocalLauncher launcher = new LocalLauncher(TaskListener.NULL);
            return launcher.getChannel().call(new Callable<Boolean,IOException>() {
                public Boolean call() throws IOException {
                    return getExeFile().exists();
                }
            });
        }

        private static final long serialVersionUID = 1L;
    }
}
