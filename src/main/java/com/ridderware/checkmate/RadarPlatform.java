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

import com.ridderware.checkmate.Platform.Status;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.gui.Painter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The representation of a radar platform that is a component of an
 * air defense system.  The platform can contain multiple radars, has uncertain
 * location relative to a centroid (or the centroid of the superior if there is
 * one), and reports tracks to the superior.
 *
 * @author Jeff Ridder
 */
public class RadarPlatform extends UncertainLocationPlatform implements IRadarTrackReceiver
{
    private HashSet<Radar> radars = new HashSet<>();

    /**
     * Creates a new instance of RadarPlatform
     * @param name name of the platform.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     */
    public RadarPlatform(String name, CMWorld world, int points)
    {
        super(name, world, points);

        this.createRadarPlatform();
    }

    /**
     * Creates a new instance of RadarPlatform with attributes for GUI display.
     * @param name name of the platform.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     * @param ttf font used for drawing the object.
     * @param fontSymbol symbol to display.
     * @param color color to paint it.
     */
    public RadarPlatform(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);

        this.createRadarPlatform();
    }

    /**
     * Sets the initial attributes of a newly minted RadarPlatform.
     */
    protected final void createRadarPlatform()
    {

    }

    /**
     * Adds a radar to the platform.
     * @param radar Radar object.
     */
    public void addRadar(Radar radar)
    {
        this.radars.add(radar);
        radar.setParent(this);
        this.getWorld().addRadar(radar);
    }

    /**
     * Returns the set of radars contained by this platform.
     * @return Set of radars.
     */
    public HashSet<Radar> getRadars()
    {
        return radars;
    }

    /**
     * Removes a radar from the platform.
     * @param radar radar to be removed.
     */
    public void removeRadar(Radar radar)
    {
        this.radars.remove(radar);
        this.getWorld().getRadars().remove(radar);
    }

    /**
     * Sets the activation state for all radars on the platform.
     * @param active true if radars are to be activated, false otherwise.
     */
    public void setRadarsActive(boolean active)
    {
        for (Radar r : radars)
        {
            r.setActive(active);
        }
    }

    /**
     * Sets the initial attributes of the platform before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();

        //  If I have no superior, then I am operating autonomously and
        //  will initialize independently.  Otherwise, I will allow my
        //  superior (SAMSite or EarlyWarningSite) to initialize and set the
        //  initial activation state.
        if (this.getSuperior() == null)
        {
            for (Radar r : radars)
            {
                r.initialize();

                r.setActive(true);
            }
        }
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node for the platform.
     *
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
                Radar r = (Radar) getWorld().createSystem(child);
                this.addRadar(r);
            }
        }

    }

    /**
     * Creates XML nodes containing the platform's attributes.
     *    @param node root node for the platform.
     *
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        if (!this.radars.isEmpty())
        {
            for (Radar r : radars)
            {
                this.getWorld().writeSystemToXML(r, node);
            }
        }
    }

    /**
     * Called by the Swarm framework to paint the agent.
     * @param args see base class documentation.
     * @return a collection of shapes.
     *
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        HashSet<Shape> boundingShapes = new HashSet<>(1);
        if (this.getStatus() == Status.ACTIVE)
        {
            if ((getSuperior() instanceof EarlyWarningSite && getSuperior().
                getState().getName().equals("Stationary")) ||
                getSuperior() == null ||
                (getSuperior() instanceof SAMSite && (getSuperior().getState().
                getName().equals("Deployed") ||
                getSuperior().getState().getName().equals("Firing"))))
            {
                Color gray = Color.gray;
                Color color = new Color(gray.getRed(), gray.getGreen(),
                    gray.getBlue(), 128);

                for (Radar r : radars)
                {
                    if (r.isEmitting())
                    {
                        color = this.getColor();
                        break;
                    }
                }

                Double3D loc = null;
                if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("ENU"))
                {
                    loc = this.getLocation();
                }
                else if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("LLA"))
                {
                    loc = new Double3D(getLocation().getY(),
                        getLocation().getX(), getLocation().getZ());
                }
                if (loc != null)
                {
                    boundingShapes.add(Painter.getPainter().paintText(this.getFontSymbol(),
                        loc, color, this.getFont(), 0.));
                }
            }
        }

        return boundingShapes;
    }

    /**
     * Called by the radars to report active tracks to the platform.
     * @param radar radar reporting its tracks.
     * @param tracks Set of platforms being tracked.
     */
    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
        // Forward tracks to superior if there is one.
        //  Might want to put a time delay on this.
        if (this.getSuperior() instanceof IRadarTrackReceiver)
        {
            //  Check for type of superior and forward tracks.
            ((IRadarTrackReceiver) this.getSuperior()).reportActiveTracks(radar,
                tracks);
        }
    }

    /**
     * Called by the radars to report tracks dropped to the platform.
     * @param radar radar reporting the dropped tracks.
     * @param dropped_tracks Set of dropped tracks.
     */
    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
        //  Forward dropped tracks to superior if there is one.
        if (this.getSuperior() instanceof IRadarTrackReceiver)
        {
            ((IRadarTrackReceiver) this.getSuperior()).reportDroppedTracks(radar,
                dropped_tracks);
        }
    }
}
