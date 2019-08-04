/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2007 Jeff Ridder
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

import com.ridderware.checkmate.Radar.Function;
import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import com.ridderware.fuse.Behavior;
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.IAgentState;
import com.ridderware.fuse.MutableDouble3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * We'll move the antenna ONLY if we're producing a SIMDIS ASI file.  Otherwise, we'll set the antenna
 * orientation according to the aircraft we're scanning, and let that be it.
 *
 * @author Jeff Ridder
 */
public class DirectionalRadar extends Radar
{
    private Antenna antenna;

    private MoveAntennaBehavior move;

    //  Height of antenna above ground in feet.
    private double antennaHeight;

    private MutableDouble3D antennaLocation;

    private boolean recorded_simdis_start;

    private double time_of_last_antenna_update = 0.;

    /**
     * Creates a new instance of DirectionalRadar
     * @param name name of the radar.
     * @param world CMWorld in which the radar plays.
     */
    public DirectionalRadar(String name, CMWorld world)
    {
        super(name, world);

        this.createDirectionalRadar();
    }

    /**
     * Creates a new instance of DirectionalRadar with attributes for graphical display.
     * @param name name of the radar.
     * @param world CMWorld in which the radar plays.
     * @param color color to draw the detection ring.
     */
    public DirectionalRadar(String name, CMWorld world, Color color)
    {
        super(name, world, color);

        this.createDirectionalRadar();
    }

    /**
     * Creates the radar by setting its initial attributes.
     */
    protected final void createDirectionalRadar()
    {
        this.antenna = new Antenna(this.getName() + "_antenna", getWorld());
        this.move = new MoveAntennaBehavior();
        this.setAntennaHeight(5.0);
        this.addBehavior(this.move);
        this.antennaLocation = new MutableDouble3D();
    }

    /**
     * Sets the initial attributes of the radar before each simulation run.
     */
    @Override
    public void initialize()
    {
        super.initialize();

        //  Initialize the antenna orientation
        if (this.getFunction() != Function.TT)
        {
            //  Randomize az
            antenna.setBoresight(getRNG().nextDouble() * 2 * Math.PI,
                antenna.getMainlobeEl());
        }

        this.recorded_simdis_start = false;

        if (this.isEmitting())
        {
            this.move.setEnabled(true);
        }
        else
        {
            this.move.setEnabled(false);
        }


        this.time_of_last_antenna_update = 0.;
    }

    /**
     * Called by the Swarm framework to notify the radar of a state change.  This override
     * gives us the opportunity to call simdisUpdate with the state change.
     * @param old_state previous state.
     * @param new_state new state.
     */
    @Override
    public void stateChanged(IAgentState old_state, IAgentState new_state)
    {
        super.stateChanged(old_state, new_state);

        if (this.isEmitting())
        {
            //  Enable antenna scan behavior
            this.time_of_last_antenna_update = getUniverse().getCurrentTime();
            this.move.setEnabled(true);
        }
        else
        {
            //  Disable antenna scan behavior
            this.move.setEnabled(false);
        }
        simdisUpdate(getUniverse().getCurrentTime(), getWorld().getASIFile());
    }

    /**
     * Moves the antenna to its location at the specified time.
     * @param time time to update the antenna location to.
     */
    @Override
    public void moveAntenna(double time)
    {
        if (time > this.time_of_last_antenna_update)
        {
            if (this.isEmitting())
            {
                if (this.getFunction() != Function.TT)
                {
                    double delta_theta =
                        (time - this.time_of_last_antenna_update) /
                        this.getScanPeriod() * 2 * Math.PI;
                    Double2D boresight = antenna.getBoresight();
                    this.antenna.setBoresight(boresight.getX() + delta_theta,
                        boresight.getY());
                    this.time_of_last_antenna_update = time;
                }
                else
                {
                    //  TT:  Plant it on the first track
                    if (getParent().getSuperior() instanceof SAMSite)
                    {
                        SAMSite site = (SAMSite) getParent().getSuperior();

                        if (site.getEngagedTarget() != null)
                        {
                            Double3D location = getAntennaLocation();
                            Double3D tgt_loc = site.getEngagedTarget().getTarget().
                                getLocation();

                            this.antenna.setBoresight(CMWorld.getEarthModel().
                                azimuthAngle(location, tgt_loc),
                                CMWorld.getEarthModel().elevationAngle(location,
                                tgt_loc, IEarthModel.EarthFactor.EM_EARTH));
                        }
                    }
                }
            }
            if (getWorld().getASIFile() != null)
            {
                simdisUpdate(time, getWorld().getASIFile());
                getParent().simdisUpdate(getUniverse().getCurrentTime(), getWorld().
                    getASIFile());
            }
        }
    }

