/**
 * Copyright (C) 2014 Ortus Solutions, Corp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package cliloader;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Wraps and loads the CFML engine to bootstrap CommandBox from the command line
 * It is executed via the main method
 * 
 * @author Ortus Solutions <info@ortussolutions.com>
 */
public class LoaderCLIMain {
	
	/**
	 * The version of this loader
	 */
	private static final String VERSION = "1.0.0+@build.number@";
	
	/**
	 * The location of the CFML engine java library
	 */
	private static final String LIB_ZIP_PATH = "libs.zip";
	
	/**
	 * The location of the CommandBox CFML scripts
	 */
	private static final String CFML_ZIP_PATH = "cfml.zip";
	
	/**
	 * The location of the CommandBox CLI properties
	 */
	private static final String CLI_PROPERTIES = "cliloader/cli.properties";
	
	/**
	 * The default shell script to load CommandBox with
	 */
	private static final String CLI_SHELL = "/cfml/system/Bootstrap.cfm";
	
	/**
	 * The current thread class loader
	 */
	private static final ClassLoader CLASSLOADER = Thread.currentThread().getContextClassLoader();
	
	/**
	 * The flag that denotes debug operation or not, we default to false
	 */
	private static Boolean debugMode = false;
	
	/**
	 * The exit code used to exist the system, 0 means success
	 */
	private static int exitCode = 0;
	
	/**
	 * Location of thisJar on disk
	 */
    private static final File thisJar = new File( LoaderCLIMain.class.getProtectionDomain().getCodeSource().getLocation().getPath() );
	
	/**
	 * Set of loggers
	 */
	public static final Set<String> LOGGERS = new HashSet<>( Arrays.asList( new String[] {
			"RunwarLogger",
			"org.jboss.logging",
			"org.xnio.Xnio",
			"org.xnio.nio.NioXnio",
			"io.undertow.UndertowLogger"
	}));

