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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A surface-to-air missile class.  SAMs are in one of four guidance modes at
 * any time that they are active (i.e., flying) -- active, semi-active, ballistic,
 * home-on-jam.  The SAM object determines which mode it is in, and transitions between modes
 * at the beginning of each update.
 *
 * @author Jeff Ridder
 */
public class SAM extends Missile
{
    /**
     * Enumeration of SAM guidance modes.
     */
    public enum GuidanceMode
    {
        /** Active -- self-homing to target */
        ACTIVE,
        /** Semi-Active -- homes to target, dependent on illumination from SAM radar */
        SEMIACTIVE,
        /** Ballistic -- No homing of any kind */
        BALLISTIC,
        /** Home-on-jam -- self-guided to jammers */
        HOJ
    }

    private GuidanceMode guidance_mode;

    private double active_guidance_range;

    /**
     * Creates a new instance of SAM.
     * @param name name of the SAM.
     * @param world CMWorld in which it plays.
     */
    public SAM(String name, CMWorld world)
    {
        super(name, world);
        this.guidance_mode = GuidanceMode.SEMIACTIVE;
        this.active_guidance_range = 0.;
        this.setPlatformType(PlatformType.AIR);
    }

    /**
     * Creates a new instance of SAM with attributes for GUI display.
     * @param name name of the SAM.
     * @param world CMWorld in which it plays.
     * @param ttf font containing the symbol to display.
     * @param fontSymbol the font symbol to display for this object.
     * @param color color to use for this symbol.
     */
    public SAM(String name, CMWorld world, Font ttf, String fontSymbol,
        Color color)
    {
        super(name, world, ttf, fontSymbol, color);
        this.guidance_mode = GuidanceMode.SEMIACTIVE;
        this.active_guidance_range = 0.;
        this.setPlatformType(PlatformType.AIR);
    }

    /**
     * Override of reset.  This is called by the FUSE framework prior to each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
    }

    /**
     * Sets the guidance mode for the SAM.
     * @param guidance_mode the guidance mode for the SAM.
     */
    public void setGuidanceMode(GuidanceMode guidance_mode)
    {
        this.guidance_mode = guidance_mode;
    }

    /**
     * Returns the guidance mode.
     * @return the guidance mode.
     */
    public GuidanceMode getGuidanceMode()
    {
        return this.guidance_mode;
    }

    /**
     * Sets the distance from target at which the missile uses active homing.  Once
     * under active guidance, the missile is self-guiding to the target and no
     * longer reliant on external tracking.
     * @param active_guidance_range active guidance range in nautical miles.
     */
    public void setActiveGuidanceRange(double active_guidance_range)
    {
        this.active_guidance_range = active_guidance_range;
    }

    /**
     * Returns the active guidance range for the missile.
     * @return active guidance range.
     */
    public double getActiveGuidanceRange()
    {
        return this.active_guidance_range;
    }

    /**
     * Parses the XML node to set the attributes of the object.
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

            if (child.getNodeName().equalsIgnoreCase("active-guidance-range"))
            {
                this.setActiveGuidanceRange(Double.parseDouble(child.
                    getTextContent()));
            }
        }
    }

    /**
     * Creates sub-elements of the XML node containing the attributes of this object.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("active-guidance-range");
        e.setTextContent(String.valueOf(this.getActiveGuidanceRange()));
        node.appendChild(e);
    }
}
