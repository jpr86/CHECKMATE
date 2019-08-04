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
 * A missile is a weapon that flies from a launch point out to a target or its
 * maximum range (if target is out of range) and then attempts to destroy it.
 *
 * @author Jeff Ridder
 */
public abstract class Missile extends Weapon
{
    /**
     *  Lethal range.
     */
    private double lethal_range;

    /**
     *  Probability of kill.
     */
    private double pk;

    /**
     * Maximum Gs.
     */
    private double max_gs;

    /**
     * Creates a new instance of Missile.
     * @param name name of the missile.
     * @param world CMWorld in which the missile plays..
     */
    public Missile(String name, CMWorld world)
    {
        super(name, world);
        this.setPlatformType(PlatformType.AIR);
    }

    /**
     * Creates a new instance of Missile with attributes for graphical display.
     * @param name name of the missile.
     * @param world CMWorld in which the missile plays.
     * @param ttf font containing the symbol to display.
     * @param fontSymbol the font symbol to display for this object.
     * @param color color to use for this symbol.
     */
    public Missile(String name, CMWorld world, Font ttf, String fontSymbol,
        Color color)
    {
        super(name, world, ttf, fontSymbol, color);
        this.setPlatformType(PlatformType.AIR);

    }

    /**
     * Called by the simulation framework to reset the object prior to each
     * simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
    }

    /**
     * Override of simdisInitialize.  In addition to base class initialization of
     * SIMDIS, this override marks the platform as a missile.
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

                pw.println("PlatformType\t" + String.valueOf(this.getId()) +
                    "\t\"missile\"");
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Sets the lethal range of the missile in nautical miles.
     * @param lethal_range lethal range.
     */
    public void setLethalRange(double lethal_range)
    {
        this.lethal_range = lethal_range;
    }

    /**
     * Returns the lethal range of the missile.
     * @return lethal range in nautical miles.
     */
    public double getLethalRange()
    {
        return this.lethal_range;
    }

    /**
     * Sets the probability-of-kill of the missile.
     * @param pk probability-of-kill.
     */
    public void setPk(double pk)
    {
        this.pk = pk;
    }

    /**
     * Returns the probability-of-kill of the missile.
     * @return probability-of-kill.
     */
    public double getPk()
    {
        return this.pk;
    }

    /**
     * Sets the max G load the missile can sustain in turns.
     * @param max_gs max G's.
     */
    public void setMaxGs(double max_gs)
    {
        this.max_gs = max_gs;
    }

    /**
     * Returns the max G load sustainable by the missile.
     * @return max G's.
     */
    public double getMaxGs()
    {
        return this.max_gs;
    }

    /**
     * Parses the XML node, setting the attributes of the missile.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("lethal-range"))
            {
                this.setLethalRange(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("pk"))
            {
                this.setPk(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("max-gs"))
            {
                this.setMaxGs(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Writes the missile's attributes as subordinate elements of the XML node.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("lethal-range");
        e.setTextContent(String.valueOf(this.getLethalRange()));
        node.appendChild(e);

        e = document.createElement("pk");
        e.setTextContent(String.valueOf(this.getPk()));
        node.appendChild(e);

        e = document.createElement("max-gs");
        e.setTextContent(String.valueOf(this.getMaxGs()));
        node.appendChild(e);
    }
}
