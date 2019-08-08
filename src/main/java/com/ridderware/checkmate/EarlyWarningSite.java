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

import com.ridderware.fuse.AgentState;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import com.ridderware.fuse.Behavior;
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.IAgentState;
import com.ridderware.fuse.StateTime;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class for an early warning site that periodically changes its position by switching between
 * moving and stationary states.  Implements a Poisson distribution to determine
 * times for state transition.  The early warning site may be composed of multiple radar platforms
 * that move relative to the site.
 *
 * @author Jeff Ridder
 */
public class EarlyWarningSite extends UncertainLocationPlatform implements IRadarTrackReceiver
{
    private IAgentState moving_state;

    private IAgentState stationary_state;

    private double min_stationary_time;

    private double mean_stationary_time;

    private double min_moving_time;

    private double mean_moving_time;

    private ChangeStateBehavior change_state_behavior =
        new ChangeStateBehavior();

    private final static Logger logger =
        LogManager.getLogger(EarlyWarningSite.class);

    /**
     * Creates an instance of EarlyWarningSite
     * @param name name of the site.
     * @param world CMWorld in which it resides.
     * @param points arbitrary point value.
     */
    public EarlyWarningSite(String name, CMWorld world, int points)
    {
        super(name, world, points);

        this.createEarlyWarningSite();
    }

    /**
     * Creates an instance of EarlyWarningSite with parameters for GUI display.
     * @param name name of the site.
     * @param world CMWorld in which it resides.
     * @param points arbitrary point value.
     * @param ttf font.
     * @param fontSymbol font symbol.
     * @param color color.
     */
    public EarlyWarningSite(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);

        this.createEarlyWarningSite();
    }

    /**
     * Creates the EarlyWarningSite with default attributes.
     */
    protected final void createEarlyWarningSite()
    {
        this.moving_state = new AgentState("Moving");
        this.stationary_state = new AgentState("Stationary");
        this.setMinStationaryTime(0.);
        this.setMeanStationaryTime(0.);
        this.setMinMovingTime(0.);
        this.setMeanMovingTime(0.);

        this.addState(moving_state);
        this.addState(stationary_state);

        this.addBehavior(change_state_behavior);
    }

    /**
     * Resets the agent's attributes before each simulation run.
     *
     */
    @Override
    public void reset()
    {
        super.reset();

        IAgentState initial_state = null;
        double start_time = 0.;

        double cycle_time = this.mean_moving_time + this.mean_stationary_time;

        //  Set initial state
        if (cycle_time <= 0.)
        {
            initial_state = stationary_state;
        }
        else
        {
            if (this.getRNG().nextDouble() < this.mean_moving_time / cycle_time)
            {
                initial_state = moving_state;
                start_time = -getRNG().nextDouble() * this.mean_moving_time;
            }
            else
            {
                initial_state = stationary_state;
                start_time = -getRNG().nextDouble() * this.mean_stationary_time;
            }
        }

        for (Platform p : this.getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                rp.reset();
                for (Radar r : rp.getRadars())
                {
                    r.initialize();
                }

                if (initial_state.getName().equals("Moving"))
                {
                    rp.setRadarsActive(false);
                }
                else
                {
                    rp.setRadarsActive(true);
                }
            }
        }

        //  Initial location
