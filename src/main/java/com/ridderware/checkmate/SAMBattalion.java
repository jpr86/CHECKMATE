/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2008 Jeff Ridder
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
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.Space;
import com.ridderware.fuse.IAgentState;
import com.ridderware.fuse.StateTime;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A representation of a SAM Battalion.  A SAM Battalion is a platform with subordinate
 * SAM sites.  The battalion determines the locations and states of subordinate SAM sites,
 * and acts as the target assigner for the battalion.
 *
 * @author Jeff Ridder
 */
public class SAMBattalion extends TargetAssignmentC2
{
    //  Transition into teardown state from firing state upon completion of engagement.
    //  OR transition into teardown state from deployed state after a maximum time.
    //  Transition into moving state after teardown time complete.
    private IAgentState teardown_state;

    //  Transition into setup from moving state after moving for predetermined amount of time.
    //  Transition from setup into deployed state after setup time complete.
    private IAgentState setup_state;

    //  Transition into moving state from hiding state after hide time complete.
    //  Transition into moving state from teardown state after teardown time complete.
    //  Transition from moving state to hiding state after move time complete.
    //  Transition from moving state to setup state after move time complete.
    private IAgentState moving_state;

    //  Transition into hiding state from moving state after move time complete.
    //  Transition from hiding state into moving state after hide time complete.
    private IAgentState hiding_state;

    //  Transition into deployed state from setup state after setup time complete.
    //  Transition from deployed state into firing state if engagement conditions are met.
    //  Transition from deployed state into teardown state if max time reached.
    private IAgentState deployed_state;
    //  Set by subordinate SAM Sites if they are engaging.
    private int num_engaging;
    //  For time-based transitions.
    private double min_moving_time;

    private double mean_moving_time;

    private double min_hiding_time;

    private double mean_hiding_time;

    private double min_deployed_time;

    private double mean_deployed_time;

    private double min_setup_time;

    private double mean_setup_time;

    private double min_teardown_time;

    private double mean_teardown_time;

    private double max_speed;   //  in knots

    private double acquisition_threshold;

    private Double2D destination_location = null;  //  for moves

    private ChangeStateBehavior change_state_behavior =
        new ChangeStateBehavior();

    private final static Logger logger = LogManager.getLogger(SAMBattalion.class);

    /**
     * Creates a new instance of SAMSite.
     * @param name name off the site.
     * @param world CMWorld in which the site plays.
     * @param points point value of the site.
     */
    public SAMBattalion(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createSAMBattalion();

    }

    /**
     * Creates a new instance of SAMSite with attributes for graphical display.
     * @param name name of the site.
     * @param world CMWorld in which the site plays.
     * @param points point value of the site.
     * @param ttf font containing the symbol to be drawn.
     * @param fontSymbol font symbol to be drawn.
     * @param color color of the symbol.
     */
    public SAMBattalion(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createSAMBattalion();
    }

    /**
     * Sets the initial attribute values of the newly minted SAMSite.
     */
    protected final void createSAMBattalion()
    {
        this.num_engaging = 0;
        this.deployed_state = new AgentState("Deployed");
        this.hiding_state = new AgentState("Hiding");
        this.moving_state = new AgentState("Moving");
        this.setup_state = new AgentState("Setup");
        this.teardown_state = new AgentState("Teardown");
        this.addState(deployed_state);
        this.addState(hiding_state);
        this.addState(moving_state);
        this.addState(setup_state);
        this.addState(teardown_state);

        this.setMinDeployedTime(0.);
        this.setMeanDeployedTime(0.);
        this.setMinHidingTime(0.);
        this.setMeanHidingTime(0.);
        this.setMinMovingTime(0.);
        this.setMeanMovingTime(0.);
        this.setMinSetupTime(0.);
        this.setMeanSetupTime(0.);
        this.setMinTeardownTime(0.);
        this.setMeanTeardownTime(0.);

        this.setMaxSpeed(0.);

        this.setAcquisitionThreshold(1.);

        this.addBehavior(change_state_behavior);
    }

