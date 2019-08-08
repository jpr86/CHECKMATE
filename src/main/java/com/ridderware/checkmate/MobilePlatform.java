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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import com.ridderware.fuse.Behavior;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble3D;
import com.ridderware.fuse.gui.Painter;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Extends Platform by adding attributes and methods related to kinematics.
 *
 * @author Jeff Ridder
 */
public abstract class MobilePlatform extends Platform
{
    private double max_range;

    private double distance_moved;

    private double speed;

    //  The heading, elevation, and bank angles relative to the local down frame.
    //  Heading (z-axis rotation), elevation (y-axis rotation), bank-angle (x-axis rotation)
    private MutableDouble3D orientation;

    private boolean rtb;

    private double start_time;

    private double end_time;

    private MovePlatformBehavior move_platform_behavior;

    private static final Logger logger =
        LogManager.getLogger(MobilePlatform.class);

    /**
     * Creates a new instance of MobilePlatform.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     */
    public MobilePlatform(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createMobilePlatform();
    }

    /**
     * Creates a new instance of MobilePlatform with attributes for graphical display.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     * @param ttf font containing the symbol to be displayed.
     * @param fontSymbol font symbol to display for this object.
     * @param color color for this symbol.
     */
    public MobilePlatform(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createMobilePlatform();
    }

    /**
     * Sets the initial attributes of the object upon creation.
     */
    protected final void createMobilePlatform()
    {
        this.setSpeed(0.);
        this.orientation = new MutableDouble3D(0., 0., 0.);
        this.setStartTime(0.);
        this.end_time = 0.;
        this.setMaxRange(Double.MAX_VALUE);
        this.setRTB(false);
        this.distance_moved = 0.;
    }

    /**
     * Sets the MovePlatformBehavior for this MobilePlatform.
     *
     * @param move_platform_behavior MovePlatformBehavior
     */
    public void setMovePlatformBehavior(MovePlatformBehavior move_platform_behavior)
    {
        this.move_platform_behavior = move_platform_behavior;
        this.addBehavior(move_platform_behavior);
    }

    /**
     * Returns the maximum range of the platform in nautical miles.
     * @return maximum range.
     */
    public double getMaxRange()
    {
        return max_range;
    }

    /**
     * Sets the maximum range of the platform in nautical miles.
     * @param max_range maximum range.
     */
    public void setMaxRange(double max_range)
    {
        this.max_range = max_range;
    }

    /**
     * Sets the return-to-base condition for the platform.
     * @param rtb return-to-base flag.
     */
    public void setRTB(boolean rtb)
    {
        this.rtb = rtb;
    }

    /**
     * Returns true if platform is returning to base, false otherwise.
     * @return return-to-base flag.
     */
    public boolean isRTB()
    {
        return this.rtb;
    }

    /**
     * Resets the attributes of the platform before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
        //  Set my initial status to INACTIVE
        this.setStatus(Platform.Status.INACTIVE);

        this.setHeading(0.);
        this.setElevationAngle(0.);
        this.setRTB(false);
        this.end_time = 0.;
        this.distance_moved = 0.;

        if (getMovePlatformBehavior() != null)
        {
            getMovePlatformBehavior().initialize();
        }

        if (this.getStartTime() == 0.)
        {
            this.activate(0.);
        }
    }

    /**
     * Called by ActivateBehavior to activate the platform at the specified time.
     * @param time current time.
     */
    public void activate(double time)
    {
        getMovePlatformBehavior().activate(time);
        logger.debug("MobilePlatform " + this.getId() + " activated at time " +
            time);
        simdisUpdate(time, getWorld().getASIFile());
    }

    /**
     * Deactivates the aircraft upon its termination -- due either to mission
     * completion or destruction -- in the current run.
     * @param status current status prior to deactivation.
     * @param time current time.
     */
    public void deactivate(double time, Platform.Status status)
    {
        if (this.getStatus() == Platform.Status.ACTIVE)
        {
            this.setStatus(status);
            this.getMovePlatformBehavior().setEnabled(false);
            this.end_time = time;
        }
    }

    /**
     * Behavior class to automatically activate the platform at the specified
     * start time.
     */
    protected class ActivateBehavior extends Behavior
    {
        /**
         * Called by the FUSE simulation framework.  Returns the next time at which to
         * perform the behavior.
         * @param current_time current simulation time.
         * @return time at which to perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            return getStartTime();
        }

        /**
         * Called by the FUSE simulation framework.  Performs the behavior at the
         * specified time.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            activate(current_time);
        }
    }

    /**
     * Returns the time at which this aircraft deactivated in the simulation.
     * @return end time.
     */
    public double getEndTime()
    {
        return this.end_time;
    }

    /**
     * Returns the start time -- the time at which the aircraft will transition from
     * an initial INACTIVE state to an ACTIVE state -- of the aircraft in seconds.
     * @return start time.
     */
    public double getStartTime()
    {
        return start_time;
    }

    /**
     * Sets the start time -- the time at which the aircraft will transition from
     * an initial INACTIVE state to an ACTIVE state -- of the aircraft in seconds.
     * @param start_time start time.
     */
    public void setStartTime(double start_time)
    {
        this.start_time = start_time;
    }

    /**
     * Returns the speed of the platform.
     * @return speed in knots.
     */
    public double getSpeed()
    {
        return speed;
    }

    /**
     * Sets the speed of the platform (in knots).
     * @param speed speed in knots.
     */
    public void setSpeed(double speed)
    {
        this.speed = speed;
    }

