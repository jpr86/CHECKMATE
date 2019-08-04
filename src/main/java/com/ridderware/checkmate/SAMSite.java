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
 * A representation of a SAM site.  A SAM site is a type of platform node with
 * multiple subordinate platforms.  The subordinates are radar platforms and
 * weapons platforms.  The SAM site responds to the environment and changes
 * states based on its actions.  During the moving state, the site will move to
 * a new location consistent with its speed and duration of movement.  Most
 * transitions are time-driven.  In other words, when a time limit for that
 * state is reached it will transition to the next state (e.g., hiding to
 * moving).  The exception is the firing state.  The site will transition into
 * the firing state when particular conditions are met, and transition out of
 * the firing state when other conditions are met.  This SAM site supports three
 * different engagement modes:  fully guided, semi-guided, and unguided.  When
 * engaging in the fully guided mode, the SAM site uses a traditional kill chain
 * of TAR --> TTR --> fire.  When engaging in semi-guided mode, the SAM site uses
 * unconventional tactics of TAR --> fire --> TTR in order to minimize emissions.
 * When engaging in unguided mode, the SAM site does not emit at all, but fires
 * a SAM ballistically toward the target using track data from the IADS.
 *
 * @author Jeff Ridder
 */
public class SAMSite extends TargetAssignmentC2
{
    /**
     * Enumeration of engagement modes.
     */
    public enum EngagementMode
    {
        /** Fully Guided -- a proper kill-chain. */
        FULLYGUIDED,
        /** Semi-Guided -- an unconventional kill-chain with late and short emissions. */
        SEMIGUIDED,
        /** Unguided -- an unconventional kill-chain that uses no emissions. */
        UNGUIDED
    }

    //  Transition from deployed into firing state upon reaching some condition.
    //  Transition from firing into teardown state upon completing engagement.
    private IAgentState firing_state;

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

    private double engagement_threshold;

    private double fully_guided_engagement_probability;

    private double semi_guided_engagement_probability;

    private double fully_guided_acquisition_threshold;

    private double fully_guided_tracking_threshold;

    private double fully_guided_firing_threshold;

    private double semi_guided_acquisition_threshold;

    private double semi_guided_firing_threshold;

    private double semi_guided_tracking_time;

    private double unguided_firing_threshold;

    private Double2D destination_location = null;  //  for moves

    private EngagedTarget engaged_target = null;

    private ChangeStateBehavior change_state_behavior =
        new ChangeStateBehavior();

    private ActivateRadarsBehavior activate_radars_behavior =
        new ActivateRadarsBehavior();

    private Set<Radar> subordinate_radars;

    private final static Logger logger = LogManager.getLogger(SAMSite.class);

    /**
     * Creates a new instance of SAMSite.
     * @param name name off the site.
     * @param world CMWorld in which the site plays.
     * @param points point value of the site.
     */
    public SAMSite(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createSAMSite();
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
    public SAMSite(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createSAMSite();
    }

    /**
     * Sets the initial attribute values of the newly minted SAMSite.
     */
    protected final void createSAMSite()
    {
        this.deployed_state = new AgentState("Deployed");
        this.firing_state = new AgentState("Firing");
        this.hiding_state = new AgentState("Hiding");
        this.moving_state = new AgentState("Moving");
        this.setup_state = new AgentState("Setup");
        this.teardown_state = new AgentState("Teardown");
        this.addState(deployed_state);
        this.addState(firing_state);
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

        this.setEngagementThreshold(1.1);
        this.setFullyGuidedEngagementProbability(1.);
        this.setSemiGuidedEngagementProbability(0.);

        this.setFullyGuidedAcquisitionThreshold(1.);
        this.setFullyGuidedTrackingThreshold(1.);
        this.setFullyGuidedFiringThreshold(1.);

        this.setSemiGuidedAcquisitionThreshold(1.);
        this.setSemiGuidedFiringThreshold(1.);
        this.setSemiGuidedTrackingTime(0.);

        this.setUnguidedFiringThreshold(1.);

        this.subordinate_radars = new HashSet<>();

        this.addBehavior(change_state_behavior);

        this.addBehavior(activate_radars_behavior);
    }

    public void setInitialState(String state_name)
    {
        for (IAgentState s : this.getStates())
        {
            if (s.getName().equals(state_name))
            {
                IAgentState initial_state = s;

                this.setInitialStateTime(new StateTime(initial_state, 0.));
                logger.debug(getName() + " initial state is " +
                    initial_state.getName());

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
                    if (p instanceof RadarPlatform)
                    {
                        RadarPlatform rp = (RadarPlatform) p;

                        rp.reset();
                        for (Radar r : rp.getRadars())
                        {
                            r.initialize();
                        }

                        if (initial_state.getName().equals("Deployed") &&
                            !(this.getSuperior() instanceof TargetAssignmentC2))
                        {
                            this.activateRadars(true);
                        }
                        else
                        {
                            this.activateRadars(false);
                        }
                    }
                    else
                    {
                        if (p instanceof SAMTEL)
                        {
                            SAMTEL tel = (SAMTEL) p;

                            tel.reset();
                        }
                    }
                }
                break;
            }
        }
    }

