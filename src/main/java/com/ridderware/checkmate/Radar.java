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

import com.ridderware.checkmate.CMAgent.ColorzEnum;
import com.ridderware.fuse.AgentState;
import java.awt.Color;
import java.awt.Shape;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import com.ridderware.fuse.Behavior;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.IAgentState;
import com.ridderware.fuse.gui.Paintable;
import com.ridderware.fuse.gui.Painter;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Radar objects are systems contained by platforms.  The platforms determine
 * when to turn a radar on or off (change its emission state).  When a radar is
 * on, it periodically scans the environment and checks all possible
 * targets for detection.  A positive detection registers as a blip.
 * Meeting a blip-scan criteria results in a track.
 *
 * @author Jeff Ridder
 */
public class Radar extends CMSystem implements Paintable, ISIMDIS
{
    /**
     * Enumeration of radar functions.
     */
    public enum Function
    {
        /** Early warning */
        EW,
        /** Target acquisition */
        TA,
        /** Target tracking */
        TT
    }

    private Color color;

    private Platform parent;

    private double min_emitting_time;

    private double mean_emitting_time;

    private double min_silent_time;

    private double mean_silent_time;

    private Function function;

    /*
     * This is the 1-sqm target reference detection range.
     */
    private double reference_range;

    private double jammed_range;

    /** The jammer currently jamming this radar */
    protected Jammer jamming_source;

    private int classification;

    private double scan_period;

    //  Flags that this radar is able to emit
    private boolean active;

    //  Transmitter power in Watts.
    private double power;

    //  Sidelobe gain in dBi.
    private double sidelobe_gain;

    //  Frequency in MHz.
    private double frequency;

    private String beam_color;

    private HashSet<Platform> blips = new HashSet<>();

    private HashSet<Platform> prev_blips = new HashSet<>();

    private HashSet<Platform> tracks = new HashSet<>();

    private ScanBehavior scan_behavior;

    private boolean disable_scan;

    private ChangeStateBehavior change_state_behavior =
        new ChangeStateBehavior();

    private StartEmittingBehavior start_emitting = new StartEmittingBehavior();

    private StopEmittingBehavior stop_emitting = new StopEmittingBehavior();

    private EmittingState emitting_state;

    private SilentState silent_state;

    //  just a convenience.
    private boolean emitting;

    private static final Logger logger = LogManager.getLogger(Radar.class);

    /**
     * Creates a new instance of Radar
     * @param name name of the radar.
     * @param world CMWorld in which the radar plays.
     */
    public Radar(String name, CMWorld world)
    {
        super(name, world);

        this.createRadar();
    }

    /**
     * Creates a new instance of Radar with attributes for graphical display.
     * @param name name of the radar.
     * @param world CMWorld in which the radar plays.
     * @param color color to draw the detection ring.
     */
    public Radar(String name, CMWorld world, Color color)
    {
        super(name, world);
        this.color = color;

        this.createRadar();
    }

    /**
     * Sets the initial attributes of the radar upon creation.
     */
    protected final void createRadar()
    {
        this.scan_period = 1.;
        this.reference_range = 0.;
        this.classification = 0;

        this.beam_color = null;

        this.scan_behavior = new ScanBehavior();
        this.addBehavior(this.scan_behavior);

        this.emitting_state = new EmittingState("Emitting");
        this.silent_state = new SilentState("Silent");

        this.addState(emitting_state);
        this.addState(silent_state);
        this.addBehavior(change_state_behavior);

        this.addBehavior(stop_emitting);
        this.addBehavior(start_emitting);

        this.jamming_source = null;

        //  Default
        this.function = Function.EW;
    }

    /**
     * Sets the radar's function.  This determines the role the radar will have
     * in an integrated air defense system, how it is activated, and how it
     * supports engagement of targets.
     * @param function function.
     */
    public void setFunction(Function function)
    {
        this.function = function;
    }

