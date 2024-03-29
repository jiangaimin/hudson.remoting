/*******************************************************************************
 *
 * Copyright (c) 2004-2009 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
*
*    Kohsuke Kawaguchi
 *     
 *
 *******************************************************************************/ 

package hudson.remoting.jnlp;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;

import java.util.logging.Logger;
import java.util.logging.Level;
import static java.util.logging.Level.INFO;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.io.IOException;

import hudson.remoting.Engine;
import hudson.remoting.EngineListener;

/**
 * Entry point to JNLP slave agent.
 *
 * <p>
 * See also <tt>slave-agent.jnlp.jelly</tt> in the core.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {

    @Option(name="-tunnel",metaVar="HOST:PORT",
            usage="Connect to the specified host and port, instead of connecting directly to Hudson. " +
                  "Useful when connection to Hudson needs to be tunneled. Can be also HOST: or :PORT, " +
                  "in which case the missing portion will be auto-configured like the default behavior")
    public String tunnel;

    @Option(name="-headless",
            usage="Run in headless mode, without GUI")
    public boolean headlessMode = Boolean.getBoolean("hudson.agent.headless")
                    || Boolean.getBoolean("hudson.webstart.headless");

    @Option(name="-url",
            usage="Specify the Hudson root URLs to connect to.")
    public final List<URL> urls = new ArrayList<URL>();

    @Option(name="-credentials",metaVar="USER:PASSWORD",
            usage="HTTP BASIC AUTH header to pass in for making HTTP requests.")
    public String credentials;

    @Option(name="-noreconnect",
            usage="If the connection ends, don't retry and just exit.")
    public boolean noReconnect = false;

    /**
     * 4 mandatory parameters.
     * Host name (deprecated), Hudson URL, secret key, and slave name.
     */
    @Argument
    public final List<String> args = new ArrayList<String>();

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            _main(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("java -jar slave.jar [options...] <secret key> <slave name>");
            new CmdLineParser(new Main()).printUsage(System.err);
        }
    }

    /**
     * Main without the argument handling.
     */
    public static void _main(String[] args) throws IOException, InterruptedException, CmdLineException {
        // see http://forum.java.sun.com/thread.jspa?threadID=706976&tstart=0
        // not sure if this is the cause, but attempting to fix
        // http://issues.hudson-ci.org/browse/HUDSON-310
        // by overwriting the security manager.
        try {
            System.setSecurityManager(null);
        } catch (SecurityException e) {
            // ignore and move on.
            // some user reported that this happens on their JVM: http://d.hatena.ne.jp/tueda_wolf/20080723
        }

        Main m = new Main();
        CmdLineParser p = new CmdLineParser(m);
        p.parseArgument(args);
        if(m.args.size()!=2)
            throw new CmdLineException("two arguments required, but got "+m.args);
        if(m.urls.isEmpty())
            throw new CmdLineException("At least one -url option is required.");

        m.main();
    }

    public void main() throws IOException, InterruptedException {
        Engine engine = new Engine(
                headlessMode ? new CuiListener() : new GuiListener(),
                urls, args.get(0), args.get(1));
        if(tunnel!=null)
            engine.setTunnel(tunnel);
        if(credentials!=null)
            engine.setCredentials(credentials);
        engine.setNoReconnect(noReconnect);
        engine.start();
        engine.join();
    }

    /**
     * {@link EngineListener} implementation that sends output to {@link Logger}.
     */
    private static final class CuiListener implements EngineListener {
        private CuiListener() {
            LOGGER.info("Hudson agent is running in headless mode.");
        }

        public void status(String msg, Throwable t) {
            LOGGER.log(INFO,msg,t);
        }

        public void status(String msg) {
            status(msg,null);
        }

        public void error(Throwable t) {
            LOGGER.log(Level.SEVERE, t.getMessage(), t);
            System.exit(-1);
        }

        public void onDisconnect() {
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
}