    /**
     * Called by the framework to reset the object to initial conditions prior
     * to each simulation run.
     */
    @Override
    public void reset()
    {
        //  TODO:  Fix this to work with SAMBattalion
        super.reset();
        double start_time = 0.;
        this.engaged_target = null;

        String initial_state_name = "";
        if (!(getSuperior() instanceof SAMBattalion))
        {
            double cycle_time = this.mean_deployed_time + this.mean_hiding_time +
                this.mean_moving_time + this.mean_setup_time +
                this.mean_teardown_time;

            if (cycle_time <= 0.)
            {
                initial_state_name = deployed_state.getName();
            }
            else
            {
                double rn = getRNG().nextDouble();

                if (rn < this.mean_moving_time / cycle_time)
                {
                    initial_state_name = moving_state.getName();
                    start_time = -getRNG().nextDouble() * this.mean_moving_time;
                }
                else
                {
                    if (rn < (this.mean_moving_time + this.mean_deployed_time) /
                        cycle_time)
                    {
                        initial_state_name = deployed_state.getName();
                        start_time = -getRNG().nextDouble() *
                            this.mean_deployed_time;
                    }
                    else
                    {
                        if (rn < (this.mean_moving_time +
                            this.mean_deployed_time +
                            this.mean_hiding_time) / cycle_time)
                        {
                            initial_state_name = hiding_state.getName();
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
                                initial_state_name = setup_state.getName();
                                start_time = -getRNG().nextDouble() *
                                    this.mean_setup_time;
                            }
                            else
                            {
                                initial_state_name = teardown_state.getName();
                                start_time = -getRNG().nextDouble() *
                                    this.mean_teardown_time;
                            }
                        }
                    }
                }
            }
        }