    /**
     * Returns the radar's function.
     * @return function.
     */
    public Function getFunction()
    {
        return function;
    }

    /**
     * See base class documentation.
     * @return max buffer size.
     */
    @Override
    public int getMaxBufferSize()
    {
        return 35;
    }

    /**
     * Returns the color of the detection range.
     * @return color.
     */
    public Color getColor()
    {
        return this.color;
    }

    /**
     * Returns the string name of the beam color.
     * @return beam color name.
     */
    public String getBeamColor()
    {
        return this.beam_color;
    }

    /**
     * No idea...see base class.
     * @return ditto.
     */
    @Override
    public Paintable.PaintType getPaintType()
    {
        return Paintable.PaintType.Simple;
    }

    /**
     * Returns the current set of tracks for the radar.
     *
     * @return current tracks.
     */
    public Set<Platform> getTracks()
    {
        return this.tracks;
    }

    /**
     * Returns the set of blips from the most recent scan of the radar.
     *
     * @return current blips.
     */
    public Set<Platform> getBlips()
    {
        return this.blips;
    }

    /**
     * Returns the set of blips from the previous scan of the radar.
     *
     * @return previous blips.
     */
    public Set<Platform> getPreviousBlips()
    {
        return this.prev_blips;
    }

    /**
     * Called by Swarm framework to paint the agent.
     * @param args see base class docs.
     * @return a collection of shapes to be drawn.
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        HashSet<Shape> boundingShapes = new HashSet<Shape>(1);
        if (this.isEmitting())
        {
            //  Ask the earth model to project a point due east and another due north out to the jammed range.
            //  2 X difference of this point with origin is the ellipse size.
            Double3D eastpt =
                CMWorld.getEarthModel().projectLocation(parent.getLocation(),
                getJammedRange(), Math.PI / 2.);
            Double3D northpt =
                CMWorld.getEarthModel().projectLocation(parent.getLocation(),
                getJammedRange(), 0.);

            double dx = eastpt.getX() - parent.getLocation().getX();
            double dy = eastpt.getY() - parent.getLocation().getY();
            double width = 2. * Math.sqrt(dx * dx + dy * dy);

            dx = northpt.getX() - parent.getLocation().getX();
            dy = northpt.getY() - parent.getLocation().getY();
            double height = 2. * Math.sqrt(dx * dx + dy * dy);

            Double3D loc = null;
            if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("ENU"))
            {
                loc = parent.getLocation();
            }
            else if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("LLA"))
            {
                loc = new Double3D(parent.getLocation().getY(), parent.getLocation().
                    getX(), parent.getLocation().getZ());
            }
            if (loc != null)
            {
                if (this.function != Function.TT)
                {
                    boundingShapes.add(Painter.getPainter().
                        paintEllipse2D_Double(width,
                        height, loc, false, color, true));
                }
                else
                {
                    boundingShapes.add(Painter.getPainter().
                        paintEllipse2D_Double(width,
                        height, loc, true, color, true, 0.5F));
                }
            }
        }
        return boundingShapes;
    }

    /**
     * Moves the antenna to its location at the specified time.  Since antennas are omni
     * for this radar class, this method does nothing.
     * @param time time to update the antenna location to.
     */
    public void moveAntenna(double time)
    {

    }