	/**
	 * The main CommandBox loader class
	 * @param args
	 * @throws Throwable
	 */
	public static void main( String[] args ) throws Throwable {
		
		// get the incoming arguments into an ArrayList
    	ArrayList<String> argList = new ArrayList( Arrays.asList( args ) );
		// Convert arguments to a map, much easier to deal with
		Map<String,String> config = toMap( args );
		
		// Load CLI properties
		Properties props = new Properties();
		try {
	        props.load( ClassLoader.getSystemResourceAsStream( CLI_PROPERTIES ) );
	    } catch (IOException e) {
	    	System.out.println( "Error Loading " + CLI_PROPERTIES );
	    	e.printStackTrace(); 
	    }
		// prepare name
		String name = props.getProperty( "name" );
		String NAME = name.toUpperCase();
		
		// Variable setups
		Boolean updateLibs	= false;
		Boolean startServer = false;
		Boolean stopServer	= false;
		Boolean background	= false;
		
		// debug mode?
		if( config.containsKey( "debug" ) ){
			debugMode = true;
			listRemoveContaining( argList, "-debug" );
		}
		// Modify loggers according to debug mode
		subvertLoggers( debugMode ? "DEBUG" : "WARN", LOGGERS );
		
		// Debug Args
		if( debugMode ){ 
			System.out.println( "Sent raw args: " + Arrays.toString( args ) ); 
			System.out.println( "Sent argList: " + argList ); 
			System.out.println( "Sent config map: " + config.toString() ); 
		}
		
		// Get the user working directory
		String currentDir = System.getProperty( "user.dir" );
		// Get system environment
		Map<String, String> env = System.getenv();
		
		/******** DISCOVER CLI HOME ************/
		File cli_home;
		// was a commandbox_home argument sent?
		if( config.get( "commandbox_home" ) != null ){
			cli_home = new File( config.get( "commandbox_home" ) );
			listRemoveContaining( argList, "-CommandBox_home" );
			if( debugMode ){
				System.out.println( "CLI Home passed as argument: " + cli_home );
			}
		} 
		// Do we have a system property?
		else if( System.getProperty( "COMMANDBOX_HOME") != null ){
			cli_home = new File( System.getProperty( "COMMANDBOX_HOME" ) );
			if( debugMode ){
				System.out.println( "CLI Home discovered from property: " + cli_home );
			}
		} 
		// Do we have it as an environment property
		else if( env.get( "COMMANDBOX_HOME" ) != null ){
			cli_home = new File( env.get( "COMMANDBOX_HOME" ) );
			if( debugMode ){
				System.out.println( "CLI Home detected from Java environment: " + cli_home );
			}
		} 
		// Else we default to the user's home directory
		else {
			String userHome = System.getProperty( "user.home" );
			if( userHome != null ){
				cli_home = new File( userHome + "/.CommandBox/" );
			} else {
				cli_home = thisJar.getParentFile();
			}
		}
		// If CommandBox home does not exist, create it.
		if( !cli_home.exists() ){
			System.out.println( "Creating CommandBox Home: " + cli_home + " (change with -CommandBox_home=/path/to/dir)" );
			cli_home.mkdir();
		} else if( debugMode ){
			System.out.println( "CLI Home: " + cli_home );
		}
		
		// Get CommandBox LibDir
		File libDir = new File( cli_home, "lib" ).getCanonicalFile();
		// railo libs dir override
		if( config.containsKey( "lib" ) ){
			libDir = new File( config.get( "lib" ) );
			listRemoveContaining( argList, "-lib" );
		}
		if( debugMode ){ System.out.println( "CommandBox Lib dir: " + libDir); }
		
		// Update libs command check
		if( config.containsKey( "update" ) ){
			System.out.println( "Force updating " + name + " home" );
			updateLibs = true;
		}
		
		// Stop Command Check
		if( config.containsKey( "stop" ) ){
			stopServer = true;
			background = false;
		}
		
		// Help Command Check
		if( config.containsKey( "?" ) || config.containsKey( "help" ) ){
			System.out.println( props.get( "usage" ).toString().replace( "/n", System.getProperty( "line.separator" ) ) );
			Thread.sleep( 1000 );
			System.exit( 0 );
		}
		
		// Start server check
		if( listContains( argList, "-server" ) ){
			startServer = true;
		}
		
		// The shell
		if( listContains( argList, "-shell" ) ){
			startServer = false;
			listRemoveContaining( argList, "-shell" );
		}
		
		// clean out any leftover pack files (an issue on windows)
		if( libDir.exists() && libDir.listFiles( new ExtFilter( ".gz" ) ).length > 0){
			for( File gz : libDir.listFiles( new ExtFilter( ".gz " ) ) ) {
				try { gz.delete(); } catch ( Exception e ) {}
			}
		}
		
		// Do we unpack the libs
		if( !libDir.exists() || libDir.listFiles( new ExtFilter( ".jar" ) ).length < 2 || updateLibs ){
			// Advice what we are doing.
			if( updateLibs ){
				System.out.println( "Library force update detected, starting to unpack!" );
			} else {
				System.out.println( "Lookey Here! First time running CommandBox, so I have to unpack some libraries for you -- this will only happen once, and takes a few seconds..." );
			}
			
			Util.unzipInteralZip( CLASSLOADER, LIB_ZIP_PATH, libDir, debugMode );
			Util.unzipInteralZip( CLASSLOADER, CFML_ZIP_PATH, new File( cli_home.getPath() + "/cfml" ), debugMode);
			Util.copyInternalFile( CLASSLOADER, "resource/trayicon.png", new File( libDir, "trayicon.png" ) );
			
			// Done
			System.out.println( "" );
			System.out.println( "Yeehaaw! We are ready to go, libraries initialized!" );
			
			// If force update, exit now.
			if( updateLibs ){
				System.exit( 0 );
			}
		}
		
		// Get listing of jars in lib dir
        File[] children = libDir.listFiles( new ExtFilter( ".jar" ) );
        if( children.length < 2 ){
        	System.out.println( "Less than 2 children" );
			libDir = new File( libDir, "lib" );
        	children = libDir.listFiles( new ExtFilter( ".jar" ) );
        }
        // Class Load them
        URL[] urls = new URL[ children.length ];
        if( debugMode ) System.out.println( "Loading Jars" );
        for( int i=0; i < children.length; i++ ){
        	urls[ i ] = children[ i ].toURI().toURL();
        	if( debugMode ) System.out.println( "- " + urls[ i ] );
        }
        //URLClassLoader cl = new URLClassLoader(urls,ClassLoader.getSystemClassLoader());
        //URLClassLoader cl = new URLClassLoader(urls,null);
        URLClassLoader cl = new URLClassLoader( urls, CLASSLOADER );
		//Thread.currentThread().setContextClassLoader(cl);
		//    	Thread shutdownHook = new Thread( "cli-shutdown-hook" ) { public void run() { cl.close(); } };
		//      Runtime.getRuntime().addShutdownHook( shutdownHook );	
		
		File configServerDir	= new File( libDir.getParentFile(), "server" );
		File configWebDir		= new File( libDir.getParentFile(), "server/railo-web" );
		
		// set some system properties
		System.setProperty( "railo.server.config.dir",	configServerDir.getAbsolutePath() );
		System.setProperty( "railo.web.config.dir",		configWebDir.getAbsolutePath() );
		System.setProperty( "cfml.cli.home",			cli_home.getAbsolutePath() );
		System.setProperty( "cfml.server.trayicon",		libDir.getAbsolutePath() + "/trayicon.png" );
		System.setProperty( "cfml.server.dockicon",		"" );
		
		// If not doing server mode
        if( !startServer && !stopServer ){
			// debug
			if( debugMode ) System.out.println( "Running in CLI mode" );
			
        	// Location of CommandBox BootStrap Shell
			String uri = cli_home.getCanonicalPath() + CLI_SHELL;
			
			// Execute Command Check
        	if( argList.size() > 1 && argList.contains("execute") ){
        		// bypass the shell for running pure CFML files
        		int executeIndex = argList.indexOf( "execute" );
        		File cfmlFile = new File( argList.get( executeIndex + 1 ) );
        		if( cfmlFile.exists() ){
        			uri = cfmlFile.getCanonicalPath();
        		}
        		argList.remove( executeIndex + 1 );
        		argList.remove( executeIndex );        		
        		if( debugMode ) System.out.println( "Executing: " + uri );
        	} 
			// If first argument is a file, try to execute it
			else if( argList.size() > 0 && new File( argList.get( 0 ) ).exists() ){
        		String filename = argList.get( 0 );
        		// Is this a CommandBox Recipe?
        		if( filename.endsWith( ".boxr" ) ){
        			argList.add( 0, "recipe" );
        		} 
				// Else, just execute the file
				else {
					uri = new File( filename ).getCanonicalPath();
        		}
        	}
			
			// Store arguments in system property so CFML can get to it
    		System.setProperty( "cfml.cli.arguments", arrayToList( argList.toArray( new String[ argList.size() ] ), " " ) );
            if( debugMode ){
				System.out.println( "Sent cfml.cli.arguments" + System.getProperty( "cfml.cli.arguments" ) );
			}
			
			// Load railo CLIMain
			Class<?> cli;
	        cli = cl.loadClass( "railocli.CLIMain" );
			// run via reflection
	        Method run = cli.getMethod( "run", new Class[]{ File.class, File.class, File.class, String.class, boolean.class } );
			try{
				File webroot = new File( getPathRoot( uri ) ).getCanonicalFile();
	        	run.invoke( null, webroot, configServerDir, configWebDir, uri, debugMode );
			} catch ( Exception e ){
				exitCode = 1;
				e.getCause().printStackTrace();
			} finally{
				cl.close();
			}
			
			System.out.flush();
			System.exit( exitCode );
        } 
		// SERVER MODE
		else {
        	if(debugMode) System.out.println("Running in server mode");
    		//only used for server mode, cli root is /
    		File webRoot;
    		if(listContains(argList,"-webroot") && config.get("webroot") != null) {
				args = removeElement(args,"-webroot");
    			webRoot = new File(config.get("webroot")).getCanonicalFile();
    		} else {
    			if(currentDir != null) {
    				webRoot = new File(currentDir).getCanonicalFile();
    			} else {
    				webRoot = new File("./").getCanonicalFile();
    			}
    		}
			// background
			if(listContains(argList,"-background")) {
				background = true;
				args = removeElement(args,"-background");
			}
	        Class<?> runwar;
	        runwar = cl.loadClass("runwar.Start");
			String path = LoaderCLIMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			//System.out.println("yum from:"+path);
			String decodedPath = java.net.URLDecoder.decode(path, "UTF-8");
			decodedPath = new File(decodedPath).getPath();

    		//args = removeElementThenAdd(args,"-server","-war "+webRoot.getPath()+" --background false --logdir " + libDir.getParent());
    		String[] argstr;
    		if(background) {
    			argstr= new String[] {
    					"-war",webRoot.getPath(),
    					"--background","true",
    					"--iconpath",libDir.getAbsolutePath() + "/trayicon.png",
    					"--libdir",libDir.getPath(),
    					"--processname",name
    					};
    		} else {
    			argstr= new String[] {
    					"-war",webRoot.getPath(),
    					"--iconpath",libDir.getAbsolutePath() + "/trayicon.png",
    					"--background","false",
    					"--processname",name
    					};
    		}
    		args = removeElementThenAdd(args,"-server",argstr);
        	if(debugMode) System.out.println("Args: " + arrayToList(args," "));
            Method main = runwar.getMethod("main",new Class[]{String[].class});
    		try{
            	main.invoke(null, new Object[]{args});
    		} catch (Exception e) {
    			exitCode = 1;
    			e.getCause().printStackTrace();
    		}
			cl.close();
        } 
	}