//        this.setLocation(this.getRandomPointGenerator().randomGaussianPoint());

        logger.debug(getName() + " initial state is " + initial_state.getName() +
            " at time " + start_time);

        this.setInitialStateTime(new StateTime(initial_state, 0.));

        this.change_state_behavior.setEnabled(true);
        this.change_state_behavior.setStartTime(start_time);
    }

    /**
     * Returns whether the platform is currently in the Moving state.
     * @return true or false.
     */
    public boolean isMoving()
    {
        return getState().getName().equals("Moving");
    }

    /**
     * Declaration of a method to set an object's attributes by parsing the input XML.
     * @param node root XML node for the object.
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

            if (child.getNodeName().equalsIgnoreCase("moving-time"))
            {
                this.setMinMovingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanMovingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("stationary-time"))
            {
                this.setMinStationaryTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanStationaryTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
        }
    }

    /**
     * Declaration of a method to create XML nodes containing an object's attributes.
     * @param node root XML node for the object.
     *
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create element
        e = document.createElement("moving-time");
        e.setAttribute("min", String.valueOf(this.min_moving_time));
        e.setAttribute("mean", String.valueOf(this.mean_moving_time));
        node.appendChild(e);

        //  Create element
        e = document.createElement("stationary-time");
        e.setAttribute("min", String.valueOf(this.min_stationary_time));
        e.setAttribute("mean", String.valueOf(this.mean_stationary_time));
        node.appendChild(e);
    }

    /**
     * Called by the FUSE framework to paint the agent.
     * @param args see base class documentation.
     * @return a collection of shapes.
     *
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        Collection<Shape> boundingShapes = null;

        if (getState().getName().equals("Stationary"))
        {
            boundingShapes = super.paintAgent(args);
        }

        if (boundingShapes == null)
        {
            boundingShapes = new HashSet<Shape>();
        }

        return boundingShapes;
    }

    /**
     * Behavior to periodically change the state of the platform.  Subclass of the
     * FUSE framework Behavior class.
     * @param old_state state changing from.
     * @param new_state state changing to.
     */
    @Override
    public void stateChanged(IAgentState old_state, IAgentState new_state)
    {
        boolean setRadarsActive = false;
        //  If state changes from moving to stationary, then generate
        //  a new location for the target.
        if (new_state.getName().equals("Stationary"))
        {
            logger.debug(getName() + " setting new location");
            if (getUniverse().getCurrentTime() != getUniverse().getStartTime())
            {
                this.setLocation(this.getRandomPointGenerator().
                    randomGaussianPoint());
            }

            //  Loop over radars in radar platforms, setting them active.
            setRadarsActive = true;
        }
        else if (new_state.getName().equals("Moving") &&
            getWorld().getASIFile() != null &&
            getUniverse().getCurrentTime() != getUniverse().getStartTime())
        {
            //  Give Simdis a hit so that it knows where we're starting from.
            this.simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                getASIFile());
            for (Platform p : getSubordinates())
            {
                p.simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                    getASIFile());
            }
        }

        for (Platform p : this.getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                rp.setRadarsActive(setRadarsActive);
            }
        }
    }

    /**
     * Override of setLocation that sets the location of subordinates as well.
     * Since EarlyWarningSite may have subordinate RadarPlatform objects, my
     * location becomes their location centroid.
     * @param location new location of this platform.
     */
    @Override
    public void setLocation(Double2D location)
    {
        super.setLocation(location);

        //  When my location changes, then this requires me to set the centroid of
        //  my subordinates.
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                //  Set p centroid and randomize its location.  Its centroid
                //  is my location.
                rp.getRandomPointGenerator().setCentroid(this.getLocation().getX(), this.getLocation().
                    getY());

                rp.setLocation(rp.getRandomPointGenerator().randomGaussianPoint());
            }
        }
    }

    /**
     * Returns the minimum stationary time attribute.
     * @return minimum stationary time in seconds.
     */
    public double getMinStationaryTime()
    {
        return min_stationary_time;
    }

    /**
     * Sets the minimum stationary time attribute.
     * @param val minimum stationary time in seconds.
     */
    public void setMinStationaryTime(double val)
    {
        this.min_stationary_time = val;
    }

    /**
     * Returns the mean stationary time.
     * @return mean stationary time in seconds.
     */
    public double getMeanStationaryTime()
    {
        return mean_stationary_time;
    }

    /**
     * Sets the mean stationary time.
     * @param val mean stationary time in seconds.
     */
    public void setMeanStationaryTime(double val)
    {
        this.mean_stationary_time = val;
    }

    /**
     * Returns the minimum moving time.
     * @return min moving time in seconds.
     */
    public double getMinMovingTime()
    {
        return min_moving_time;
    }

    /**
     * Sets the minimum moving time.
     * @param val min moving time in seconds.
     */
    public void setMinMovingTime(double val)
    {
        this.min_moving_time = val;
    }

    /**
     * Returns the mean moving time.
     * @return mean moving time in seconds.
     */
    public double getMeanMovingTime()
    {
        return mean_moving_time;
    }

    /**
     * Sets the mean moving time.
     * @param val mean moving time in seconds.
     */
    public void setMeanMovingTime(double val)
    {
        this.mean_moving_time = val;
    }

    /**
     * Called by subordinate radar platforms to report tracks.
     * @param radar Radar reporting the tracks.
     * @param tracks tracks being reported by the radar.
     */
    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
        //  Relay to superior (if there is one)
        //  Do this without delay?  For now, yes.
        if (getSuperior() instanceof IRadarTrackReceiver)
        {
            ((IRadarTrackReceiver) getSuperior()).reportActiveTracks(radar,
                tracks);
        }
    }

    /**
     * Called by subordinate radar platforms to report dropped tracks.
     * @param radar Radar reporting the dropped tracks.
     * @param dropped_tracks the tracks that were dropped by the radar.
     */
    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
        //  Relay to superior (if there is one)
        //  Do this without delay?  For now, yes.
        if (getSuperior() instanceof IRadarTrackReceiver)
        {
            ((IRadarTrackReceiver) getSuperior()).reportDroppedTracks(radar,
                dropped_tracks);
        }
    }

    /**
     * A behavior for automatically changing the state of the EarlyWarningSite between
     * the moving and stationary states.
     * @author Jeff Ridder
     */
    protected class ChangeStateBehavior extends Behavior
    {
        private double start_time = 0.;

        /**
         * Called by the FUSE framework to get the next scheduled time to perform this
         * behavior.
         * @param current_time current time.
         * @return time at which to next perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double next_time = current_time;

            if (current_time == getUniverse().getStartTime())
            {
                next_time = this.start_time;
            }

            if (getStatus() == Platform.Status.ACTIVE)
            {
                double move_cycle = min_moving_time + mean_moving_time;
                double stationary_cycle = min_stationary_time +
                    mean_stationary_time;

                if (getState().getName().equals("Moving"))
                {
                    while (next_time <= current_time && move_cycle > 0.)
                    {
                        next_time += min_moving_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_moving_time - min_moving_time);
                    }
                }
                else
                {
                    while (next_time <= current_time && stationary_cycle > 0.)
                    {
                        next_time += min_stationary_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_stationary_time -
                            min_stationary_time);
                    }
                }

//                logger.debug(getName()+" next scheduled time is "+next_time);
            }

            return next_time;
        }

        /**
         * Sets the starting time of the initial behavior.
         * @param start_time start time.
         */
        public void setStartTime(double start_time)
        {
            this.start_time = start_time;
        }

        /**
         * Called by the FUSE framework to perform the behavior.
         * @param current_time time.
         */
        @Override
        public void perform(double current_time)
        {
            if (getState().getName().equals("Moving"))
            {
                logger.debug(getName() + " changing to stationary at " +
                    current_time);
                requestNextState(stationary_state);
            }
            else
            {
                logger.debug(getName() + " changing to moving at " +
                    current_time);
                requestNextState(moving_state);
            }
        }
    }
}