    /**
     * Writes the initialization strings to the SIMDIS .asi file.
     * @param asi_file the SIMDIS .asi file.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
        if (asi_file != null && getParent().getSIMDISIcon() != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("BeamID\t" + String.valueOf(getParent().getId()) +
                    "\t" + String.valueOf(this.getId()));

                if (this.getFunction() == Function.TT)
                {
                    pw.println("VertBW\t" + String.valueOf(this.getId()) +
                        "\t1.");
                    pw.println("HorzBW\t" + String.valueOf(this.getId()) +
                        "\t1.");
                    pw.println("BodyOffset\t" + String.valueOf(this.getId()) +
                        "\t0.\t0.\t3.");
                }
                else
                {
                    pw.println("VertBW\t" + String.valueOf(this.getId()) +
                        "\t180.");
                    pw.println("HorzBW\t" + String.valueOf(this.getId()) +
                        "\t360.");
                    pw.println("ElevOffset\t" + String.valueOf(this.getId()) +
                        "\t90.");
                    pw.println("AntennaAlgorithm\t" +
                        String.valueOf(this.getId()) + "\t\"omni\"");
                    pw.println("AntennaGain\t" + String.valueOf(this.getId()) +
                        "\t30.");
                    pw.println("AntennaPeakPower\t" +
                        String.valueOf(this.getId()) + "\t200000.");
                    pw.println("AntennaFrequency\t" +
                        String.valueOf(this.getId()) + "\t400.");
                }

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Writes the time data to the SIMDIS .asi file.
     * @param time time at which to write the data.
     * @param asi_file SIMDIS .asi file.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        if (getParent().getSIMDISIcon() != null && asi_file != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);
                if (this.isEmitting())
                {
                    pw.println("BeamOnOffCmd\t" + this.getId() + "\t" + time +
                        "\t1");

                    if (this.getFunction() == Function.EW)
                    {
                        pw.println("BeamData\t" + this.getId() + "\t" +
                            time + "\t" + this.beam_color + "\t0.\t0.\t" +
                            LengthUnit.NAUTICAL_MILES.convert(this.getJammedRange(),
                            LengthUnit.METERS));
                    }
                    else if (this.getFunction() == Function.TA)
                    {
                        pw.println("BeamData\t" + this.getId() + "\t" +
                            time + "\t" + this.beam_color + "\t0.\t0.\t" +
                            LengthUnit.NAUTICAL_MILES.convert(this.getJammedRange(),
                            LengthUnit.METERS));
                    }
                    else if (this.getFunction() == Function.TT)
                    {
                        pw.println("BeamData\t" + this.getId() + "\t" + time +
                            "\t" + this.beam_color + "\t" + "\t\"target\"");
                        //  How do I ensure the assigned target is being tracked?
                        if (this.getParent().getSuperior() instanceof SAMSite)
                        {
                            SAMSite.EngagedTarget et = ((SAMSite) this.getParent().
                                getSuperior()).getEngagedTarget();

                            if (et != null)
                            {
                                Platform target = et.getTarget();

                                pw.println("BeamTargetIDCmd\t" + this.getId() +
                                    "\t" + time + "\t" + target.getId());
                            }
                        }
                    }
                }
                else
                {
                    pw.println("BeamOnOffCmd\t" + this.getId() + "\t" + time +
                        "\t0");
                }
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Sets the reference, unjammed, one-square-meter detection range of the radar.
     * @param detection_range detection range in nautical miles.
     */
    public void setReferenceRange(double detection_range)
    {
        this.reference_range = detection_range;
    }

    /**
     * Returns the reference detection range.
     * @return reference range.
     */
    public double getReferenceRange()
    {
        return this.reference_range;
    }

    /**
     * Sets the jammed detection range of the radar vs. a one-square-meter target.
     * @param jammed_range jammed range in n.mi.
     */
    protected void setJammedRange(double jammed_range)
    {
        this.jammed_range = jammed_range;
    }

    /**
     * Returns the jammed range of the radar against a one-square-meter target.
     * @return jammed range in n.mi.
     */
    public double getJammedRange()
    {
        return this.jammed_range;
    }

    /**
     * Sets the parent platform holding this radar.
     * @param parent a Platform object.
     */
    public void setParent(Platform parent)
    {
        this.parent = parent;
    }

    /**
     * Returns the parent platform.
     * @return a Platform object.
     */
    public Platform getParent()
    {
        return this.parent;
    }

    /**
     * Returns the classification of the radar.
     * @return classification.
     */
    public int getClassification()
    {
        return classification;
    }