    /**
     * Sets the distance the platform has moved for the current run.
     *
     * @param distance_moved distance moved in nautical miles.
     */
    public void setDistanceMoved(double distance_moved)
    {
        this.distance_moved = distance_moved;
    }

    /**
     * Returns the distance the platform has moved for the current run.
     * @return distance moved in nautical miles.
     */
    public double getDistanceMoved()
    {
        return this.distance_moved;
    }

    /**
     * Adds to the distance the platform has moved in nautical miles.
     * @param distance distance to add to the current distance moved.
     */
    public void accrueDistanceMoved(double distance)
    {
        this.distance_moved += distance;
    }

    /**
     * Returns the heading of the platform in radians relative to "North" (constant x, y increasing).
     * @return heading.
     */
    public double getHeading()
    {
        return this.orientation.getX();
    }

    /**
     * Sets the heading in radians relative to "North".
     * @param heading heading in radians.
     */
    public void setHeading(double heading)
    {
        this.orientation.setX(heading);
    }

    /**
     * Sets the elevation angle in radians.
     * @param elevation elevation angle (positive is up).
     */
    public void setElevationAngle(double elevation)
    {
        this.orientation.setY(elevation);
    }

    /**
     * Returns the elevation angle in radians.
     * 
     * @return elevation angle (positive is up)
     */
    public double getElevationAngle()
    {
        return this.orientation.getY();
    }

    /**
     * Sets the bank angle in radians.
     * @param bank bank angle (positive is roll right).
     */
    public void setBankAngle(double bank)
    {
        this.orientation.setZ(bank);
    }

    /**
     * Returns the bank angle in radians.
     * 
     * @return bank angle (positive is roll right)
     */
    public double getBankAngle()
    {
        return this.orientation.getZ();
    }

    /**
     * Returns the orientation of the platform as a Double3D (heading, elevation, bank)
     * @return 3-D vector of the orientation.
     */
    public Double3D getOrientation()
    {
        return orientation;
    }

    /**
     * Sets the orientation of the platform as a Double3D (heading, elevation, bank) in radians.
     * @param orientation a 3-D vector holding the orientation.
     */
    public void setOrientation(Double3D orientation)
    {
        this.orientation.setXYZ(orientation);
    }

    /**
     * Calls the embedded behavior to move the platform to the requested time.
     * @param time current time.
     */
    public void movePlatform(double time)
    {
        if (getMovePlatformBehavior() != null)
        {
            getMovePlatformBehavior().movePlatform(time);
        }
    }

    /**
     * Override of base class method to paint the agent.
     * @param args see base class.
     * @return collection of shapes to be drawn.
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        HashSet<Shape> boundingShapes = new HashSet<Shape>(1);

        if (this.getStatus() == Status.ACTIVE)
        {
            Double3D loc = null;
            if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("ENU"))
            {
                loc = this.getLocation();
            }
            else if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("LLA"))
            {
                loc = new Double3D(getLocation().getY(), getLocation().getX(), getLocation().
                    getZ());
            }
            if (loc != null)
            {
                boundingShapes.add(Painter.getPainter().paintText(this.getFontSymbol(),
                    loc, this.getColor(), this.getFont(), this.getHeading()));
            }

        }
        return boundingShapes;
    }

    /**
     * Override of base class method.  Writes the SIMDIS data including orientation data.
     * @param time time of the update
     * @param asi_file the SIMDIS .asi file.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        if (this.getSIMDISIcon() != null && asi_file != null &&
            this.getStatus() == Platform.Status.ACTIVE)
        {
            try
            {
                String simdis_output = "";

                if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("ENU"))
                {
                    simdis_output = LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getX(), LengthUnit.METERS) +
                        "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getY(), LengthUnit.METERS) +
                        "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getZ(), LengthUnit.METERS);
                }
                else if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("LLA"))
                {
                    //  round earth
                    simdis_output += getLocation().getX() +
                        "\t" + getLocation().getY() +
                        "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getZ(), LengthUnit.METERS);
                }

                simdis_output += "\t" + this.getHeading() + "\t" +
                    this.getElevationAngle() + "\t0.";

                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("PlatformData\t" + String.valueOf(this.getId()) +
                    "\t" + String.valueOf(time) + "\t" +
                    simdis_output);
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Returns the MovePlatformBehavior object.
     * @return MovePlatformBehavior object.
     */
    public MovePlatformBehavior getMovePlatformBehavior()
    {
        return move_platform_behavior;
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


            if (child.getNodeName().equalsIgnoreCase("start-time"))
            {
                this.start_time = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().equalsIgnoreCase("max-range"))
            {
                this.max_range = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().equalsIgnoreCase("speed"))
            {
                this.setSpeed(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("MovePlatformBehavior"))
            {
                this.setMovePlatformBehavior(getWorld().
                    createMovePlatformBehavior(child, this));
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

        Document document = node.getOwnerDocument();

        Element e = document.createElement("speed");
        e.setTextContent(String.valueOf(this.speed));
        node.appendChild(e);

        e = document.createElement("start-time");
        e.setTextContent(String.valueOf(this.start_time));
        node.appendChild(e);

        e = document.createElement("max-range");
        e.setTextContent(String.valueOf(this.max_range));
        node.appendChild(e);

        if (this.move_platform_behavior != null)
        {
            getWorld().writeMovePlatformBehaviorToXML(this.move_platform_behavior,
                node);
        }
    }
}