    public Double3D getAntennaLocation()
    {
        antennaLocation.setXYZ(getParent().getLocation());
        antennaLocation.setZ(antennaLocation.getZ()+LengthUnit.FEET.convert(this.antennaHeight,LengthUnit.NAUTICAL_MILES));

        return antennaLocation;
    }

    /**
     * Scans the radar to look for tracks.  This is called by ScanBehavior.
     * @param current_time current time.
     */
    @Override
    public void scanRadar(double current_time)
    {
        //  How do directional radars work with receivers and jammers.  Currently, with
        //  the base omni radar, detection and jamming are constant regardless of radar orientation.
        //  With directional radar:
        //  Receivers --
        //     Does detection depend on orientation of radar?  No...otherwise we'd have to model discrete dwells.
        //     Instead, receivers will assume detection of average sidelobe unless they indicate otherwise.  That is
        //     It is up to the receiver to determine how to work with directional radar, and which features they consider
        //     to be detectable (mainlobe, sidelobe, or ave_sidelobe).
        //
        //  Jammers --
        //     Jamming should depend on the orientation of the radar, although orientation can be determined at the discrete
        //     times at which the mainlobe intersects a target.  Problem is that we generally check for radar detections only
        //     once per scan period, not at intermediate steps.  Resolution to this may be similar to what we do with receivers,
        //     which is to assume jamming of the average sidelobe unless the jammer indicates otherwise, and to ignore the
        //     orientation.  Otherwise, use orientation, but only at the discrete scan period steps.  The simdis visual will be
        //     wrong, however...not to mention, how do we use moveAntenna...or do we?

        //  Memorize the boresight angle because we are going to set it back to this at the end.
        Double2D boresight = new Double2D(antenna.getBoresight());

        HashSet<Platform> dropped_tracks =
            new HashSet<>(this.getTracks());

        //  Clear anything in the blips.
        this.getBlips().clear();

        //  Clear previous tracks.
        this.getTracks().clear();

        Double3D myLoc = getAntennaLocation();

        //  Now we loop over each AC, change the radar orientation to the target AC, and find jamming
        //  for each one.
        if (getParent().getSuperior() instanceof SAMSite &&
            this.getFunction() == Function.TT)
        {
            SAMSite site = (SAMSite) getParent().getSuperior();

            if (site.getEngagedTarget() != null && site.getEngagedTarget().
                getTarget() instanceof Aircraft &&
                getWorld().getEMLOSUtil().hasLOS(site.getEngagedTarget().
                getTarget(), getParent()))
            {
                Aircraft ac = (Aircraft) site.getEngagedTarget().getTarget();
                ac.movePlatform(current_time);

                Double3D acLoc = ac.getLocation();

                //  We need to plant it on the target AC
                this.antenna.setBoresight(CMWorld.getEarthModel().
                    azimuthAngle(myLoc, acLoc),
                    CMWorld.getEarthModel().elevationAngle(myLoc, acLoc,
                    IEarthModel.EarthFactor.EM_EARTH));
//                if ( this.getFunction() != Function.TT )
//                {
//                    double theta = myLoc.angleTheta(acLoc);
//                    double phi = myLoc.anglePhi(acLoc);
//                    antenna.setBoresight(theta, phi);
//                }

                //  Isn't this going to depend on the orientation of the antenna?
                findJammedRange();

                double j_sq = getJammedRange() * getJammedRange();
                double r_sq = j_sq * Math.sqrt(ac.getRCS());

                if (r_sq > 0.)
                {
                    //  I need true distance.
                    double d_sq =
                        CMWorld.getEarthModel().trueDistanceSq(myLoc, acLoc);

//                    if ( this.getFunction() != Function.TT )
//                    {
//                        //  This should be unreachable
//                        double theta = myLoc.angleTheta(ac.getLocation());
//                        double phi = myLoc.anglePhi(ac.getLocation());
//                        antenna.setBoresight(theta, phi);
//                    }
                    if (ac.getStatus() == Platform.Status.ACTIVE &&
                        d_sq < r_sq)
                    {
                        getBlips().add(ac);

                        //  This checks for a track condition of 2 consecutive blips
                        if (getPreviousBlips().contains(ac))
                        {
                            getTracks().add(ac);
                            dropped_tracks.remove(ac);
                        }
                    }
                }
            }
        }
        else if (this.getFunction() != Function.TT)
        {
            //  This is an EW or TA radar, circular scanning with single beam covering lowest elevation.

            for (Aircraft ac : getWorld().getAircraft())
            {
                if (ac.getRCS() > 0. &&
                    getWorld().getEMLOSUtil().hasLOS(ac, getParent()) &&
                    ac.getStatus() == Platform.Status.ACTIVE)
                {
                    Double3D acLoc = ac.getLocation();
                    //  If the AC has an RCS, is active, and we have LOS to the target, then...

                    //  Put the beam on the AC.
                    double theta =
                        CMWorld.getEarthModel().azimuthAngle(myLoc, acLoc);
                    double phi = boresight.getY();
                    antenna.setBoresight(theta, phi);

                    //  Determine the elevation of the target so that we can determine whether it is within the beam.
                    double el = CMWorld.getEarthModel().elevationAngle(myLoc,
                        acLoc, IEarthModel.EarthFactor.EM_EARTH);
                    //  If the target aircraft is within the elevation coverage of the beam, then see if it is within range.
                    if (el <= antenna.getMainlobeEl() * 2.)
                    {
                        findJammedRange();
                        double j_sq = getJammedRange() * getJammedRange();
                        double r_sq = j_sq * Math.sqrt(ac.getRCS());
                        if (r_sq > 0.)
                        {

                            double d_sq = CMWorld.getEarthModel().
                                trueDistanceSq(myLoc, acLoc);

                            if (d_sq < r_sq)
                            {
                                //  Add a blip
                                getBlips().add(ac);

                                //  This checks for a track condition of 2 consecutive blips
                                if (getPreviousBlips().contains(ac))
                                {
                                    getTracks().add(ac);
                                    dropped_tracks.remove(ac);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (this.getFunction() != Function.TT)
        {
            antenna.setBoresight(boresight.getX(), boresight.getY());
        }

        getPreviousBlips().clear();

        getPreviousBlips().addAll(getBlips());

        if (getParent() instanceof IRadarTrackReceiver)
        {
            //  Report the drops first in order to create capacity for new tracks in the active list.
            if (!dropped_tracks.isEmpty())
            {
                ((IRadarTrackReceiver) getParent()).reportDroppedTracks(this,
                    dropped_tracks);
            }

            if (!getTracks().isEmpty())
            {
                ((IRadarTrackReceiver) getParent()).reportActiveTracks(this,
                    getTracks());
            }
        }

        if (this.getWorld().getASIFile() != null)
        {
            this.simdisUpdate(current_time, getWorld().getASIFile());
        }
    }

    /**
     * Returns the antenna for this radar.
     * @return embedded antenna object.
     */
    public Antenna getAntenna()
    {
        return this.antenna;
    }

    /**
     * Sets the attributes of this radar by parsing the input XML node.
     * @param node XML node to be parsed.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeName().equalsIgnoreCase("Antenna"))
            {
                this.antenna.fromXML(child);
            }
            else if ( child.getNodeName().equalsIgnoreCase("antenna-height"))
            {
                this.setAntennaHeight(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Writes the attributes of this radar to the specified XML node.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);
        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        e = document.createElement("Antenna");
        this.antenna.toXML(e);
        node.appendChild(e);

        e = document.createElement("antenna-height");
        e.setTextContent(String.valueOf(this.getAntennaHeight()));
        node.appendChild(e);
    }

    /**
     * Writes the initial beam parameters for this radar to the SIMDIS asi file.
     * @param asi_file SIMDIS asi file.
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

                pw.println("VertBW\t" + String.valueOf(this.getId()) + "\t" +
                    String.valueOf(2. * Math.toDegrees(antenna.getMainlobeEl())));
                pw.println("HorzBW\t" + String.valueOf(this.getId()) + "\t" +
                    String.valueOf(2. * Math.toDegrees(antenna.getMainlobeAz())));
                pw.println("BodyOffset\t" + String.valueOf(this.getId()) +
                    "\t0.\t0.\t3.");

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Writes the current beam parameters to the SIMDIS asi file.
     * @param time time of the update.
     * @param asi_file SIMDIS asi file.
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
                    if (!recorded_simdis_start)
                    {
                        pw.println("BeamOnOffCmd\t" +
                            String.valueOf(this.getId()) + "\t" +
                            String.valueOf(time) + "\t1");
                        recorded_simdis_start = true;
                    }

                    findJammedRange();

                    double az = this.antenna.getBoresight().getX();
                    double el = this.antenna.getBoresight().getY();

                    Double3D myLoc = getParent().getLocation();

                    //  Now we loop over each AC, change the radar orientation to the target AC, and find jamming
                    //  for each one.
                    if (getParent().getSuperior() instanceof SAMSite &&
                        this.getFunction() == Function.TT)
                    {
                        SAMSite site = (SAMSite) getParent().getSuperior();

                        if (site.getEngagedTarget() != null && site.getEngagedTarget().
                            getTarget() instanceof Aircraft)
                        {
                            Aircraft ac = (Aircraft) site.getEngagedTarget().
                                getTarget();

                            Double3D acLoc = ac.getLocation();

                            az = CMWorld.getEarthModel().azimuthAngle(myLoc,
                                acLoc);
                            el =
                                CMWorld.getEarthModel().elevationAngle(myLoc,
                                acLoc, IEarthModel.EarthFactor.REAL_EARTH);
                        }
                    }

                    pw.println("BeamData\t" + this.getId() + "\t" +
                        time + "\t" + this.getBeamColor() +
                        "\t" + az + "\t" + el +
                        "\t" +
                        LengthUnit.NAUTICAL_MILES.convert(this.getJammedRange(),
                        LengthUnit.METERS));
                }
                else
                {
                    recorded_simdis_start = false;
                    pw.println("BeamOnOffCmd\t" + this.getId() + "\t" +
                        time + "\t0");
                }
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * @return the antennaHeight
     */
    public double getAntennaHeight()
    {
        return antennaHeight;
    }

    /**
     * @param antennaHeight the antennaHeight to set
     */
    public void setAntennaHeight(double antennaHeight)
    {
        this.antennaHeight = antennaHeight;
    }

    /**
     * Inner class to periodically invoke the radar's moveAntenna method.
     */
    protected class MoveAntennaBehavior extends Behavior
    {
        private double delta_t = -1.;

        /**
         * Called by the Swarm framework to return the next schedule time for this behavior
         * to be invoked.
         * @param current_time current simulation time.
         * @return next time at which to call the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double next_time = current_time;

            if (isEmitting() && getWorld().getASIFile() != null)
            {
                if (delta_t < 0.)
                {
                    if (getWorld().getJammers().isEmpty())
                    {
                        delta_t = getScanPeriod() / 4.;
                    }
                    else
                    {
                        delta_t = (antenna.getSidelobeAz() / Math.PI) *
                            getScanPeriod();
                    }

                    if (getFunction() == Function.TT)
                    {
                        delta_t = getScanPeriod();
                    }
                }

                next_time += delta_t;
            }

            return next_time;
        }

        /**
         * Called by the Swarm framework to invoke the behavior.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            moveAntenna(current_time);
        }
    }
}