    /**
     * Sets the classification of the radar.
     * @param classification integer indicating classification.
     */
    public void setClassification(int classification)
    {
        this.classification = classification;
    }

    /**
     * Returns the scan period of the radar.
     * @return scan period.
     */
    public double getScanPeriod()
    {
        return scan_period;
    }

    /**
     * Sets the scan period of the radar.
     * @param scan_period scan period in seconds.
     */
    public void setScanPeriod(double scan_period)
    {
        this.scan_period = scan_period;
    }

    /**
     * Indicates whether the radar is currently in the emitting state.
     * @return true if emitting, false otherwise.
     */
    public boolean isEmitting()
    {
        return this.emitting;
    }

    /**
     * Returns whether or not the radar is jammed.
     * @return true if jammed, false otherwise.
     */
    public boolean isJammed()
    {
        boolean bjammed = false;
        if (this.getJammedRange() != this.getReferenceRange())
        {
            bjammed = true;
        }

        return bjammed;
    }

    /**
     * Returns the jammer jamming this radar.  If more than one jammer, then
     * the strongest source is returned.
     * @return Jammer object.
     */
    public Jammer getJammingSource()
    {
        return this.jamming_source;
    }

    /**
     * Callback from the Swarm framework to notify the agent of a state change.
     * @param old_state previous (leaving) state.
     * @param new_state new (entering) state.
     */
    @Override
    public void stateChanged(IAgentState old_state, IAgentState new_state)
    {
        logger.debug("Radar " + getName() + " changing from state " +
            old_state.getName() + " to state " + new_state.getName());

        if (new_state instanceof EmittingState)
        {
            //  Enable when emitting
            if (!disable_scan)
            {
                this.scan_behavior.setEnabled(true);
            }
            else
            {
                this.scan_behavior.setEnabled(false);
            }
            this.emitting = true;
        }
        else
        {
            this.scan_behavior.setEnabled(false);
            this.emitting = false;
            //  Report all current tracks as dropped tracks.
            if (parent instanceof IRadarTrackReceiver)
            {
                ((IRadarTrackReceiver) parent).reportDroppedTracks(this, tracks);
            }
            tracks.clear();
            prev_blips.clear();

            //  Give SIMDIS a hit.
            if (getWorld().getASIFile() != null)
            {
                this.simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                    getASIFile());
            }
        }

