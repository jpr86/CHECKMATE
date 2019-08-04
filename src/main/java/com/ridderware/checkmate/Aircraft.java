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

import com.ridderware.checkmate.MobilePlatform.ActivateBehavior;
import com.ridderware.checkmate.Platform.Status;
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
 * Abstract aircraft class.  An aircraft is a mobile platform that has
 * additional attributes including radar cross-section, maximum range, and
 * the ability to enter the simulation at a time later than the beginning of the
 * simulation.
 *
 * @author Jeff Ridder
 */
public class Aircraft extends MobilePlatform
{
    // Radar cross section in square meters
    private double rcs;

    private boolean activated;

    private ActivateBehavior activate_behavior;

    /**
     * Creates a new instance of Aircraft.
     * @param name the name of the aircraft.
     * @param world the CMWorld that the aircraft plays in.
     * @param points the point value of the aircraft.
     */
    public Aircraft(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createAircraft();
    }

    /**
     * Creates a new instance of Aircraft.
     * @param name the name of the aircraft.
     * @param world the CMWorld that the aircraft plays in.
     * @param points the point value of the aircraft.
     * @param ttf the font used to display a symbol for the aircraft.
     * @param fontSymbol the font symbol for the aircraft.
     * @param color the color of the font symbol.
     */
    public Aircraft(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createAircraft();
    }

    /**
     * Sets the initial aircraft attributes.
     */
    protected final void createAircraft()
    {
        this.getWorld().addAircraft(this);

        this.setMaxRange(0.);
        this.rcs = 1.;
        this.activated = false;
        this.setPlatformType(PlatformType.AIR);

        this.activate_behavior = new ActivateBehavior();
        this.addBehavior(this.activate_behavior);
    }

    /**
     * Sets the radar cross-section of the aircraft in square meters.
     * @param rcs radar cross-section.
     */
    public void setRCS(double rcs)
    {
        this.rcs = rcs;
    }

    /**
     * Returns the radar cross-section of the aircraft.
     * @return the radar cross-section in square meters.
     */
    public double getRCS()
    {
        return this.rcs;
    }

    /**
     * Returns whether the aircraft was ever activated during the course of the simulation.
     * @return true if the aircraft was activated, false otherwise.
     */
    public boolean getActivated()
    {
        return this.activated;
    }

    /**
     * Sets the status of the aircraft.  If status is <CODE>ACTIVE</CODE>, then
     * the aircraft is flagged as activated and <CODE>getActivated()</CODE> will return true.
     * @param status Status enum indicating the activation state of the aircraft.
     */
    @Override
    public void setStatus(Status status)
    {
        super.setStatus(status);

        if (this.getStatus() == Status.ACTIVE)
        {
            this.activated = true;
        }
    }

    /**
     * Resets the aircraft to its initial state before each simulation run.  This is
     * called automatically by the Swarm simulation framework.
     */
    @Override
    public void reset()
    {
        this.activated = false;
        super.reset();
    }

    /**
     * Override of simdisInitialize.  In addition to base class initialization of
     * SIMDIS, this override marks the platform as an aircraft.
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
                    "\t\"aircraft\"");
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Sets aircraft parameters by parsing XML contained within the node.
     * @param node XML containing parameters relating to this aircraft.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("rcs"))
            {
                this.rcs = Double.parseDouble(child.getTextContent());
            }
        }
    }

    /**
     * Creates DOM XML nodes containing the parameters describing this aircraft.
     * @param node root node of this aircraft.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("rcs");
        e.setTextContent(String.valueOf(this.rcs));
        node.appendChild(e);
    }
}