    /**
     * Sets the threshold at which target acquisition radars will be activated as a multiple of the lethal range of the SAM
     * (e.g., threshold = 1 implies TAR activation at max lethal range).
     * @param acquisition_threshold acquisition threshold.
     */
    public void setAcquisitionThreshold(double acquisition_threshold)
    {
        this.acquisition_threshold =
            acquisition_threshold;
    }

    /**
     * Returns the acquisition threshold.
     * @return acquisition threshold.
     */
    public double getAcquisitionThreshold()
    {
        return this.acquisition_threshold;
    }

    /**
     * Keeps track of the number of subordinate SAM sites currently involved in 
     * engagements.  A SAM Battalion isn't free to move until/unless the number engaging is 0.
     * @param engaging used by SAM sites to inform the battalion as to its engagement status (e.g., +1 for new engagements, -1 for ended engagements).
     * @return the current number of engagements by this battalion.
     */
    public int engaging(int engaging)
    {
        this.num_engaging += engaging;

        return this.num_engaging;
    }

    /**
     * Called by the framework to reset the object to initial conditions prior
     * to each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
        this.num_engaging = 0;
        IAgentState initial_state = null;
        double start_time = 0.;

        double cycle_time = this.mean_deployed_time + this.mean_hiding_time +
            this.mean_moving_time + this.mean_setup_time +
            this.mean_teardown_time;

        if (cycle_time <= 0.)
        {
            initial_state = deployed_state;
        }
        else
        {
            double rn = getRNG().nextDouble();

            if (rn < this.mean_moving_time / cycle_time)
            {
                initial_state = moving_state;
                start_time = -getRNG().nextDouble() * this.mean_moving_time;
            }
            else
            {
                if (rn < (this.mean_moving_time + this.mean_deployed_time) /
                    cycle_time)
                {
                    initial_state = deployed_state;
                    start_time = -getRNG().nextDouble() *
                        this.mean_deployed_time;
                }
                else
                {
                    if (rn < (this.mean_moving_time + this.mean_deployed_time +
                        this.mean_hiding_time) / cycle_time)
                    {
                        initial_state = hiding_state;
                        start_time = -getRNG().nextDouble() *
                            this.mean_hiding_time;
                    }
                    else
                    {
                        if (rn < (this.mean_moving_time +
                            this.mean_deployed_time +
                            this.mean_hiding_time + this.mean_setup_time) /
                            cycle_time)
                        {
                            initial_state = setup_state;
                            start_time = -getRNG().nextDouble() *
                                this.mean_setup_time;
                        }
                        else
                        {
                            initial_state = teardown_state;
                            start_time = -getRNG().nextDouble() *
                                this.mean_teardown_time;
                        }
                    }
                }
            }
        }

        if (initial_state == deployed_state)
        {
            this.setAssignable(true);
        }
        else
        {
            this.setAssignable(false);
        }

        for (Platform p : this.getSubordinates())
        {
            if (p instanceof SAMSite)
            {
                SAMSite ss = (SAMSite) p;
                ss.setInitialState(initial_state.getName());
            }
        }

        logger.debug(getName() + " initial state is " + initial_state.getName() +
            " at time " + start_time);

        this.setInitialStateTime(new StateTime(initial_state, 0.));

        this.change_state_behavior.setEnabled(true);
        this.change_state_behavior.setStartTime(start_time);
    }

    /**
     * Parses the XML node, setting the attributes of the object.
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

            if (child.getNodeName().equalsIgnoreCase("max-speed"))
            {
                this.setMaxSpeed(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("moving-time"))
            {
                this.setMinMovingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanMovingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("hiding-time"))
            {
                this.setMinHidingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanHidingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("deployed-time"))
            {
                this.setMinDeployedTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanDeployedTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("setup-time"))
            {
                this.setMinSetupTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanSetupTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("teardown-time"))
            {
                this.setMinTeardownTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanTeardownTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("acquisition-threshold"))
            {
                this.setAcquisitionThreshold(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Creates sub-elements of the XML node containing attributes of this object.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

//  Create elephants
        e = document.createElement("max-speed");
        e.setTextContent(String.valueOf(this.max_speed));
        node.appendChild(e);

        e = document.createElement("moving-time");
        e.setAttribute("min", String.valueOf(this.min_moving_time));
        e.setAttribute("mean", String.valueOf(this.mean_moving_time));
        node.appendChild(e);

        e = document.createElement("hiding-time");
        e.setAttribute("min", String.valueOf(this.min_hiding_time));
        e.setAttribute("mean", String.valueOf(this.mean_hiding_time));
        node.appendChild(e);

        e = document.createElement("deployed-time");
        e.setAttribute("min", String.valueOf(this.min_deployed_time));
        e.setAttribute("mean", String.valueOf(this.mean_deployed_time));
        node.appendChild(e);

        e = document.createElement("setup-time");
        e.setAttribute("min", String.valueOf(this.min_setup_time));
        e.setAttribute("mean", String.valueOf(this.mean_setup_time));
        node.appendChild(e);

        e = document.createElement("teardown-time");
        e.setAttribute("min", String.valueOf(this.min_teardown_time));
        e.setAttribute("mean", String.valueOf(this.mean_teardown_time));
        node.appendChild(e);

        e = document.createElement("acquisition-threshold");
        e.setTextContent(String.valueOf(this.acquisition_threshold));
        node.appendChild(e);
    }

    /**
     * Called by the framework to paint the agent for GUI display.
     * @param args arguments.
     * @return collection of shapes to be drawn.
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        Collection<Shape> boundingShapes = null;

        if (getState().getName().equals("Setup") ||
            getState().getName().equals("Deployed") ||
            getState().getName().equals("Teardown"))
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
     * Sets the location of this platform and subordinates relative to this.
     * @param location new location.
     */
    @Override
    public void setLocation(Double2D location)
    {
        super.setLocation(location);

        //  When my location changes, then this requires me to set the centroid of
        //  my subordinates.
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof UncertainLocationPlatform)
            {
                UncertainLocationPlatform rp = (UncertainLocationPlatform) p;

                //  Set p centroid and randomize its location.  Its centroid
                //  is my location.
                rp.getRandomPointGenerator().setCentroid(this.getLocation().getX(), this.getLocation().
                    getY());

                rp.setLocation(rp.getRandomPointGenerator().randomGaussianPoint());
            }

        }
    }

    /**
     * 
     */
    public void tearDown()
    {
        if (engaging(0) == 0)
        {
            this.activateRadars(false);
            this.requestNextState(teardown_state);
            //  Trick the behavior into resetting the transition time.
            this.change_state_behavior.setEnabled(false);
            this.change_state_behavior.setEnabled(true);

            for (Platform p : getSubordinates())
            {
                if (p instanceof SAMSite)
                {
                    ((SAMSite) p).changeState(teardown_state.getName());
                }

            }
        }
    }

    /**
     * Sets the maximum speed at which the site can travel over ground when moving.
     * @param max_speed maximum speed in knots.
     */
    public void setMaxSpeed(double max_speed)
    {
        this.max_speed = max_speed;
    }

    /**
     * Returns the maximum speed at which the site can travel over ground when moving.
     * @return maximum speed in knots.
     */
    public double getMaxSpeed()
    {
        return this.max_speed;
    }

    /**
     * Sets the minimum amount of time for each move.
     * @param time time in seconds.
     */
    public void setMinMovingTime(double time)
    {
        this.min_moving_time = time;
    }

    /**
     * Returns the minimum time for each move.
     * @return time in seconds.
     */
    public double getMinMovingTime()
    {
        return this.min_moving_time;
    }

    /**
     * Sets the mean amount of time for each move.
     * @param time time in seconds.
     */
    public void setMeanMovingTime(double time)
    {
        this.mean_moving_time = time;
    }

    /**
     * Returns the mean time for each move.
     * @return time in seconds.
     */
    public double getMeanMovingTime()
    {
        return this.mean_moving_time;
    }

    /**
     * Sets the minimum amount of time to hide.
     * @param time time in seconds.
     */
    public void setMinHidingTime(double time)
    {
        this.min_hiding_time = time;
    }

    /**
     * Returns the minimum hiding time.
     * @return time in seconds.
     */
    public double getMinHidingTime()
    {
        return this.min_hiding_time;
    }

    /**
     * Sets the mean amount of time to hide.
     * @param time time in seconds.
     */
    public void setMeanHidingTime(double time)
    {
        this.mean_hiding_time = time;
    }

    /**
     * Returns the mean amount of time to hide.
     * @return time in seconds.
     */
    public double getMeanHidingTime()
    {
        return this.mean_hiding_time;
    }

    /**
     * Sets the minimum amount of time to set up the site.
     * @param time time in seconds.
     */
    public void setMinSetupTime(double time)
    {
        this.min_setup_time = time;
    }

    /**
     * Returns the minimum amount of time to set up the site.
     * @return time in seconds.
     */
    public double getMinSetupTime()
    {
        return this.min_setup_time;
    }

    /**
     * Sets the mean time to set up the site.
     * @param time time in seconds.
     */
    public void setMeanSetupTime(double time)
    {
        this.mean_setup_time = time;
    }

    /**
     * Returns the mean time to set up the site.
     * @return time in seconds.
     */
    public double getMeanSetupTime()
    {
        return this.mean_setup_time;
    }

    /**
     * Sets the minimum amount of time to tear down the site.
     * @param time time in seconds.
     */
    public void setMinTeardownTime(double time)
    {
        this.min_teardown_time = time;
    }

    /**
     * Returns the minimum amount of time to tear down the site.
     * @return time in seconds.
     */
    public double getMinTeardownTime()
    {
        return this.min_teardown_time;
    }

    /**
     * Sets the mean time to tear down the site.
     * @param time time in seconds.
     */
    public void setMeanTeardownTime(double time)
    {
        this.mean_teardown_time = time;
    }

    /**
     * Returns the mean time to tear down the site.
     * @return time in seconds.
     */
    public double getMeanTeardownTime()
    {
        return this.mean_teardown_time;
    }

    /**
     * Sets the minimum amount of time the site will be deployed without firing.
     * @param time time in seconds.
     */
    public void setMinDeployedTime(double time)
    {
        this.min_deployed_time = time;
    }

    /**
     * Returns the minimum amount of time the site will be deployed without firing.
     * @return time in seconds.
     */
    public double getMinDeployedTime()
    {
        return this.min_deployed_time;
    }

    /**
     * Sets the mean time deployed without firing.
     * @param time time in seconds.
     */
    public void setMeanDeployedTime(double time)
    {
        this.mean_deployed_time = time;
    }

    /**
     * Returns the mean time deployed without firing.
     * @return time in seconds.
     */
    public double getMeanDeployedTime()
    {
        return this.mean_deployed_time;
    }

    @Override
    public void processTracks(double time)
    {
        super.processTracks(time);
        boolean deactivate = true;
        for (Platform t : this.getAssignedTargets())
        {
            double engageability = this.getEngageability(t);

            //  Turn on TA at the appropriate time
            if (this.getState() == deployed_state &&
                engageability < this.getAcquisitionThreshold())
            {
                this.activateRadars(true);
                deactivate = false;
                logger.debug(this.getName() +
                    " ACTIVATING the Battalion TAs at time " + getUniverse().
                    getCurrentTime());
                break;
            }
        }

        if (deactivate)
        {
            this.activateRadars(false);
        }
    }

    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
        super.reportActiveTracks(radar, tracks);

        //  Need also to push this info down to the SAM Sites.
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof SAMSite)
            {
                ((SAMSite) p).reportActiveTracks(radar, tracks);
            }
        }
    }

    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
        super.reportDroppedTracks(radar, dropped_tracks);
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof SAMSite)
            {
                ((SAMSite) p).reportDroppedTracks(radar, dropped_tracks);
            }
        }
    }

    /**
     * Sets the activation state of all subordinate radars.
     * @param activate true if radars are to be activated, false otherwise.
     */
    protected void activateRadars(boolean activate)
    {
        for (Platform p : getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                for (Radar r : rp.getRadars())
                {
                    if (r.getFunction() == Radar.Function.TA)
                    {
                        r.setActive(activate);
                    }

                }
            }
        }
    }

    /**
     * Called by the simulation framework to report state changes for the agent.
     * @param old_state previous (from) state.
     * @param new_state next (to) state.
     */
    @Override
    public void stateChanged(IAgentState old_state, IAgentState new_state)
    {
        logger.debug(this.getName() + " state changed from " +
            old_state.getName() + " to " + new_state.getName() + " at time " +
            getUniverse().getCurrentTime());

        if (new_state.getName().equals("Deployed"))
        {
            this.setAssignable(true);
        }
        else
        {
            this.setAssignable(false);
        }

        if (new_state == moving_state && getWorld().getASIFile() != null &&
            getUniverse().getCurrentTime() != getUniverse().getStartTime())
        {
            //  Give SIMDIS a hit and tell subordinates to do the same
            this.simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                getASIFile());
            for (Platform p : getSubordinates())
            {
                p.simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                    getASIFile());
            }

        }

        if (old_state.getName().equals("Moving"))
        {
            //  Set the location
            if (destination_location != null)
            {
                setLocation(destination_location);
            }

        }

        this.change_state_behavior.setEnabled(true);
    }

    private void setDestinationLocation(double max_distance)
    {
        if (max_distance == 0.)
        {
            destination_location = new Double2D(getLocation().getX(), getLocation().
                getY());   //  can't move!
        }
        else
        {
            //  The farther away I am from the centroid, the more likely
            //  I am to steer toward the centroid.  Therefore, it is a question
            //  of biasing the heading based on distance from the centroid.
            Space space = getUniverse().getSpace();


            if (space != null)
            {
                double xmin = 0.0;
                double xmax = 0.0;
                double ymin = 0.0;
                double ymax = 0.0;
                if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("ENU"))
                {
                    xmin = space.getXmin();
                    xmax =
                        space.getXmax();
                    ymin =
                        space.getYmin();
                    ymax =
                        space.getYmax();
                }
                else
                {
                    if (CMWorld.getEarthModel().getCoordinateSystem().
                        equalsIgnoreCase("LLA"))
                    {
                        xmin = space.getYmin();
                        xmax =
                            space.getYmax();
                        ymin =
                            space.getXmin();
                        ymax =
                            space.getXmax();
                    }

                }

                do
                {
                    RandomPointGenerator rpg = getRandomPointGenerator();
                    Double2D centroid = rpg.getCentroid();
                    Double3D centroid3D = new Double3D(centroid);
                    double d =
                        CMWorld.getEarthModel().trueDistance(getLocation(),
                        centroid3D);
                    if (d > 0.)
                    {
                        double centroid_heading = CMWorld.getEarthModel().
                            azimuthAngle(getLocation(), centroid3D);
                        double sigma = rpg.getRandomRadius() / d * Math.PI / 2.;
                        double angle = centroid_heading + getRNG().nextGaussian() *
                            sigma;

                        double distance = getRNG().nextDouble() * max_distance;
                        Double3D new_loc = CMWorld.getEarthModel().
                            projectLocation(getLocation(), distance, angle);
                        destination_location =
                            new Double2D(new_loc.getX(),
                            new_loc.getY());
                    }
                    else
                    {
                        destination_location = new Double2D(getLocation().getX(), getLocation().
                            getY());
                        break;
                    }

                }
                while (destination_location.getX() < xmin ||
                    destination_location.getX() > xmax ||
                    destination_location.getY() < ymin ||
                    destination_location.getY() > ymax);
            }




        }
    }

    /**
     * Behavior to automatically handle time-based transitions between states.
     */
    protected class ChangeStateBehavior extends Behavior
    {
        private IAgentState prev_state = null;

        private IAgentState cur_state = null;

        private IAgentState next_state = null;

        private double start_time = 0.;

        /**
         * Called by the simulation framework to get the next time at which to perform
         * the behavior.
         * @param current_time current simulation time.
         * @return next time to perform the behavior.
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
                double setup_cycle = min_setup_time + mean_setup_time;
                double deployed_cycle = min_deployed_time + mean_deployed_time;
                double teardown_cycle = min_teardown_time + mean_teardown_time;
                double hide_cycle = min_hiding_time + mean_hiding_time;

                if (getState().getName().equals("Moving") && move_cycle > 0.)
                {
                    while (next_time <= current_time)
                    {
                        next_time += min_moving_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_moving_time - min_moving_time);
                    }

                    cur_state = getState();

                    //  Now select the next state -- 3 choices:
                    //  1) hide
                    //  2) set up
                    //  3) deploy (only if setup time = 0.)
                    if (prev_state != null &&
                        prev_state.getName().equals("Hiding"))
                    {
                        //  If previous state was hiding and I'm moving, then setup or deploy.
                        if (setup_cycle > 0.)
                        {
                            next_state = setup_state;
                        }
                        else
                        {
                            next_state = deployed_state;
                        }
                    }
                    else if (prev_state != null &&
                        prev_state.getName().equals("Teardown") &&
                        teardown_cycle > 0.)
                    {
                        //  if previous state is teardown and I'm moving, then I will either hide or setup next
                        if (hide_cycle > 0.)
                        {
                            next_state = hiding_state;
                        }
                        else
                        {
                            if (setup_cycle > 0.)
                            {
                                next_state = setup_state;
                            }
                            else
                            {
                                //  Fallback condition if the other states are unavailable.
                                next_state = deployed_state;
                            }
                        }
                    }
                    else if (getRNG().nextDouble() < 0.5)
                    {
                        if (setup_cycle > 0.)
                        {
                            next_state = setup_state;
                        }
                        else
                        {
                            next_state = deployed_state;
                        }
                    }
                    else
                    {
                        if (hide_cycle > 0.)
                        {
                            next_state = hiding_state;
                        }
                        else
                        {
                            //  Fallback again.
                            next_state = deployed_state;
                        }
                    }

                    //  Finally, generate the destination point for the move.
                    double max_distance =
                        (next_time - current_time) * max_speed / 3600.;

                    //  Now generate a point < max_distance away from here, but compatible
                    //  with the centroid.
                    setDestinationLocation(max_distance);
                }
                else if (getState().getName().equals("Hiding") && hide_cycle >
                    0.)
                {
                    while (next_time <= current_time)
                    {
                        next_time += min_hiding_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_hiding_time -
                            min_hiding_time);
                    }

                    cur_state = getState();
                    //  No matter what, when I'm done hiding I am going to move again.
                    next_state = moving_state;
                }
                else if (getState().getName().equals("Setup") && setup_cycle >
                    0.)
                {
                    while (next_time <= current_time)
                    {
                        next_time += min_setup_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_setup_time -
                            min_setup_time);
                    }

                    cur_state = getState();
                    //  No matter what, I will transition to deployed after setting up.
                    next_state = deployed_state;
                }
                else if (getState().getName().equals("Deployed") &&
                    deployed_cycle > 0.)
                {
                    while (next_time <= current_time)
                    {
                        next_time += min_deployed_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_deployed_time -
                            min_deployed_time);
                    }

                    cur_state = getState();
                    //  This is a time-based transition.  It is possible that this will be
                    //  interrupted by firing.  However, the time transition will be to
                    //  Teardown or, if unavailable, to moving.
                    if (teardown_cycle > 0.)
                    {
                        next_state = teardown_state;
                    }
                    else
                    {
                        //  Fallback
                        next_state = moving_state;
                    }
                }
                else if (getState().getName().equals("Teardown") &&
                    teardown_cycle > 0.)
                {
                    while (next_time <= current_time)
                    {
                        next_time += min_teardown_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_teardown_time -
                            min_teardown_time);
                    }

                    cur_state = getState();
                    //  No matter what, we will move after tearing down.
                    next_state = moving_state;

                }
            }

            return next_time;
        }

        /**
         * Sets the time to start the behavior.
         * @param start_time start time.
         */
        public void setStartTime(double start_time)
        {
            this.start_time = start_time;
        }

        /**
         * Called by the simulation framework to perform the behavior.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            logger.debug(getName() + " " + cur_state.getName() +
                " attempting transition to " + next_state.getName());

            if (getState() == cur_state &&
                !(getState() == deployed_state && engaging(0) != 0))
            {
                prev_state = cur_state;
                requestNextState(next_state);
                logger.debug(getName() + " requested next state: " +
                    next_state.getName());
                for (Platform p : getSubordinates())
                {
                    if (p instanceof SAMSite)
                    {
                        ((SAMSite) p).changeState(next_state.getName());
                    }
                }
            }
        }
    }
}