	public static String[] removeElement(String[] input, String deleteMe) {
		final List<String> list =  new ArrayList<String>();
		Collections.addAll(list, input);
		for(String item: input) {
	        if(item.startsWith(deleteMe)) {
	        	list.remove(item);
	        }
		}
		input = list.toArray(new String[list.size()]);
		return input;
	}
	
	public static String[] removeElementThenAdd(String[] input, String deleteMe, String[] addList) {
	    List<String> result = new LinkedList<String>();
	    for(String item : input)
	        if(!item.startsWith(deleteMe))
	            result.add(item);

	    if(addList != null)
		    for(String item : addList)
		    		result.add(item);
	    
	    return result.toArray(input);
	}


	public static class ExtFilter implements FilenameFilter {
		private String ext;
		public ExtFilter(String extension) {
			ext = extension;
		}
		
		public boolean accept(File dir, String name) {
			return name.toLowerCase().endsWith(ext);
		}

	}
	
	/**
	 * Convert an array to a list with a nice separator
	 * @param s
	 * @param separator
	 * @return 
	 */
	public static String arrayToList( String[] s, String separator ){  
		String result = "";  
	    if( s.length > 0 ){  
			result = s[ 0 ];     
			for( int i = 1; i < s.length; i++ ){  
				result = result + separator + s[ i ];  
			}  
		}  
	    return result;  
	}
	
