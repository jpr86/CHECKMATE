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

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A platform with attributes and methods to support pseudo-random
 * location on each iteration.
 * @author Jeff Ridder
 */
public class UncertainLocationPlatform extends Platform
{
    private RandomPointGenerator rpg;

    /**
     * Creates a new instance of UncertainLocationPlatform
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     */
    public UncertainLocationPlatform(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createUncertainLocationPlatform();
    }

    /**
     * Creates a new instance of UncertainLocationPlatform with attributes for graphical display.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     * @param ttf font containing the symbol to be displayed.
     * @param fontSymbol font symbol to be displayed.
     * @param color color used to draw the symbol.
     */
    public UncertainLocationPlatform(String name, CMWorld world, int points,
        Font ttf, String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createUncertainLocationPlatform();
    }

    /**
     * Sets the initial attributes of the platform upon creation.
     */
    protected final void createUncertainLocationPlatform()
    {
        this.getWorld().addTarget(this);

        this.rpg = null;
    }

    /**
     * Creates a random point generator for the object.
     */
    protected final void createRPG()
    {
        if (this.rpg == null)
        {
            this.rpg = new RandomPointGenerator(getWorld());
        }
    }

    /**
     * Sets the initial attributes of the platform before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();

        //  I commented this out so that it doesn't automatically randomize the location
        //  before each run -- this is to support the GA -- location will be set prior
        //  to each run via a setLocation call.  JPR 08/11/2006.
//        this.setLocation(this.randomPoint());
        this.setStatus(UncertainLocationPlatform.Status.ACTIVE);
    }

    /**
     * Override of simdisInitialize.  In addition to base class initialization of
     * SIMDIS, this override marks the platform as a landsite.
     * @param asi_file the SIMDIS .asi file.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
        super.simdisInitialize(asi_file);

        if (this.getSIMDISIcon() != null && asi_file != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("PlatformType\t" + this.getId() + "\t\"landsite\"");
                pw.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Returns the random point generator for this object.
     * @return RandomPointGenerator.
     */
    public RandomPointGenerator getRandomPointGenerator()
    {
        if (this.rpg == null)
        {
            this.createRPG();
        }

        return this.rpg;
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node for the platform.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("Location"))
            {
                if (this.rpg == null)
                {
                    this.createRPG();
                }

                this.rpg.fromXML(child);
            }
        }
    }

    /**
     * Creates XML nodes containing the platform's attributes.
     *
     * @param node root node for the platform.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        e = document.createElement("Location");
        node.appendChild(e);
        this.rpg.toXML(e);
    }
}
