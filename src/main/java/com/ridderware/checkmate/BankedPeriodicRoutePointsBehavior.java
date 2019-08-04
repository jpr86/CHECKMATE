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

import com.ridderware.fuse.Double3D;
import org.apache.logging.log4j.*;

/**
 * A behavior for platforms that navigate through route points periodically and bank appropriately
 * in turns.  When a platform reaches the last route point, it will then move toward the first route point
 * and repeat the route until max range is reached.  
 *
 * @author Jeff Ridder
 */
public class BankedPeriodicRoutePointsBehavior extends RoutePointsBehavior
{
    private static final Logger logger =
        LogManager.getLogger(BankedPeriodicRoutePointsBehavior.class);

    boolean orbiting = false;

    /**
     * Creates a new instance of BankedPeriodicRoutePointsBehavior
     * @param platform the aircraft controlled by this behavior.
     */
    public BankedPeriodicRoutePointsBehavior(MobilePlatform platform)
    {
        super(platform);
    }

    /**
     * Resets the kinematic parameters for this behavior.
     */
    @Override
    public void initialize()
    {
        super.initialize();
        this.orbiting = false;
    }

    /**
     * Moves the platform and sets the appropriate orientation, including bank angle in turns.
     *
     * @param time time to move the platform to.
     */
    @Override
    public final void movePlatform(double time)
    {
        if (this.orbiting)
        {
            flyOrbit(time);
        }
        else
        {
            final MobilePlatform p = (MobilePlatform) getPlatform();

            final IEarthModel e = CMWorld.getEarthModel();

            //  Get the current and destination route points.
            BankedRoutePoint p_current = (BankedRoutePoint) getRoute().
                getRoutePoints().get(this.getCurrentPoint());
            if (p_current == null)
            {
                logger.error("Current route point: " + getCurrentPoint() +
                    ", is not a BankedRoutePoint which is required for this behavior");
                System.exit(1);
            }

            BankedRoutePoint p_destination = null;  //  Next RoutePoint I'm heading toward
            //  Here we determine the destination point.
            if (this.getCurrentPoint() <
                this.getRoute().getRoutePoints().size() - 1)
            {
                p_destination = (BankedRoutePoint) this.getRoute().
                    getRoutePoints().get(this.getCurrentPoint() + 1);
            }
            else
            {
                p_destination = (BankedRoutePoint) this.getRoute().
                    getRoutePoints().get(0);
            }

            //  My current location
            Double3D location = p.getLocation();
            Double3D orientation = p.getOrientation();

            double delta_time = time - this.getTimeOfLastKinematicUpdate();

            //  We're going to iterate this until delta_time <= 0.
            while (delta_time > 0.)
            {
                //  Distance flown over the time increment (n.mi.)
                double delta_distance = delta_time * p.getSpeed() / 3600.;

                //  Compute the max heading change not based on the current bank angle, but on the max for the current point.
                //  First, find the current route point.

                //  Now get the max bank angle allowed at the current route point.
                double max_bank_angle = p_current.getBankAngle();

                double gs = 1. / Math.cos(max_bank_angle);
                double delta_heading_max = delta_time * 32.12 * Math.sqrt(gs *
                    gs - 1.) / 1.68781 / p.getSpeed();  //  in radians

                //  The distance to the destination point
                double dist_to_dest = e.trueDistance(location,
                    p_destination.getPoint());

                //  The angluar distance to the destination point (+ = to the right of the nose, - = to the left of the nose).
                double delta_heading_to_dest =
                    AngleUnit.normalizeArc(orientation.getX(),
                    e.azimuthAngle(location, p_destination.getPoint()));

//                if ( dist_to_dest <= slop_factor*delta_distance && Math.abs(delta_heading_to_dest) <= slop_factor*delta_heading_max )
                if (dist_to_dest <= Math.max(delta_distance,
                    p_destination.getSlop()))
                {
                    //  Let's just increment the points, and do nothing else unless there is an orbit about to happen.

                    //  We achieve the destination point within this time increment.
//                    delta_time *= (1.-dist_to_dest/delta_distance);
//
//                    location = p_destination.getPoint();
//
//                    orientation = new Double3D(orientation.getX()+delta_heading_to_dest, e.elevationAngle(location, p_destination.getPoint(), IEarthModel.EarthFactor.REAL_EARTH), 0.);
//
//                    p.accrueDistanceMoved(dist_to_dest);

                    if (getCurrentPoint() < this.getRoute().getRoutePoints().
                        size() - 1)
                    {
                        incrementCurrentPoint();
                    }
                    else
                    {
                        setCurrentPoint(0);
                    }

//                    if ( p.getDistanceMoved() > p.getMaxRange() )
//                    {
//                        p.deactivate(time, Platform.Status.INACTIVE);
//                        p.setLocation(location);
//                        return;
//                    }

                    //  Set the new destination point
                    if (getCurrentPoint() < getRoute().getRoutePoints().size() -
                        1)
                    {
                        p_destination = (BankedRoutePoint) getRoute().
                            getRoutePoints().get(getCurrentPoint() + 1);
                    }
                    else
                    {
                        p_destination = (BankedRoutePoint) getRoute().
                            getRoutePoints().get(0);
                    }

                    //  Check for whether I've entered an orbit at this point.
                    if (this.getRoute().getRoutePoints().get(getCurrentPoint()).
                        getPointType() == RoutePoint.PointType.ORBIT)
                    {
                        this.enterOrbit(time - delta_time,
                            this.getRoute().getRoutePoints().get(getCurrentPoint()).
                            getOrbitTime());
//                        p.setLocation(location);
//                        p.setOrientation(orientation);
                        this.flyOrbit(time);
                        return;
                    }
                }
                else
                {
                    //  We're not going to make our destination either due to insufficient distance or too great a heading change.
                    delta_time = 0.;

                    //  Set heading
                    if (p.getStatus() == Platform.Status.ACTIVE &&
                        p_destination != null)
                    {
                        //  The elevation must be corrected by subtracting the elevation for the destination point, but at the same height as current location.
                        double elevation =
                            e.elevationAngle(location, p_destination.getPoint(),
                            IEarthModel.EarthFactor.REAL_EARTH) -
                            e.elevationAngle(location, new Double3D(p_destination.getPoint().
                            getX(), p_destination.getPoint().getY(),
                            location.getZ()), IEarthModel.EarthFactor.REAL_EARTH);

                        double heading = 0.;
                        double bank = 0.;
                        //  Set the heading and bank angle.
                        if (Math.abs(delta_heading_to_dest) <= delta_heading_max)
                        {
                            heading =
                                AngleUnit.normalizeAngle(orientation.getX() +
                                delta_heading_to_dest, -Math.PI);
                        }
                        else
                        {
                            heading =
                                AngleUnit.normalizeAngle(orientation.getX() +
                                Math.signum(delta_heading_to_dest) *
                                delta_heading_max, -Math.PI);
                            bank = Math.signum(delta_heading_to_dest) *
                                max_bank_angle;
                        }
                        //  Before we compute the new xy location, adjust delta_distance for altitude change.
                        double delta_h = Math.sin(elevation) * delta_distance;
                        Double3D xylocation = e.projectLocation(location,
                            Math.cos(elevation) * delta_distance, heading);
                        location = new Double3D(xylocation.getX(),
                            xylocation.getY(), xylocation.getZ() + delta_h);
                        orientation = new Double3D(heading, elevation, bank);
                        p.accrueDistanceMoved(delta_distance);
                    }
                }
            }
            p.setOrientation(orientation);
            p.setLocation(location);

            this.setTimeOfLastKinematicUpdate(time);

            if (p.getDistanceMoved() > p.getMaxRange())
            {
                p.deactivate(time, Platform.Status.INACTIVE);
            }
        }
    }