	public static boolean listContains(ArrayList<String> argList, String text) {  
	    boolean found = false;
		for(String item : argList)
	        if(item.startsWith(text))
	            found = true;
		return found;
	}
	
	/**
	 * Remove an element from the array list
	 * @param argList
	 * @param text 
	 */
	public static void listRemoveContaining( ArrayList<String> argList, String text ){
		for ( Iterator<String> it = argList.iterator(); it.hasNext(); ){
			String str = it.next();
			if( str.startsWith( text ) ) {
				it.remove();
			}
		}
	}
	
	/**************************************** PRIVATE METHODS ********************************************************/
	
	/**
	 * Convert an array of arguments to a struct map
	 * @param args
	 * @return Map
	 */
	private static Map<String, String> toMap( String[] args ){
		Map<String, String> config = new HashMap<>();
		String key,value;
		
		if( args != null ){
			for(int i=0; i < args.length; i++ ){
				String raw = args[ i ].trim();
				// empty argument, just continue to next one
				if( raw.length() == 0 ) continue;
				// If it starts with -, its a param, remove it.
				if( raw.startsWith( "-" ) ) raw = raw.substring( 1 ).trim();
				// find = location to parse
				int index = raw.indexOf( '=' );
				// If not found, then set it to "" value, else set value
				if( index == -1 ) {
					key		= raw;
					value	= "";
				} else {
					key		= raw.substring( 0, index ).trim();
					value	= raw.substring( index+1 ).trim();
				}
				// store in map as lowercase key
				config.put( key.toLowerCase(), value );
			}
		}
		
		return config;
	}

	/**
	 * 
	 * @param string
	 * @return 
	 */
	private static String getPathRoot( String string ){
		return string.replaceAll( "^([^\\\\//]*?[\\\\//]).*?$", "$1" );
	}
	
	/**
	 * Need to analyse this method
	 * @param level
	 * @param loggers 
	 */
	private static void subvertLoggers( String level, Set<String> loggers ){
		System.setProperty("java.util.logging.ConsoleHandler.formatter", "java.util.logging.SimpleFormatter");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n");
        java.util.logging.ConsoleHandler chandler = new java.util.logging.ConsoleHandler();
        if(level.trim().toUpperCase().equals("TRACE"))
        	level = "FINER";
        if(level.trim().toUpperCase().equals("WARN"))
        	level = "WARNING";
        if(level.trim().toUpperCase().equals("DEBUG"))
        	level = "FINEST";
        java.util.logging.Level LEVEL = java.util.logging.Level.parse(level);
        chandler.setLevel(LEVEL);
		java.util.logging.LogManager logManager = java.util.logging.LogManager.getLogManager();
		for(Enumeration<String> loggerNames = logManager.getLoggerNames(); loggerNames.hasMoreElements();){
	        String name = loggerNames.nextElement();
	        java.util.logging.Logger nextLogger = logManager.getLogger(name);
	        if(loggers.contains(name) && nextLogger != null) {
	        	nextLogger.setUseParentHandlers(false);
	        	nextLogger.setLevel(LEVEL);
	        	if(nextLogger.getHandlers() != null) {
	        		for(java.util.logging.Handler handler : nextLogger.getHandlers()) {
	        			nextLogger.removeHandler(handler);
	        		}
	        		nextLogger.addHandler(chandler);	        		
	        	}
	        }
	    }		
	}

}
