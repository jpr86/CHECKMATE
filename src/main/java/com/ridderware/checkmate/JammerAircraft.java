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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A jammer aircraft carries a receiver and one or more jammers.
 * @author Jeff Ridder
 */
public class JammerAircraft extends Aircraft implements IReceiverContainer
{
    private Set<Jammer> jammers = new HashSet<>();

    private Receiver receiver;

    /**
     * Creates a new instance of JammerAircraft.
     * @param name name of the aircraft.
     * @param world CMWorld in which the aircraft plays.
     * @param points point value of the aircraft.
     */
    public JammerAircraft(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createJammerAircraft();
    }

    /**
     * Creates a new instance of JammerAircraft with attributes for graphical display.
     * @param name name of the aircraft.
     * @param world CMWorld in which the aircraft plays.
     * @param points point value of the aircraft.
     * @param ttf font containing the symbol to be displayed.
     * @param fontSymbol font symbol to display for this object.
     * @param color color to draw the symbol.
     */
    public JammerAircraft(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createJammerAircraft();
    }

    /**
     * Sets the initial attributes of the aircraft upon creation.
     */
    protected final void createJammerAircraft()
    {

    }

    /**
     * Returns a collection of jammers contained by this aircraft.
     *
     * @return Java Collection of Jammer objects.
     */
    protected Collection<Jammer> getJammers()
    {
        return this.jammers;
    }

    /**
     * Resets the aircraft attributes prior to each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();

        for (Jammer j : jammers)
        {
            j.initialize();
        }

        if (receiver != null)
        {
            receiver.initialize();
        }
    }

    /**
     * Called by the base class ActivateBehavior, activates the aircraft in the simulation
     * by causing a transition from INACTIVE to ACTIVE.
     * @param time current time.
     */
    @Override
    public void activate(double time)
    {
        super.activate(time);

        if (this.getStatus() == Platform.Status.ACTIVE)
        {
            this.receiver.activate(time);
        }
    }

    /**
     * Adds a jammer to the aircraft.
     * @param j a Jammer object.
     */
    public void addJammer(Jammer j)
    {
        this.jammers.add(j);
        j.setParent(this);
        this.getWorld().addJammer(j);
    }

    /**
     * Returns the receiver on this aircraft.
     * @return a Receiver object.
     */
    public Receiver getReceiver()
    {
        return receiver;
    }

    /**
     * Sets the receiver on this aircraft.
     * @param receiver a Receiver object.
     */
    public void setReceiver(Receiver receiver)
    {
        this.receiver = receiver;
        receiver.setParent(this);
        this.getWorld().addReceiver(receiver);
    }

    /**
     * Called by the receiver to report tracks to the aircraft.
     * @param receiver Receiver object reporting the tracks.
     * @param tracks emitter tracks being reported.
     */
    @Override
    public void reportTracks(Receiver receiver, Set<Radar> tracks)
    {
        for (Jammer j : jammers)
        {
            j.checkForReactiveAssignments(tracks);
        }
    }

    /**
     * Writes the necessary data to the SIMDIS asi file to draw this object.
     * @param time update time.
     * @param asi_file SIMDIS asi file.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        super.simdisUpdate(time, asi_file);

        //  Now tell all jammers to update as well
        for (Jammer jammer : this.getJammers())
        {
            jammer.simdisUpdate(time, asi_file);
        }
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node for this platform.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);
        NodeList children = node.getChildNodes();
        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("System"))
            {
                CMAgent system = getWorld().createSystem(child);

                if (system instanceof Receiver)
                {
                    this.setReceiver((Receiver) system);
                }
                else if (system instanceof Jammer)
                {
                    this.addJammer((Jammer) system);
                }
            }
            if (child.getNodeName().equalsIgnoreCase("CookieCutterReceiver") ||
                child.getNodeName().equalsIgnoreCase("TableLookupReceiver") ||
                child.getNodeName().equalsIgnoreCase("SensitivityReceiver"))
            {
                this.setReceiver((Receiver) getWorld().createSystem(child));

            }
            else if (child.getNodeName().equalsIgnoreCase("BasicJammer") ||
                child.getNodeName().equalsIgnoreCase("AssignmentsJammer"))
            {
                this.addJammer((Jammer) getWorld().createSystem(child));
            }
        }
    }

    /**
     * Creates XML nodes containing the platform's attributes.
     * @param node root node for the platform.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        if (this.receiver != null)
        {
            this.getWorld().writeSystemToXML(receiver, node);
        }

        for (Jammer j : this.jammers)
        {
            this.getWorld().writeSystemToXML(j, node);
        }
    }
}
