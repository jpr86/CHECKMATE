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
import com.ridderware.fuse.Scenario;

/**
 * A main routine that runs a basic CHECKMATE simulation.
 *
 * @author Jeff Ridder
 */
public class CMMain
{
    /**
     * Creates a new instance of CMMain.
     */
    public CMMain()
    {
    }

    /**
     * The main method.
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        ArgsHandler args_handler = ArgsHandler.getInstance();

        args_handler.processArgs(args);

        if (args_handler.helpCheck())
        {
            System.exit(0);
        }

        Scenario scenario = new Scenario();

        CMWorld world = new CMWorld();

        scenario.setAgentFactory(world);

        world.readScenario(args_handler.getScenarioURL(), scenario);

        world.simdisInitialize(args_handler);

        for (long i = 0; i < args_handler.getNumEvaluations(); i++)
        {
            world.populateUniverse(scenario.getUniverse());
            //  Randomize target locations
            for (Platform t : world.getTargets())
            {
                if ( (t instanceof SAMSite && !(t.getSuperior() instanceof SAMBattalion )) || 
                    t instanceof SAMBattalion ||
                    t instanceof EarlyWarningSite ||
                    (t instanceof PassThroughC2 && !(t instanceof SAMSite || t instanceof SAMBattalion))  ||
                    (t instanceof RadarPlatform && t.getSuperior() == null) ||
                    (t instanceof SAMTEL && t.getSuperior() == null))
                {
                    t.setLocation(((UncertainLocationPlatform) t).getRandomPointGenerator().
                        randomGaussianPoint());
                }
            }

            //  Execute
            scenario.execute();
        }

        if (args_handler.getOutputXML())
        {
            String xmlfilename = args_handler.getWorkingDirectory().
                getAbsolutePath() + File.separator + "xml_scenario.xml";
            world.writeScenario(xmlfilename);
        }
    }
}
