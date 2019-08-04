/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2006 Jeff Ridder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ridderware.checkmate;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import org.apache.logging.log4j.*;

/**
 * ArgsHandler parses command line arguments and makes them available everywhere
 * via a singleton.
 *
 * @author Jeff Ridder
 */
public class ArgsHandler
{
    private static ArgsHandler one = new ArgsHandler();

    private ArrayList<String> scenario_urls = new ArrayList<String>();

    private File working_directory = null;

    private String asi_file_name = null;

    private int num_evaluations = 1;

    private Long runStartTime = null;

    private boolean outputXML = false;

    private boolean bhelp = false;

    private static final Logger logger = LogManager.getLogger(ArgsHandler.class);

    /** Creates a new instance of ArgsHandler */
    protected ArgsHandler()
    {
    }

    /**
     * Returns the singleton instance of the ArgsHandler.
     *
     * @return the ArgsHandler instance.
     */
    public static ArgsHandler getInstance()
    {
        return one;
    }

    /**
     * Returns the start time of the run.
     * @return start time.
     */
    protected Long getRunStartTime()
    {
        return this.runStartTime;
    }

    /**
     * Is this just a help request?  If so, then allow other code to find this
     * out.
     * @return true or false.
     */
    public boolean helpCheck()
    {
        return bhelp;
    }

    /**
     * Sets the help check condition, flagging this run as a simple help request.
     * @param bhelp true if this is a help check.
     */
    protected void setHelpCheck(boolean bhelp)
    {
        this.bhelp = bhelp;
    }

    /**
     * Returns the scenario file URL specified on the command line using
     * "file:".
     *
     * @return scenario URL.
     */
    public String getScenarioURL()
    {
        return scenario_urls.get(0);
    }

    /**
     * Returns the scenario file URL specified on the command line using
     * "file:" at the specified index
     * @param i index of scenario file URL to return.
     * @return scenario URL.
     */
    public String getScenarioURL(int i)
    {
        if (i < scenario_urls.size())
        {
            return scenario_urls.get(i);
        }
        else
        {
            return getScenarioURL();
        }
    }

    /**
     * Returns the working directory specified on the command line using "-d".
     *
     * @return working directory as a File object.
     */
    public File getWorkingDirectory()
    {
        return working_directory;
    }

    /**
     * Returns the .asi file name for writing SIMDIS data.
     * @return name of SIMDIS .asi file.
     */
    public String getASIFileName()
    {
        return this.asi_file_name;
    }

    /**
     * Returns the number of simulation evaluations to perform.
     * This is the total number of evaluations for generating statistics.
     * This is specified on the command line using "-it".
     * @return number of simulation evaluations.
     */
    public int getNumEvaluations()
    {
        return this.num_evaluations;
    }

    /**
     * Returns whether to write out the scenario in XML.  This is specified on
     * the command line using "-xml".
     *
     * @return true if XML scenarios are to be written.
     */
    public boolean getOutputXML()
    {
        return this.outputXML;
    }

    /**
     * Creates and returns a Java File of the specified name in the working
     * directory.
     * @param filename name of file to create.
     * @return Java File.
     */
    public File getFile(String filename)
    {
        return (new File(working_directory.getAbsolutePath() + File.separator +
            filename));
    }

    /**
     * Parses and processes the command line arguments, storing them for later
     * access and use.
     * @param args command line arguments.
     */
    public void processArgs(String[] args)
    {
        String workingDir = null;

        for (int i = 0; i < args.length; i++)
        {
            if (args[i].length() > 5 && args[i].substring(0, 5).equals("file:"))
            {
                //  This is the scenario file
                scenario_urls.add(args[i]);
            }
            else if (args[i].equals("-d"))
            {
                workingDir = args[i + 1].trim();

                if (!workingDir.endsWith(java.io.File.separator))
                {
                    workingDir += java.io.File.separator;
                }

                //if we weren't given an absolute path
                if (!new File(workingDir).isAbsolute())
                {
                    //record our current location
                    working_directory =
                        new java.io.File(System.getProperty("user.dir") +
                        java.io.File.separator);

                    if (!workingDir.startsWith(".."))
                    {
                        workingDir = java.io.File.separator + workingDir;
                    }

                    //go up in the directory structure as much as requested
                    while (workingDir.startsWith(".."))
                    {
                        working_directory = working_directory.getParentFile();
                        workingDir = workingDir.substring(2);
                    }
                    new File(working_directory.getAbsolutePath()).mkdirs();
                    working_directory =
                        new java.io.File(working_directory.getAbsolutePath() +
                        workingDir);
                }
                else
                {
                    //an absolute path was specified.
                    working_directory = new java.io.File(workingDir);
                }
            }
            else if (args[i].equals("-xml"))
            {
                this.outputXML = true;
            }
            else if (args[i].equals("-it"))
            {
                try
                {
                    num_evaluations = Integer.parseInt(args[i + 1]);
                }
                catch (NumberFormatException e)
                {
                    logger.warn("Number of iterations not of integer type. Found: " +
                        args[i + 1]);
                    logger.warn("Using default number of iterations: " +
                        num_evaluations);
                }
            }
            else if (args[i].equalsIgnoreCase("-s"))
            {
                this.asi_file_name = args[i + 1];
            }
            else if (args[i].equals("help") || args[i].equals("?"))
            {
                logger.info("Options are as follows:");
                logger.info(" -d\tDIR\t\tloads the working directory from which all files will be read/written");
                logger.info(" -it\tINT\t\tspecifies the number of times to evaluate the scenario");
                logger.info(" -s\tSTR\t\tspecifies that SIMDIS output should be written to the specified filename");
                logger.info(" file:[path-to-scenario]\tspecifies the URL of the XML scenario file");
                logger.info(" -xml\t\t\tspecifies that the individual[s] should be output as XML");

                bhelp = true;
            }
        }

        if (!bhelp)
        {
            //in case we weren't given a working directory, the default will be wherever the JVM was launched.
            if (this.working_directory == null)
            {
                this.working_directory =
                    new File(System.getProperty("user.dir") + File.separator);
            }

            outputPreRunSummary();
            runStartTime = System.nanoTime();
        }
    }

    /**
     * Outputs a summary of the run conditions prior to executing the run.
     */
    public void outputPreRunSummary()
    {
        String runTempDesc = null;
        runTempDesc = ("\nPre-Runtime Summary Data Follows: \n");
        runTempDesc += ("Start Time: " + new Date() + "\n");
        runTempDesc += ("Num Evaluations: " + this.num_evaluations + "\n");
        runTempDesc += ("Working directory set to: " +
            this.working_directory.getAbsolutePath() + "\n");
        for (String url : scenario_urls)
        {
            runTempDesc += ("Scenario file URL: " + url + "\n");
        }

        logger.info(runTempDesc);
    }

    /**
     * Outputs a summary of the run performance following a run.
     */
    public void outputPostRunSummary()
    {
        Long runEndTime = System.nanoTime();
        String runTempDesc = ("\nPost-Runtime Summary Data Follows: \n");
        runTempDesc += ("End Time: " + new Date() + "\n");
        runTempDesc += ("Num Evaluations: " + num_evaluations + "\n");
        double seconds = (runEndTime - runStartTime) / Math.pow(10, 9);
        runTempDesc += ("Estimated runtime per each evaluation: " + (seconds /
            num_evaluations) + " seconds \n");
        runTempDesc += ("Total RunTime = " + seconds + " seconds");

        logger.info(runTempDesc);
    }
}
