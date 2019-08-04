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
import java.util.Set;
import org.apache.logging.log4j.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A subclass of MobilePlatform that carries a radar that emits while moving.
 * @author Jeff Ridder
 */
public class MovingEmitterPlatform extends MobilePlatform implements IRadarTrackReceiver
{
    private Radar radar;

    private static final Logger logger =
        LogManager.getLogger(MovingEmitterPlatform.class);

    /**
     * Creates a new instance of MovingEmitterPlatform.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     */
    public MovingEmitterPlatform(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createMovingEmitterPlatform();
    }

    /**
     * Creates a new instance of MovingEmitterPlatform with attributes for
     * graphical display.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     * @param ttf font containing the symbol to be displayed.
     * @param fontSymbol font symbol to be displayed for this object.
     * @param color color of the symbol.
     */
    public MovingEmitterPlatform(String name, CMWorld world, int points,
        Font ttf, String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createMovingEmitterPlatform();
    }

    /**
     * Sets the initial attributes of the platform upon creation.
     */
    protected final void createMovingEmitterPlatform()
    {
        this.getWorld().addTarget(this);
    }

    /**
     * Reports the tracks from the radar to the platform.
     * @param radar Radar object reporting the tracks.
     * @param tracks platform tracks being reported.
     */
    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
    }

    /**
     * Called by subordinate radar to report dropped tracks.
     * @param radar Radar object reporting dropped tracks.
     * @param dropped_tracks dropped tracks being reported.
     */
    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
    }

    /**
     * Sets the initial attributes of the platform prior to each simulation run.  This
     * may include random route generation.
     */
    @Override
    public void reset()
    {
        super.reset();

        //  Set my initial status to INACTIVE
        this.setStatus(Platform.Status.INACTIVE);

        radar.initialize();

        //  Activate from the get go
        this.activate(0.);
    }

    /**
     * Called by the base class ActivateBehavior, this activates the platform in the
     * simulation causing it to be ACTIVE.
     * @param time current time.
     */
    @Override
    public void activate(double time)
    {
        getMovePlatformBehavior().activate(time);
        logger.debug("Moving emitter " + this.getId() + " activated at time " +
            time);

        //  If currently inactive and the route is feasible, then activate.
        if (this.getStatus() == Platform.Status.ACTIVE)
        {
            radar.setActive(true);
        }
    }

    /**
     * Deactivates the platform in the simulation.
     * @param time current time.
     * @param status deactivation status.
     */
    @Override
    public void deactivate(double time, Platform.Status status)
    {
        if (this.getStatus() == Platform.Status.ACTIVE)
        {
            this.setStatus(status);
            this.getMovePlatformBehavior().setEnabled(false);
            radar.setActive(false);
        }
    }

    /**
     * Returns the onboard radar.
     * @return a Radar object.
     */
    public Radar getRadar()
    {
        return radar;
    }

    /**
     * Sets the onboard radar.
     * @param radar a Radar object.
     */
    public void setRadar(Radar radar)
    {
        if (radar == null)
        {
            throw new IllegalArgumentException();
        }
        this.radar = radar;
        radar.setParent(this);
        this.getWorld().addRadar(radar);
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

            if (child.getNodeName().equalsIgnoreCase("radar") ||
                child.getNodeName().equalsIgnoreCase("System"))
            {
                Radar rdr = (Radar) getWorld().createSystem(child);
                rdr.setParent(this);
                this.setRadar(rdr);
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

        if (this.radar != null)
        {
            this.getWorld().writeSystemToXML(radar, node);
        }
    }
}