        //  Anytime we switch from emitting to not emitting, tell parents to give SIMDIS a hit.
        getParent().simdisUpdate(getUniverse().getCurrentTime(), getWorld().
            getASIFile());
    }

    /**
     * Called by a containing object to tell the radar whether it is active or not (this does
     * NOT imply emission).
     * @param active active flag.
     */
    public void setActive(boolean active)
    {
        //  Can't request state changes here...so have to set up params so change state behavior can do it
        logger.debug("Radar " + getName() + " going active " + active);
        this.active = active;

        if (!this.active)
        {
            //  Go silent
            this.stop_emitting.setEventTime(getUniverse().getCurrentTime() + .1);
            this.stop_emitting.setEnabled(true);
            this.start_emitting.setEventTime(0.);
            this.start_emitting.setEnabled(true);

            //  Stop periodic emissions state changes
            this.change_state_behavior.setEnabled(false);

            logger.debug("Radar " + getName() +
                " disabled change state behavior and went silent");
        }
        else
        {
            //  enable periodic emissions behavior.
            this.change_state_behavior.setEnabled(true);

            //  Figure out whether I'm currently emitting or not
            double cycle_time = this.mean_emitting_time + this.mean_silent_time;

            if (cycle_time <= 0.)
            {
                this.start_emitting.setEventTime(getUniverse().getCurrentTime() +
                    .1);
                this.start_emitting.setEnabled(true);
                this.stop_emitting.setEventTime(0.);
                this.stop_emitting.setEnabled(true);

                logger.debug("Radar " + getName() +
                    " enabled change state behavior and is emitting continuously");
            }
            else
            {
                if (getRNG().nextDouble() < this.mean_emitting_time / cycle_time)
                {
                    this.start_emitting.setEventTime(getUniverse().
                        getCurrentTime() + .1);
                    this.start_emitting.setEnabled(true);
                    this.stop_emitting.setEventTime(0.);
                    this.stop_emitting.setEnabled(true);
                    logger.debug("Radar " + getName() +
                        " enabled change state behavior and is emitting periodically");
                }
                else
                {
                    this.stop_emitting.setEventTime(getUniverse().getCurrentTime() +
                        .1);
                    this.stop_emitting.setEnabled(true);
                    this.start_emitting.setEventTime(0.);
                    this.start_emitting.setEnabled(true);
                    logger.debug("Radar " + getName() +
                        " enabled change state behavior and is silent periodically");
                }
            }
        }
    }

    /**
     * Sets the initial attributes of the radar before each simulation run.
     */
    public void initialize()
    {
        this.blips.clear();
        this.tracks.clear();
        this.prev_blips.clear();

        this.jammed_range = this.reference_range;
        this.jamming_source = null;

        this.active = false;
        this.start_emitting.setEnabled(false);
        this.stop_emitting.setEnabled(false);
        this.emitting = false;

        //  We can disable the scan behavior for scenarios where no aircraft is
        //  detectable and no jammers are present, so do that check now.
        this.disable_scan = true;
        if (!getWorld().getJammers().isEmpty())
        {
            this.disable_scan = false;
        }

        for (Aircraft a : getWorld().getAircraft())
        {
            if (a.getRCS() > 0.)
            {
                this.disable_scan = false;
                break;
            }
        }
    }

    /**
     * Computes the jammed range of the radar.
     */
    public void findJammedRange()
    {
        //  Get the jammer effectiveness vs this radar
        double j_effectiveness = 0.;

        this.jamming_source = null;

        //  Loop over all jammers, take the largest one
        for (Jammer j : getWorld().getJammers())
        {
            if (j.getParent().getStatus() == Platform.Status.ACTIVE && getWorld().
                getEMLOSUtil().hasLOS(j.getParent(), getParent()))
            {
                double k = j.jammingEffectiveness(this);

                //  Jammers get tracked for free if they are jamming me.
                if (k > 0.)
                {
                    this.getBlips().add(j.getParent());

                    if (this.getPreviousBlips().contains(j.getParent()))
                    {
                        this.getTracks().add(j.getParent());
                    }
                }

                if (k > j_effectiveness)
                {
                    j_effectiveness = k;
                    jamming_source = j;
                }
            }
        }

        this.setJammedRange(this.getReferenceRange() * (1. - j_effectiveness));
    }

    /**
     * Scans the radar to look for tracks.  This is called by ScanBehavior.
     * @param current_time current time.
     */
    public void scanRadar(double current_time)
    {
        HashSet<Platform> dropped_tracks = new HashSet<>(tracks);

        //  Clear anything in the blips.
        blips.clear();

        //  Clear previous tracks.
        tracks.clear();

        this.jamming_source = null;

        findJammedRange();

        Double3D myLoc = parent.getLocation();

        double j_sq = jammed_range * jammed_range;
        //  Now check each aircraft for detection   --  no...check assigned aircraft for detection
        if (getParent().getSuperior() instanceof SAMSite &&
            this.getFunction() == Function.TT)
        {
            SAMSite site = (SAMSite) getParent().getSuperior();

            if (site.getEngagedTarget() != null && site.getEngagedTarget().
                getTarget() instanceof Aircraft &&
                getWorld().getEMLOSUtil().hasLOS(getParent(), site.getEngagedTarget().
                getTarget()))
            {
                Aircraft ac = (Aircraft) site.getEngagedTarget().getTarget();
                ac.movePlatform(current_time);

                double r_sq = j_sq * Math.sqrt(ac.getRCS());
                if (r_sq > 0.)
                {
                    Double3D acLoc = ac.getLocation();
                    double d_sq =
                        CMWorld.getEarthModel().trueDistanceSq(myLoc, acLoc);

                    if (ac.getStatus() == Platform.Status.ACTIVE && d_sq < r_sq)
                    {
                        blips.add(ac);

                        //  This checks for a track condition of 2 consecutive blips
                        if (prev_blips.contains(ac))
                        {
                            tracks.add(ac);
                            dropped_tracks.remove(ac);
                        }
                    }
                }
            }
        }
        else
        {
            for (Aircraft ac : getWorld().getAircraft())
            {
                //  Adjust range-squared for rcs
                double r_sq = j_sq * Math.sqrt(ac.getRCS());

                if (r_sq > 0. && getWorld().getEMLOSUtil().hasLOS(getParent(),
                    ac))
                {
                    Double3D acLoc = ac.getLocation();
                    double d_sq =
                        CMWorld.getEarthModel().trueDistanceSq(myLoc, acLoc);

                    if (ac.getStatus() == Platform.Status.ACTIVE && d_sq < r_sq)
                    {
                        blips.add(ac);

                        //  This checks for a track condition of 2 consecutive blips
                        if (prev_blips.contains(ac))
                        {
                            tracks.add(ac);
                            dropped_tracks.remove(ac);
                        }
                    }
                }
            }
        }

        prev_blips.clear();

        prev_blips.addAll(blips);

        if (parent instanceof IRadarTrackReceiver)
        {
            //  Report the drops first in order to create capacity for new tracks in the active list.
            if (!dropped_tracks.isEmpty())
            {
                ((IRadarTrackReceiver) parent).reportDroppedTracks(this,
                    dropped_tracks);
            }

            if (!tracks.isEmpty())
            {
                ((IRadarTrackReceiver) parent).reportActiveTracks(this, tracks);
            }
        }

        if (this.getWorld().getASIFile() != null)
        {
            this.simdisUpdate(current_time, getWorld().getASIFile());
        }
    }

    /**
     * Behavior to periodically scan the radar.
     */
    protected class ScanBehavior extends Behavior
    {
        /**
         * Called by the Swarm framework to get the next time to perform the behavior.
         * @param current_time current time.
         * @return next time to perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double scan_time = current_time;

            if (isEmitting())
            {
                scan_time += getScanPeriod();
            }

            return scan_time;
        }

        /**
         * Called by the Swarm framework to perform the behavior.  Calls scanRadar.
         * @param current_time current time.
         */
        @Override
        public void perform(double current_time)
        {
            if (isEmitting())
            {
                scanRadar(current_time);
            }
        }
    }

    /**
     * Defines the EmittingState for the radar.  A subclass of Swarm framework State.
     */
    protected class EmittingState extends AgentState
    {
        /**
         * Creates a new instance of EmittingState.
         * @param name name of the state.
         */
        public EmittingState(String name)
        {
            super(name);
        }
    }

    //  Single execution behavior to start emitting
    /**
     * Behavior to start emitting.
     */
    protected class StartEmittingBehavior extends Behavior
    {
        /**
         * Time at which to invoke the behavior.
         */
        protected double event_time = 0.;

        /**
         * Gets the event_time attribute of the StartEmitting object. Called
         * by the Swarm framework.
         * @param current_time current simulation time.
         * @return event time.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            return event_time;
        }

        /**
         * Performs the behavior. Called by the Swarm framework.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            requestNextState(emitting_state);
        }

        /**
         * Sets the event_time attribute of the StartEmitting object
         * @param event_time time at which to invoke the behavior.
         */
        protected void setEventTime(double event_time)
        {
            this.event_time = event_time;

            //	Tells the universe that I'm baaaaaaack!
            this.setEnabled(true);
        }
    }
    //  Single execution behavior to stop emitting
    /**
     * Behavior to stop emitting.
     */
    protected class StopEmittingBehavior extends Behavior
    {
        /**
         * Time at which to invoke the behavior.
         */
        protected double event_time = 0.;

        /**
         * Gets the event_time attribute of the StartEmitting object. Called
         * by the Swarm framework.
         * @param current_time current simulation time.
         * @return event time.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            return event_time;
        }

        /**
         *  Performs the behavior. Called by the Swarm framework.
         *
         * @param  current_time  current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            requestNextState(silent_state);
        }

        /**
         *  Sets the event_time attribute of the StartEmitting object
         *
         * @param  event_time time at which to invoke the behavior.
         */
        protected void setEventTime(double event_time)
        {
            this.event_time = event_time;

            //	Tells the universe that I'm baaaaaaack!
            this.setEnabled(true);
        }
    }

    /**
     * Defines the SilentState for the radar.  A subclass of Swarm framework State.
     */
    protected class SilentState extends AgentState
    {
        /**
         * Creates a new instance of SilentState.
         * @param name name of the state.
         */
        public SilentState(String name)
        {
            super(name);
        }
    }

    /**
     * Behavior to periodically change the state of the radar.
     */
    protected class ChangeStateBehavior extends Behavior
    {
        /**
         * Called by the Swarm framework to determine the next time to perform the behavior.
         * @param current_time current time.
         * @return next time at which to perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double next_time = current_time;

            if (active)
            {
                double emitting_cycle = min_emitting_time + mean_emitting_time;
                double silent_cycle = min_silent_time + mean_silent_time;

                if (getState() instanceof EmittingState)
                {
                    while (next_time <= current_time && emitting_cycle > 0.)
                    {
                        next_time += min_emitting_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_emitting_time -
                            min_emitting_time);
                    }
                }
                else
                {
                    while (next_time <= current_time && silent_cycle > 0.)
                    {
                        next_time += min_silent_time - Math.log(1. - getRNG().
                            nextDouble()) * (mean_silent_time - min_silent_time);
                    }
                }

                logger.debug("Radar " + getName() +
                    " next scheduled state change is " + next_time);
            }

            return next_time;
        }

        /**
         * Called by the Swarm framework to perform the behavior.
         * @param current_time current time.
         */
        @Override
        public void perform(double current_time)
        {
            if (getState() instanceof EmittingState)
            {
                logger.debug(getName() + " changing to silent at " +
                    current_time);
                requestNextState(silent_state);
            }
            else
            {
                logger.debug(getName() + " changing to emitting at " +
                    current_time);
                requestNextState(emitting_state);
            }
        }
    }

    /**
     * Returns the power of the radar.
     * @return power in Watts.
     */
    public double getPower()
    {
        return power;
    }

    /**
     * Sets the power of the radar.
     * @param power power in Watts.
     */
    public void setPower(double power)
    {
        this.power = power;
    }

    /**
     * Returns the sidelobe gain of the radar in dBi.
     * @return sidelobe gain.
     */
    public double getSidelobeGain()
    {
        return sidelobe_gain;
    }

    /**
     * Sets the sidelobe gain of the radar in dBi.
     * @param sidelobe_gain sidelobe gain.
     */
    public void setSidelobeGain(double sidelobe_gain)
    {
        this.sidelobe_gain = sidelobe_gain;
    }

    /**
     * Returns the frequency of the radar.
     * @return frequency in MHz.
     */
    public double getFrequency()
    {
        return frequency;
    }

    /**
     * Sets the frequency of the radar.
     * @param frequency frequency in MHz.
     */
    public void setFrequency(double frequency)
    {
        this.frequency = frequency;
    }

    /**
     * Returns the minimum emitting time of the radar.
     * @return time in seconds.
     */
    public double getMinEmittingTime()
    {
        return min_emitting_time;
    }

    /**
     * Sets the minimum emitting time of the radar.
     * @param min_emitting_time time in seconds.
     */
    public void setMinEmittingTime(double min_emitting_time)
    {
        this.min_emitting_time = min_emitting_time;
    }

    /**
     * Returns the mean emitting time of the radar.
     * @return time in seconds.
     */
    public double getMeanEmittingTime()
    {
        return mean_emitting_time;
    }

    /**
     * Sets the mean emitting time of the radar.
     * @param mean_emitting_time time in seconds.
     */
    public void setMeanEmittingTime(double mean_emitting_time)
    {
        this.mean_emitting_time = mean_emitting_time;
    }

    /**
     * Returns the minimum silent time of the radar.
     * @return time in seconds.
     */
    public double getMinSilentTime()
    {
        return min_silent_time;
    }

    /**
     * Sets the minimum silent time of the radar.
     * @param min_silent_time time in seconds.
     */
    public void setMinSilentTime(double min_silent_time)
    {
        this.min_silent_time = min_silent_time;
    }

    /**
     * Returns the mean silent time of the radar.
     * @return time in seconds.
     */
    public double getMeanSilentTime()
    {
        return mean_silent_time;
    }

    /**
     * Sets the mean silent time of the radar.
     * @param mean_silent_time time in seconds.
     */
    public void setMeanSilentTime(double mean_silent_time)
    {
        this.mean_silent_time = mean_silent_time;
    }

    /**
     * Sets the system's attributes by parsing the XML.
     * @param node root node for the system.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeName().toLowerCase().equals("color"))
            {
                this.beam_color = child.getTextContent().toLowerCase();
                this.color = ColorzEnum.getColor(this.beam_color);
            }
            else if (child.getNodeName().toLowerCase().equals("reference-range"))
            {
                this.reference_range =
                    Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("scan-period"))
            {
                this.scan_period = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("classification"))
            {
                this.classification = Integer.parseInt(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("power"))
            {
                this.power = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("sidelobe-gain"))
            {
                this.sidelobe_gain = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("frequency"))
            {
                this.frequency = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("emit-time"))
            {
                this.setMinEmittingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanEmittingTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().toLowerCase().equals("silent-time"))
            {
                this.setMinSilentTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanSilentTime(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("function"))
            {
                this.function = Function.valueOf(child.getTextContent());
            }
        }
    }

    /**
     * Creates XML nodes containing the system's attributes.
     *
     * @param node root node for the system.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        e = document.createElement("color");
        e.setTextContent(this.beam_color);
        node.appendChild(e);

        e = document.createElement("reference-range");
        e.setTextContent(String.valueOf(this.reference_range));
        node.appendChild(e);

        e = document.createElement("scan-period");
        e.setTextContent(String.valueOf(this.scan_period));
        node.appendChild(e);

        e = document.createElement("classification");
        e.setTextContent(String.valueOf(this.classification));
        node.appendChild(e);

        e = document.createElement("power");
        e.setTextContent(String.valueOf(this.power));
        node.appendChild(e);

        e = document.createElement("sidelobe-gain");
        e.setTextContent(String.valueOf(this.sidelobe_gain));
        node.appendChild(e);

        e = document.createElement("frequency");
        e.setTextContent(String.valueOf(this.frequency));
        node.appendChild(e);

        e = document.createElement("emit-time");
        e.setAttribute("min", String.valueOf(this.min_emitting_time));
        e.setAttribute("mean", String.valueOf(this.mean_emitting_time));
        node.appendChild(e);

        e = document.createElement("silent-time");
        e.setAttribute("min", String.valueOf(this.min_silent_time));
        e.setAttribute("mean", String.valueOf(this.mean_silent_time));
        node.appendChild(e);

        e = document.createElement("function");
        e.setTextContent(this.function.toString());
        node.appendChild(e);
    }
}