        this.subordinate_radars.clear();
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                for (Radar r : rp.getRadars())
                {
                    this.subordinate_radars.add(r);
                }
            }
        }

        this.setInitialState(initial_state_name);

        logger.debug(getName() + " initial state is " + initial_state_name +
            " at time " + start_time);

        this.change_state_behavior.setEnabled(true);
        this.change_state_behavior.setStartTime(start_time);

        this.activate_radars_behavior.setEnabled(false);
        this.activate_radars_behavior.setActivationTime(start_time);
    }

    /**
     * Returns the currently engaged target.
     * @return an EngagedTarget object.
     */
    public EngagedTarget getEngagedTarget()
    {
        return this.engaged_target;
    }

    public void setEngagedTarget(EngagedTarget tgt)
    {
        if (this.engaged_target != tgt)
        {
            this.engaged_target = tgt;

            if (getSuperior() instanceof SAMBattalion)
            {
                if (tgt != null)
                {
                    ((SAMBattalion) getSuperior()).engaging(1);
                }
                else
                {
                    ((SAMBattalion) getSuperior()).engaging(-1);
                }
            }
        }
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
            else
            {
                if (child.getNodeName().equalsIgnoreCase("moving-time"))
                {
                    this.setMinMovingTime(Double.parseDouble(child.getAttributes().
                        getNamedItem("min").getTextContent()));
                    this.setMeanMovingTime(Double.parseDouble(child.getAttributes().
                        getNamedItem("mean").getTextContent()));
                }
                else
                {
                    if (child.getNodeName().equalsIgnoreCase("hiding-time"))
                    {
                        this.setMinHidingTime(Double.parseDouble(child.getAttributes().
                            getNamedItem("min").getTextContent()));
                        this.setMeanHidingTime(Double.parseDouble(child.getAttributes().
                            getNamedItem("mean").getTextContent()));
                    }
                    else
                    {
                        if (child.getNodeName().equalsIgnoreCase("deployed-time"))
                        {
                            this.setMinDeployedTime(Double.parseDouble(child.getAttributes().
                                getNamedItem("min").getTextContent()));
                            this.setMeanDeployedTime(Double.parseDouble(child.getAttributes().
                                getNamedItem("mean").getTextContent()));
                        }
                        else
                        {
                            if (child.getNodeName().equalsIgnoreCase("setup-time"))
                            {
                                this.setMinSetupTime(Double.parseDouble(child.getAttributes().
                                    getNamedItem("min").getTextContent()));
                                this.setMeanSetupTime(Double.parseDouble(child.getAttributes().
                                    getNamedItem("mean").getTextContent()));
                            }
                            else
                            {
                                if (child.getNodeName().equalsIgnoreCase("teardown-time"))
                                {
                                    this.setMinTeardownTime(Double.parseDouble(child.getAttributes().
                                        getNamedItem("min").getTextContent()));
                                    this.setMeanTeardownTime(Double.parseDouble(child.getAttributes().
                                        getNamedItem("mean").getTextContent()));
                                }
                                else
                                {
                                    if (child.getNodeName().equalsIgnoreCase("engagement-threshold"))
                                    {
                                        this.setEngagementThreshold(Double.parseDouble(child.getTextContent()));
                                    }
                                    else
                                    {
                                        if (child.getNodeName().equalsIgnoreCase("fully-guided-engagement-probability"))
                                        {
                                            this.setFullyGuidedEngagementProbability(Double.parseDouble(child.getTextContent()));
                                        }
                                        else
                                        {
                                            if (child.getNodeName().
                                                equalsIgnoreCase("semi-guided-engagement-probability"))
                                            {
                                                this.setSemiGuidedEngagementProbability(Double.parseDouble(child.getTextContent()));
                                            }
                                            else
                                            {
                                                if (child.getNodeName().
                                                    equalsIgnoreCase("fully-guided-acquisition-threshold"))
                                                {
                                                    this.setFullyGuidedAcquisitionThreshold(Double.parseDouble(child.getTextContent()));
                                                }
                                                else
                                                {
                                                    if (child.getNodeName().
                                                        equalsIgnoreCase("fully-guided-tracking-threshold"))
                                                    {
                                                        this.setFullyGuidedTrackingThreshold(Double.parseDouble(child.getTextContent()));
                                                    }
                                                    else
                                                    {
                                                        if (child.getNodeName().
                                                            equalsIgnoreCase("fully-guided-firing-threshold"))
                                                        {
                                                            this.setFullyGuidedFiringThreshold(Double.parseDouble(child.getTextContent()));
                                                        }
                                                        else
                                                        {
                                                            if (child.getNodeName().
                                                                equalsIgnoreCase("semi-guided-acquisition-threshold"))
                                                            {
                                                                this.setSemiGuidedAcquisitionThreshold(Double.parseDouble(child.getTextContent()));
                                                            }
                                                            else
                                                            {
                                                                if (child.getNodeName().
                                                                    equalsIgnoreCase("semi-guided-firing-threshold"))
                                                                {
                                                                    this.setSemiGuidedFiringThreshold(Double.parseDouble(child.getTextContent()));
                                                                }
                                                                else
                                                                {
                                                                    if (child.getNodeName().
                                                                        equalsIgnoreCase("semi-guided-tracking-time"))
                                                                    {
                                                                        this.setSemiGuidedTrackingTime(Double.parseDouble(child.getTextContent()));
                                                                    }
                                                                    else
                                                                    {
                                                                        if (child.getNodeName().
                                                                            equalsIgnoreCase("unguided-firing-threshold"))
                                                                        {
                                                                            this.setUnguidedFiringThreshold(Double.parseDouble(child.getTextContent()));
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

        e = document.createElement("engagement-threshold");
        e.setTextContent(String.valueOf(this.getEngagementThreshold()));
        node.appendChild(e);

        e = document.createElement("fully-guided-engagement-probability");
        e.setTextContent(String.valueOf(this.getFullyGuidedEngagementProbability()));
        node.appendChild(e);

        e = document.createElement("semi-guided-engagement-probability");
        e.setTextContent(String.valueOf(this.getSemiGuidedEngagementProbability()));
        node.appendChild(e);

        e = document.createElement("fully-guided-acquisition-threshold");
        e.setTextContent(String.valueOf(this.getFullyGuidedAcquisitionThreshold()));
        node.appendChild(e);

        e = document.createElement("fully-guided-tracking-threshold");
        e.setTextContent(String.valueOf(this.getFullyGuidedTrackingThreshold()));
        node.appendChild(e);

        e = document.createElement("fully-guided-firing-threshold");
        e.setTextContent(String.valueOf(this.getFullyGuidedFiringThreshold()));
        node.appendChild(e);

        e = document.createElement("semi-guided-acquisition-threshold");
        e.setTextContent(String.valueOf(this.getSemiGuidedAcquisitionThreshold()));
        node.appendChild(e);

        e = document.createElement("semi-guided-firing-threshold");
        e.setTextContent(String.valueOf(this.getSemiGuidedFiringThreshold()));
        node.appendChild(e);

        e = document.createElement("semi-guided-tracking-time");
        e.setTextContent(String.valueOf(this.getSemiGuidedTrackingTime()));
        node.appendChild(e);

        e = document.createElement("unguided-firing-threshold");
        e.setTextContent(String.valueOf(this.getUnguidedFiringThreshold()));
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

    /**
     * Sets the threshold at which fully guided shots will be fired as a multiple
     * of the lethal range of the SAM (e.g., threshold = 1 implies shots at max lethal range).
     * @param fully_guided_firing_threshold fully guided firing threshold.
     */
    public void setFullyGuidedFiringThreshold(double fully_guided_firing_threshold)
    {
        this.fully_guided_firing_threshold = fully_guided_firing_threshold;
    }

    /**
     * Returns the threshold at which fully guided shots will be fired.
     * @return fully guided firing threshold.
     */
    public double getFullyGuidedFiringThreshold()
    {
        return this.fully_guided_firing_threshold;
    }

    /**
     * Sets the threshold at which semi-guided shots will be fired as a multiple of the
     * lethal range of the SAM (e.g., threshold = 1 implies shots at max lethal range).
     * @param semi_guided_firing_threshold semi-guided firing threshold.
     */
    public void setSemiGuidedFiringThreshold(double semi_guided_firing_threshold)
    {
        this.semi_guided_firing_threshold = semi_guided_firing_threshold;
    }

    /**
     * Returns the semi-guided firing threshold.
     * @return semi-guided firing threshold.
     */
    public double getSemiGuidedFiringThreshold()
    {
        return this.semi_guided_firing_threshold;
    }

    /**
     * Sets the maximum amount of time that the TTR will be activated for semi-guided
     * shots.  The SAM may be fired ballistically, with the TTR on for the terminal
     * phase of the engagement or, if the target is close enough, the tracking
     * time may be sufficient for the equivalent of a fully guided shot at close range.
     * @param semi_guided_tracking_time semi-guided tracking time in seconds.
     */
    public void setSemiGuidedTrackingTime(double semi_guided_tracking_time)
    {
        this.semi_guided_tracking_time = semi_guided_tracking_time;
    }

    /**
     * Returns the semi-guided tracking time.
     * @return semi-guided tracking time.
     */
    public double getSemiGuidedTrackingTime()
    {
        return this.semi_guided_tracking_time;
    }

    /**
     * Sets the threshold at which unguided shots will be fired as a multiple of
     * the lethal range of the SAM (e.g., threshold = 1 implies shots at max lethal range).
     * Unguided shots have the greatest chance of hitting the target if the threshold
     * is relatively low.
     *
     * @param unguided_firing_threshold unguided firing threshold.
     */
    public void setUnguidedFiringThreshold(double unguided_firing_threshold)
    {
        this.unguided_firing_threshold = unguided_firing_threshold;
    }

    /**
     * Returns the unguided firing threshold.
     * @return unguided firing threshold.
     */
    public double getUnguidedFiringThreshold()
    {
        return this.unguided_firing_threshold;
    }

    /**
     * Sets the threshold at which target acquisition radars will be activated for
     * fully guided engagements, as a multiple of the lethal range of the SAM
     * (e.g., threshold = 1 implies TAR activation at max lethal range).
     * @param fully_guided_acquisition_threshold fully guided acquisition threshold.
     */
    public void setFullyGuidedAcquisitionThreshold(double fully_guided_acquisition_threshold)
    {
        this.fully_guided_acquisition_threshold =
            fully_guided_acquisition_threshold;
    }

    /**
     * Returns the fully guided acquisition threshold.
     * @return fully guided acquisition threshold.
     */
    public double getFullyGuidedAcquisitionThreshold()
    {
        return this.fully_guided_acquisition_threshold;
    }

    /**
     * Sets the threshold at which target acquisition radars will be activated for
     * semi-guided engagements, as a multiple of the lethal range of the SAM
     * (e.g., threshold = 1 implies TAR activation at max lethal range).
     * @param semi_guided_acquisition_threshold semi-guided acquisition threshold.
     */
    public void setSemiGuidedAcquisitionThreshold(double semi_guided_acquisition_threshold)
    {
        this.semi_guided_acquisition_threshold =
            semi_guided_acquisition_threshold;
    }

    /**
     * Returns the semi-guided acquisition threshold.
     * @return semi-guided acquisition threshold.
     */
    public double getSemiGuidedAcquisitionThreshold()
    {
        return this.semi_guided_acquisition_threshold;
    }

    /**
     * Sets the threshold at whick target tracking radars will be activated for
     * fully guided engagements (contingent on TAR track), as a multiple of the
     * lethal range of the SAM (e.g., threshold = 1 implies TTR activation at
     * max lethal range).
     * @param fully_guided_tracking_threshold fully guided tracking threshold.
     */
    public void setFullyGuidedTrackingThreshold(double fully_guided_tracking_threshold)
    {
        this.fully_guided_tracking_threshold = fully_guided_tracking_threshold;
    }

    /**
     * Returns the fully guided tracking threshold.
     * @return fully-guided tracking threshold.
     */
    public double getFullyGuidedTrackingThreshold()
    {
        return this.fully_guided_tracking_threshold;
    }

    /**
     * Sets the fully guided engagement probability for the site.  This is the
     * probability that any engagement will occur as fully guided.  The sum of
     * the fully guided and semi-guided engagement probabilities must be less than
     * one, where 1 - sum is the probability of unguided engagement.
     * @param fully_guided_engagement_probability fully guided engagement probability.
     */
    public void setFullyGuidedEngagementProbability(double fully_guided_engagement_probability)
    {
        this.fully_guided_engagement_probability =
            fully_guided_engagement_probability;
    }

    /**
     * Returns the fully guided engagement probability.
     * @return fully guided engagement probability.
     */
    public double getFullyGuidedEngagementProbability()
    {
        return this.fully_guided_engagement_probability;
    }

    /**
     * Sets the semi-guided engagement probability for the site.  This is the
     * probability that any engagement will occur as semi-guided.  The sum of
     * the fully guided and semi-guided engagement probabilities must be less than
     * one, where 1 - sum is the probability of unguided engagement.
     * @param semi_guided_engagement_probability semi-guided engagement probability.
     */
    public void setSemiGuidedEngagementProbability(double semi_guided_engagement_probability)
    {
        this.semi_guided_engagement_probability =
            semi_guided_engagement_probability;
    }

    /**
     * Returns the semi-guided engagement probability.
     * @return semi-guided engagement probability.
     */
    public double getSemiGuidedEngagementProbability()
    {
        return this.semi_guided_engagement_probability;
    }

    public void changeState(String new_state)
    {
        for (IAgentState s : this.getStates())
        {
            if (s.getName().equals(new_state))
            {
                this.requestNextState(s);
                break;
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
//            logger.debug(this.getName() + " set state to DEPLOYED " +
//                " at time " + getUniverse().getCurrentTime());
            this.setAssignable(true);
            //  For SAM sites, need more than just being deployed to cause radars
            //  to emit.  Therefore, this should be more conditional...based on targets assigned, etc.
            //  If I am in an "autonomous" mode, then I will periodically emit
            //  once deployed.  Otherwise, I need cues.

            //  For now, we're assuming we need IADS cues
            if (!(this.getSuperior() instanceof TargetAssignmentC2))
            {
                this.activateRadars(true);
            }
        }
        else
        {
            if (new_state != firing_state)
            {
//                if (old_state.getName().equals("Deployed"))
//                {
//                    logger.debug(this.getName() + " no longer DEPLOYED at time " +
//                        getUniverse().getCurrentTime());
//                }

                this.activateRadars(false);

                this.setAssignable(false);


                if (this.engaged_target != null)
                {
                    if (getSuperior() != null)
                    {
                        ((TargetAssignmentC2) getSuperior()).abortingTarget(this,
                            engaged_target.getTarget());
                    }
                    this.setEngagedTarget(null);
                }
            }
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
//            logger.debug(getName()+" setting new location");
            //  Set the location
            if (destination_location != null)
            {
                setLocation(destination_location);
                this.activateRadars(false);
            }
        }

        //  Firing state
        if (new_state.getName().equals("Firing"))
        {
            logger.debug(this.getName() + " set state to FIRING " +
                " at time " + getUniverse().getCurrentTime());
            //  We must fire because we were instructed to.  Now, what is the condition and target?
            //  Disable the change state behavior
            this.change_state_behavior.setEnabled(false);

            engaged_target.getShooter().engageTarget(engaged_target.getTarget(),
                engaged_target.getSAMGuidanceMode());
        }

        if (old_state.getName().equals("Firing"))
        {
            logger.debug(this.getName() + " No longer FIRING " +
                " at time " + getUniverse().getCurrentTime());
            //  re-enable change state behavior
            this.change_state_behavior.setEnabled(true);
            this.activateRadars(false);
        }
    }

    /**
     * Supporting method to handle shooting of targets.  This selects a
     * shooter (SAMTEL) and requests a transition to the firing state.
     */
    protected void shootTarget()
    {
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof SAMTEL)
            {
                if (((SAMTEL) p).getEngageability(engaged_target.getTarget()) <=
                    1.)
                {
                    engaged_target.setShooter((SAMTEL) p);
                    requestNextState(this.firing_state);
                    break;
                }
            }
        }
    }

    /**
     * Subordinate radars report their tracks here.  Upon reporting, the SAM site
     * immediately looks for conditions that result in activating other radars or firing a
     * weapon, depending on the engagement mode.
     * @param radar radar reporting the tracks.
     * @param tracks tracks being reported.
     */
    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
        //  Super-class relays to superior with delay -- need to ensure that the
        //  reporting radar is a subordinate before calling super.
        if (this.subordinate_radars.contains(radar))
        {
            super.reportActiveTracks(radar, tracks);
        }
        else if (radar != null && radar.getFunction() == Radar.Function.TA)
        {
            if (this.engaged_target != null)
            {
                this.engaged_target.setTAR(radar);
            }
        }

        if (this.engaged_target != null && radar != null)
        {
            //  This is for reporting of active tracks in support of fully guided shots.
            //  This supports a traditional kill chain of TA --> TT --> Fire
            if (this.engaged_target.getEngagementMode() ==
                EngagementMode.FULLYGUIDED)
            {
                //  Do we have an active TA track of the target?  If so, and it is
                //  within range (and we are doing a fully guided shot), then activate
                //  the TT.
                if (this.engaged_target.getTAR() == radar &&
                    this.engaged_target.getTTR() == null &&
                    radar.getFunction() == Radar.Function.TA &&
                    tracks.contains(this.engaged_target.getTarget()) &&
                    this.getEngageability(this.engaged_target.getTarget()) <
                    this.getFullyGuidedTrackingThreshold())
                {
                    //  Target engagement is active, we have an active TA track, we are going
                    //  for a fully guided engagement, and the target is within TT threshold.
                    this.activateRadars(true, Radar.Function.TT);
                    logger.debug(this.getName() + " ACTIVATING the TT at time " + getUniverse().
                        getCurrentTime());
                } //  If active TT track, then check for firing conditions
                else if (this.engaged_target.getTTR() == radar &&
                    radar.getFunction() == Radar.Function.TT &&
                    tracks.contains(this.engaged_target.getTarget()) &&
                    this.getTrackData(this.engaged_target.getTarget()) !=
                    null &&
                    this.engaged_target.getShooter() == null &&
                    this.getEngageability(this.engaged_target.getTarget()) <
                    this.getFullyGuidedFiringThreshold())
                {
                    this.engaged_target.setSAMGuidanceMode(SAM.GuidanceMode.SEMIACTIVE);
                    this.shootTarget();
                    logger.debug(this.getName() +
                        " SHOOTING the target FULLYGUIDED at time " + getUniverse().
                        getCurrentTime());
                }

            }

            //  This is for reporting of active tracks in support of semi-guided shots.
            //  This supports an unconventional kill chain of TA --> Fire --> TT
            if (this.engaged_target.getEngagementMode() ==
                EngagementMode.SEMIGUIDED)
            {
                //  Here, a TA track could result in an immediate shot in unguided mode,
                //  followed by a TT activation delayed by TBD seconds.
                if (this.engaged_target.getTAR() == radar &&
                    radar.getFunction() == Radar.Function.TA &&
                    tracks.contains(this.engaged_target.getTarget()) &&
                    this.getEngageability(this.engaged_target.getTarget()) <
                    this.getSemiGuidedFiringThreshold())
                {
                    this.engaged_target.setSAMGuidanceMode(SAM.GuidanceMode.BALLISTIC);
                    this.shootTarget();
                    logger.debug(this.getName() +
                        " SHOOTING the target SEMIGUIDED at time " + getUniverse().
                        getCurrentTime());

                    //  Schedule TT activation.
                    //  Estimate time to impact and schedule TTR for the future.
                    double d = CMWorld.getEarthModel().trueDistance(getLocation(), engaged_target.getTarget().
                        getLocation());
                    double v = 0.;
                    for (Platform p : this.engaged_target.getShooter().
                        getSubordinates())
                    {
                        if (p instanceof SAM)
                        {
                            v = ((SAM) p).getSpeed();
                            break;
                        }
                    }
                    double flyout = 0.;
                    if (v > 0.)
                    {
                        flyout = d / v * 3600.;
                    }

                    activate_radars_behavior.setEnabled(true);
                    activate_radars_behavior.setActivationTime(getUniverse().
                        getCurrentTime() + Math.max(0.1, flyout -
                        this.getSemiGuidedTrackingTime()));
                    activate_radars_behavior.setRadarFunction(Radar.Function.TT);
                }
            }
        }
    }

    /**
     * Called by subordinate radars to report dropped tracks.  Depending on the
     * engagement mode, loss of track of an engaged target may result in
     * immediate transition to the teardown state.
     * @param radar radar reporting the dropped tracks.
     * @param dropped_tracks dropped tracks being reported.
     */
    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
        if (this.subordinate_radars.contains(radar))
        {
            super.reportDroppedTracks(radar, dropped_tracks);
        }

        if (this.engaged_target != null &&
            this.engaged_target.getEngagementMode() ==
            EngagementMode.FULLYGUIDED && radar != null)
        {
            if (this.engaged_target.getTTR() == radar &&
                radar.getFunction() == Radar.Function.TT &&
                dropped_tracks.contains(this.engaged_target.getTarget()) &&
                this.getState() == deployed_state)
            {
                this.activateRadars(false);
                logger.debug(this.getName() +
                    " DEACTIVATING all radars and requesting TEARDOWN at time " + getUniverse().
                    getCurrentTime());
                tearDown();
            }
        }
    }

    public void tearDown()
    {
        if (getSuperior() instanceof SAMBattalion)
        {
            ((SAMBattalion) getSuperior()).tearDown();
        }
        else
        {
            requestNextState(teardown_state);
        }
    }

    /**
     * If no target is currently being engaged, then look for conditions to
     * begin engagement of a target.  If the target is being engaged, then look
     * for conditions that result in activation or deactivation of subordinate radars.
     * This is called periodically to cause the SAM site to consider its tracks
     * independently of other events (e.g., reportActiveTracks).
     *
     * @param time the current time.
     */
    @Override
    public void processTracks(double time)
    {
        super.processTracks(time);

        //  First, look for targets within engagement threshold
        if (this.engaged_target == null)
        {
            for (Platform p : this.getAssignedTargets())
            {
                if (this.getEngageability(p) <= this.engagement_threshold)
                {
                    //  Now decide the engagement mode to this target --
                    //  Fully guided, semi-guided, or unguided
                    double rn = getRNG().nextDouble();
                    if (rn < this.fully_guided_engagement_probability)
                    {
                        this.setEngagedTarget(new EngagedTarget(p,
                            EngagementMode.FULLYGUIDED));
                    }
                    else
                    {
                        if (rn < this.fully_guided_engagement_probability +
                            this.semi_guided_engagement_probability)
                        {
                            this.setEngagedTarget(new EngagedTarget(p,
                                EngagementMode.SEMIGUIDED));
                        }
                        else
                        {
                            this.setEngagedTarget(new EngagedTarget(p,
                                EngagementMode.UNGUIDED));
                        }
                    }
                }
            }
        }

        //  Now, look for transitions and activations
        if (this.engaged_target != null)
        {
            EngagedTarget t = this.engaged_target;

            double engageability = this.getEngageability(t.getTarget());

            //  See if it is time to activate TA's
            if (t.getTAR() == null && (this.getState() == deployed_state ||
                this.getState() == firing_state) &&
                ((engageability < this.getFullyGuidedAcquisitionThreshold() &&
                t.getEngagementMode() == EngagementMode.FULLYGUIDED) ||
                (engageability < this.getSemiGuidedAcquisitionThreshold() &&
                t.getEngagementMode() == EngagementMode.SEMIGUIDED)))
            {
                //  Light 'em up now
                this.activateRadars(true, Radar.Function.TA);
                logger.debug(this.getName() + " ACTIVATING the TA at time " + getUniverse().
                    getCurrentTime());
            }

            //  See if it is time to transition into firing state (Unguided mode)
            if (t.getEngagementMode() == EngagementMode.UNGUIDED &&
                engageability < this.getUnguidedFiringThreshold())
            {
                this.engaged_target.setSAMGuidanceMode(SAM.GuidanceMode.BALLISTIC);
                this.shootTarget();
                logger.debug(this.getName() +
                    " SHOOTING the target ballistically at time " + getUniverse().
                    getCurrentTime());
            }

            //  Look for HOJ firing
            if (t.getEngagementMode() == EngagementMode.FULLYGUIDED &&
                t.getTTR() != null && t.getShooter() == null &&
                t.getTTR().isJammed() && this.getEngageability(t.getTTR().
                getJammingSource().getParent()) < 1.)
            {
                Radar tar = t.getTAR();
                Radar ttr = t.getTTR();

                //  Tell superior that I am aborting the previous target.
                if (getSuperior() instanceof TargetAssignmentC2)
                {
                    ((TargetAssignmentC2) getSuperior()).abortingTarget(this,
                        t.getTarget());
                    this.setEngagedTarget(null);
                }

                //  Change target to the jamming source and fire
                this.setEngagedTarget(new EngagedTarget(t.getTTR().
                    getJammingSource().getParent(),
                    EngagementMode.FULLYGUIDED));
                this.engaged_target.setTAR(tar);
                this.engaged_target.setTTR(ttr);
                this.engaged_target.setSAMGuidanceMode(SAM.GuidanceMode.HOJ);
                this.shootTarget();

                logger.debug(this.getName() +
                    " HOJ -- DEACTIVATING radars and SHOOTING at time " + getUniverse().
                    getCurrentTime());
            }

            //  Last, look to see if the engaged_target is now out of range and should be dropped.
            if (this.engaged_target.getTAR() != null &&
                ((engageability > this.getFullyGuidedAcquisitionThreshold() &&
                t.getEngagementMode() == EngagementMode.FULLYGUIDED) ||
                (engageability > this.getSemiGuidedAcquisitionThreshold() &&
                t.getEngagementMode() == EngagementMode.SEMIGUIDED)) &&
                this.getState() == deployed_state)
            {
                this.activateRadars(false, Radar.Function.TA);
                logger.debug(this.getName() + " DEACTIVATING the TA at time " + getUniverse().
                    getCurrentTime());
            }
            else
            {
                if (this.engaged_target.getTTR() != null &&
                    engageability > this.getFullyGuidedTrackingThreshold() &&
                    t.getEngagementMode() == EngagementMode.FULLYGUIDED &&
                    this.getState() == deployed_state)
                {
                    this.activateRadars(false, Radar.Function.TT);
                    logger.debug(this.getName() +
                        " DEACTIVATING the TT at time " + getUniverse().
                        getCurrentTime());
                }
                else
                {
                    if (engageability > this.getEngagementThreshold() &&
                        this.getState() == deployed_state)
                    {
                        boolean emitted = this.engaged_target.getEmitted();

                        this.setEngagedTarget(null);
                        this.activateRadars(false);

                        logger.debug(this.getName() +
                            " DEACTIVATING all radars at time " + getUniverse().
                            getCurrentTime());

                        //  If any radars were on, then prepare to move.
                        if (emitted)
                        {
                            tearDown();
                            logger.debug(this.getName() +
                                " requesting TEARDOWN at time " + getUniverse().
                                getCurrentTime());
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by a superior to unassign a target from the site.  If the site was
     * engaging the target, then it will tear down.
     * @param target target being unassigned.
     */
    @Override
    public void unassignTarget(Platform target)
    {
        super.unassignTarget(target);
        this.setEngagedTarget(null);

        //  Tell radars to shut down.
        this.activateRadars(false);
        logger.debug(this.getName() +
            " DEACTIVATING all radars in response to UNASSIGN at time " + getUniverse().
            getCurrentTime());

        if (this.engaged_target != null &&
            this.engaged_target.getTarget() == target &&
            this.engaged_target.getEmitted() &&
            (this.getState() == deployed_state ||
            this.getState() == firing_state))
        {
            //  We'd best teardown
            tearDown();
            logger.debug(this.getName() + " requesting TEARDOWN at time " + getUniverse().
                getCurrentTime());
        }
    }

    /**
     * Called by the superior to assign a higher priority target.  If the shooter has no
     * capacity, it will displace a current target to accept this one.
     * @param target priority target being assigned.
     */
    @Override
    public void priorityAssignTarget(Platform target)
    {
        super.priorityAssignTarget(target);
        this.setEngagedTarget(null);
    }

    /**
     * Called by subordinate shooter to report outcome of an engagement.  Regardless
     * of outcome, the site will abort the target and being tearing down.
     * @param target target of the engagement.
     * @param destroyed true if target was destroyed, false otherwise.
     */
    @Override
    public void targetDestroyed(Platform target, boolean destroyed)
    {
        //  This should cause a state transition from firing to teardown.
        //  Should also inform superior, no?
        super.targetDestroyed(target, destroyed);

        if (destroyed)
        {
            target.setStatus(Platform.Status.DEAD);
            logger.debug(this.getName() + " target DESTROYED at time " + getUniverse().
                getCurrentTime());
        }
        else
        {
            logger.debug(this.getName() +
                " engagement complete and target NOT DESTROYED at time " + getUniverse().
                getCurrentTime());
        }


        logger.debug(this.getName() + " requesting TEARDOWN at time " + getUniverse().
            getCurrentTime());

        //  Tell superior that I am aborting the target.
        if (getSuperior() instanceof TargetAssignmentC2)
        {
            ((TargetAssignmentC2) getSuperior()).abortingTarget(this, target);
            this.setEngagedTarget(null);
        }
        tearDown();
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
                    r.setActive(activate);

                    if (engaged_target != null)
                    {
                        if (r.getFunction() == Radar.Function.TA)
                        {
                            if (activate)
                            {
                                engaged_target.setTAR(r);
                            }
                            else
                            {
                                engaged_target.setTAR(null);
                            }
                        }
                        else
                        {
                            if (r.getFunction() == Radar.Function.TT)
                            {
                                if (activate)
                                {
                                    engaged_target.setTTR(r);
                                }
                                else
                                {
                                    engaged_target.setTTR(null);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the activation state of subordinate radars of the specified function.
     * @param activate true if radars are to be activated, false otherwise.
     * @param function radar function to be activated/deactivated.
     */
    protected void activateRadars(boolean activate, Radar.Function function)
    {
        for (Platform p : getSubordinates())
        {
            if (p instanceof RadarPlatform)
            {
                RadarPlatform rp = (RadarPlatform) p;

                for (Radar r : rp.getRadars())
                {
                    if (r.getFunction() == function)
                    {
                        r.setActive(activate);

                        if (engaged_target != null)
                        {
                            if (activate)
                            {
                                if (function == Radar.Function.TA)
                                {
                                    engaged_target.setTAR(r);
                                }
                                else
                                {
                                    if (function == Radar.Function.TT)
                                    {
                                        engaged_target.setTTR(r);
                                    }
                                }
                            }
                            else
                            {
                                if (function == Radar.Function.TA)
                                {
                                    engaged_target.setTAR(null);
                                }
                                else
                                {
                                    if (function == Radar.Function.TT)
                                    {
                                        engaged_target.setTTR(null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * ActivateRadarsBehavior is used solely for one time activation of subordinate
     * radars at some point in the future.  This is so that radar illumination can
     * be delayed after some condition is met.  For example, this behavior might
     * be used to cause a TT radar to emit some time AFTER firing a SAM.
     */
    protected class ActivateRadarsBehavior extends Behavior
    {
        private Radar.Function function = Radar.Function.TA;

        private double activation_time;

        /**
         * Sets the activation time of the radars.
         * @param time activation time.
         */
        public void setActivationTime(double time)
        {
            this.activation_time = time;
        }

        /**
         * Sets the radar function to be activated.
         * @param function radar function to be activated.
         */
        public void setRadarFunction(Radar.Function function)
        {
            this.function = function;
        }

        /**
         * Called by the simulation framework to get the time at which to
         * perform the behavior.
         * @param current_time current simulation time.
         * @return activation time.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            return this.activation_time;
        }

        /**
         * Called by the simulation framework to perform the behavior.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            if (getState() == deployed_state ||
                getState() == firing_state)
            {
                activateRadars(true, function);
            }
        }
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
                    xmax = space.getXmax();
                    ymin = space.getYmin();
                    ymax = space.getYmax();
                }
                else
                {
                    if (CMWorld.getEarthModel().getCoordinateSystem().
                        equalsIgnoreCase("LLA"))
                    {
                        xmin = space.getYmin();
                        xmax = space.getYmax();
                        ymin = space.getXmin();
                        ymax = space.getXmax();
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
                        destination_location = new Double2D(new_loc.getX(),
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
            else if (getSuperior() instanceof SAMBattalion)
            {
                return current_time;    //  Disables this behavior.
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
                    else
                    {
                        if (prev_state != null &&
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
                        else
                        {
                            //  e.g., there is no prev_state, so do a random draw of the
                            //  next one based on which states exist.
                            if (getRNG().nextDouble() < 0.5)
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
                        }
                    }

                    //  Finally, generate the destination point for the move.
                    double max_distance =
                        (next_time - current_time) * max_speed / 3600.;

                    //  Now generate a point < max_distance away from here, but compatible
                    //  with the centroid.
                    setDestinationLocation(max_distance);
                }
                else
                {
                    if (getState().getName().equals("Hiding") && hide_cycle >
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
                    else
                    {
                        if (getState().getName().equals("Setup") && setup_cycle >
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
                        else
                        {
                            if (getState().getName().equals("Deployed") &&
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
                            else
                            {
                                if (getState().getName().equals("Teardown") &&
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
                        }
                    }
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
            if (getState() == cur_state &&
                !(getState() == deployed_state && getEngagedTarget() != null) &&
                !(getSuperior() instanceof SAMBattalion))
            {
                prev_state = cur_state;
                logger.debug(getName() + " requesting state change to " +
                    next_state.getName() + " at time " + current_time);
                requestNextState(next_state);
            }
        }
    }

    /**
     * Class to keep track of which target is being engaged and how it is
     * being engaged.
     */
    protected class EngagedTarget
    {
        private Platform target;

        private EngagementMode engagement_mode;

        private Radar tar;

        private Radar ttr;

        private boolean emitted;

        private SAMTEL shooter;

        private SAM.GuidanceMode guidance_mode;

        /**
         * Creates an instance of EngagedTarget.
         * @param target target being engaged.
         * @param engagement_mode engagement mode.
         */
        public EngagedTarget(Platform target, EngagementMode engagement_mode)
        {
            this.target = target;
            this.engagement_mode = engagement_mode;
            this.tar = null;
            this.ttr = null;
            this.shooter = null;
            this.emitted = false;
            this.guidance_mode = SAM.GuidanceMode.SEMIACTIVE;
        }

        /**
         * Returns the target.
         * @return target.
         */
        public Platform getTarget()
        {
            return this.target;
        }

        /**
         * Returns the engagement mode.
         * @return engagement mode.
         */
        public EngagementMode getEngagementMode()
        {
            return this.engagement_mode;
        }

        /**
         * Sets the shooter for this engagement.
         * @param shooter SAMTEL object.
         */
        public void setShooter(SAMTEL shooter)
        {
            this.shooter = shooter;
        }

        /**
         * Returns the shooter for this engagement.
         * @return SAMTEL object.
         */
        public SAMTEL getShooter()
        {
            return this.shooter;
        }

        /**
         * Sets the initial SAM guidance mode at firing.
         * @param guidance_mode SAM guidance mode.
         */
        public void setSAMGuidanceMode(SAM.GuidanceMode guidance_mode)
        {
            this.guidance_mode = guidance_mode;
        }

        /**
         * Returns the initial SAM guidance mode at firing.
         * @return SAM guidance mode.
         */
        public SAM.GuidanceMode getSAMGuidanceMode()
        {
            return this.guidance_mode;
        }

        /**
         * Sets the target acquisition radar for this engagement.
         * @param tar target acquisition radar.
         */
        public void setTAR(Radar tar)
        {
            this.tar = tar;
            if (tar != null)
            {
                this.emitted = true;
            }
        }

        /**
         * Returns the target acquisition radar for this engagement.
         * @return target acquisition radar.
         */
        public Radar getTAR()
        {
            return tar;
        }

        /**
         * Sets the target tracking radar for this engagement.
         * @param ttr target tracking radar.
         */
        public void setTTR(Radar ttr)
        {
            this.ttr = ttr;
            if (ttr != null)
            {
                this.emitted = true;
            }
        }

        /**
         * Returns the target tracking radar for this engagement.
         * @return target tracking radar.
         */
        public Radar getTTR()
        {
            return this.ttr;
        }

        /**
         * Returns whether any radars emitted during the course of this engagement.
         * This may influence whether a state transition is necessary.
         * @return true if emissions occurred, false otherwise.
         */
        public boolean getEmitted()
        {
            return this.emitted;
        }
    }

    /**
     * Returns the engagement threshold.
     * @return engagement threshold.
     */
    public double getEngagementThreshold()
    {
        return engagement_threshold;
    }

    /**
     * Sets the threshold at which an assigned target will be considered for
     * engagement as a multiple of the max lethal range of the site (e.g.,
     * threshold = 1 means that engagements will begin at max lethal range).
     * @param engagement_threshold engagement threshold.
     */
    public void setEngagementThreshold(double engagement_threshold)
    {
        this.engagement_threshold = engagement_threshold;
    }
}