    /**
     * Called to execute the orbit flight path, which spirals out from the center to the orbit radius, and then flies that radius for the
     * duration of the orbit.
     * @param time the current simulation time to which we are updating the location and orientation of the platform.
     */
    protected void flyOrbit(double time)
    {
        //  Procedure:
        //  1) Compute turn radius at current speed an max bank angle.
        //  2) Determine if current distance from orbit point is close to the turn radius.  If so, then fly a constant bank angle path.
        //  3) If current distance is less than x% of turn radius, then fly spiral out.
        //  4) If current distance is greater than x% of turn radius, then fly spiral in.

        MobilePlatform p = (MobilePlatform) getPlatform();

        IEarthModel e = CMWorld.getEarthModel();

        //  Get the current and destination route points.
        BankedRoutePoint p_current = (BankedRoutePoint) getRoute().
            getRoutePoints().get(this.getCurrentPoint());
        if (p_current == null)
        {
            logger.error("Current route point: " + getCurrentPoint() +
                ", is not a BankedRoutePoint which is required for this behavior");
            System.exit(1);
        }

        //  My current location
        Double3D location = p.getLocation();

        double delta_time = time - this.getTimeOfLastKinematicUpdate();

        double distance = delta_time * p.getSpeed() / 3600.;

        //  Now get the max bank angle allowed at the current route point.
        double max_bank_angle = p_current.getBankAngle();

        double gs = 1. / Math.cos(max_bank_angle);
        double delta_heading_max =
            delta_time * 32.12 * Math.sqrt(gs * gs - 1.) / 1.68781 /
            p.getSpeed();  //  in radians

        double r1 = e.trueDistance(p_current.getPoint(), location);

//        double theta1 = e.azimuthAngle(p_current.getPoint(), location);

        double speed_fps = p.getSpeed() * 6076.1 / 3600;
        double orbit_radius = speed_fps * speed_fps / 32.12 /
            Math.tan(max_bank_angle) / 6076.1;   //  orbit radius in n.mi.

        //  Test for 3 conditions:
        //  1) If r1 < 0.95 * r2 then

        //  A zeroth order approximation which is exact for a circle (not a spiral).
        double delta_heading = Math.max(0., (orbit_radius -
            Math.abs(orbit_radius - r1)) / orbit_radius * distance /
            orbit_radius);

        assert (delta_heading <= delta_heading_max);

        //  Change the heading depending on whether I am inside or outside of the orbit radius
        p.setHeading(AngleUnit.normalizeAngle(p.getHeading() + Math.signum(r1 -
            orbit_radius) * delta_heading, Math.PI));

        //  Now set the bank angle
        double ratio = p.getSpeed() * delta_heading * 1.68781 / delta_time /
            32.12;
        double bank_angle = Math.acos(1. / Math.sqrt(1. + ratio * ratio));
        p.setBankAngle(Math.signum(r1 - orbit_radius) * bank_angle);

        p.setLocation(e.projectLocation(location, distance, p.getHeading()));

        p.accrueDistanceMoved(distance);

        this.setTimeOfLastKinematicUpdate(time);
    }

    /**
     * Called upon entering an orbit in order to enable the BreakOrbitBehavior.
     * @param current_time current time.
     * @param duration duration of the orbit in seconds.
     */
    @Override
    protected void enterOrbit(double current_time, double duration)
    {
        this.orbiting = true;

        //  Enable break orbit behavior and set time to break
        this.getBreakOrbitBehavior().setEnabled(true);
        this.getBreakOrbitBehavior().setBreakTime(current_time + duration);
    }

    /**
     * Called by BreakOrbitBehavior to break the orbit.
     * @param current_time current time.
     */
    @Override
    public void breakOrbit(double current_time)
    {
        this.orbiting = false;
    }

    /**
     * Returns the round trip distance of the route, including distance orbited.
     *
     * @param route a Route object.
     * @return round trip distance in nautical miles.
     */
    public double roundTripDistance(Route route)
    {
        //  Don't orbit on the return, so take 1 orbit distance away.
        return this.oneWayDistance(route);
    }
}
