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
 * A behavior for platforms that navigate through route points periodically.  When a
 * platform reaches the last route point, it will then move toward the first route point
 * and repeat the route until max range is reached.
 *
 * @author Jeff Ridder
 */
public class PeriodicRoutePointsBehavior extends RoutePointsBehavior
{
    private static final Logger logger =
        LogManager.getLogger(PeriodicRoutePointsBehavior.class);

    /**
     * Creates a new instance of PeriodicRoutePointsBehavior
     * @param platform the platform controlled by this behavior.
     */
    public PeriodicRoutePointsBehavior(MobilePlatform platform)
    {
        super(platform);
    }

    /**
     * Moves the platform periodically over a set of route points.
     * @param time simulation time to move the platform to.
     */
    @Override
    public void movePlatform(double time)
    {
        MobilePlatform p = getPlatform();

        IEarthModel e = CMWorld.getEarthModel();

        double delta_time = time - this.getTimeOfLastKinematicUpdate();

        double distance = delta_time * p.getSpeed() / 3600.;

        //  This accrues even if I'm orbiting, because distance_flown is intended
        //  to be used to support rtb decisions (e.g. by comparison of distance_flown
        //  to max range).
        p.accrueDistanceMoved(distance);

        RoutePoint destination = null;  //  Next RoutePoint I'm heading toward

        Double3D location = p.getLocation();

        if (this.getCurrentPoint() < this.getRoute().getRoutePoints().size() - 1)
        {
            destination =
                this.getRoute().getRoutePoints().get(this.getCurrentPoint() + 1);
        }
        else
        {
            destination = this.getRoute().getRoutePoints().get(0);
        }

        double dist_to_dest = e.trueDistance(location, destination.getPoint());

        if (distance >= dist_to_dest)
        {
            distance -= dist_to_dest;

            if (getCurrentPoint() < this.getRoute().getRoutePoints().size() - 1)
            {
                incrementCurrentPoint();
            }
            else
            {
                setCurrentPoint(0);
            }

            if (p.getDistanceMoved() > p.getMaxRange())
            {
                p.deactivate(time, Platform.Status.INACTIVE);

                return;
            }

            p.setLocation(destination.getPoint());
            location = p.getLocation();


            //  Check for whether I've entered an orbit at this point.
            if (this.getRoute().getRoutePoints().get(getCurrentPoint()).
                getPointType() == RoutePoint.PointType.ORBIT)
            {
                this.enterOrbit(time + dist_to_dest * 3600. / p.getSpeed(), this.getRoute().
                    getRoutePoints().get(getCurrentPoint()).getOrbitTime());
                return;
            }

            if (getCurrentPoint() < getRoute().getRoutePoints().size() - 1)
            {
                destination =
                    getRoute().getRoutePoints().get(getCurrentPoint() + 1);
            }
            else
            {
                destination = getRoute().getRoutePoints().get(0);
            }

            if (destination != null && destination.getPoint() != location)
            {
                dist_to_dest = e.trueDistance(location, destination.getPoint());
            }
            else
            {
                dist_to_dest = 0.;
            }
        }

        if (dist_to_dest > 0.)
        {
            p.setLocation(e.interpolateLocation(location, destination.getPoint(),
                distance));
        }

        //  Set heading
        if (p.getStatus() == Platform.Status.ACTIVE && destination != null)
        {
            p.setHeading(e.azimuthAngle(location, destination.getPoint()));
            //  The elevation must be corrected by subtracting the elevation for the destination point, but at the same height as current location.
            p.setElevationAngle(e.elevationAngle(location,
                destination.getPoint(), IEarthModel.EarthFactor.REAL_EARTH) - e.elevationAngle(location, new Double3D(destination.getPoint().
                getX(), destination.getPoint().getY(),
                location.getZ()), IEarthModel.EarthFactor.REAL_EARTH));
        }

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
        super.enterOrbit(current_time, duration);

        MobilePlatform p = getPlatform();

        IEarthModel e = CMWorld.getEarthModel();

        //  Give SIMDIS an update, but first set the heading correctly.
        if (p != null && p.getWorld().getASIFile() != null)
        {

            RoutePoint destination = null;  //  Next RoutePoint I'm heading toward

            Double3D location = p.getLocation();
            if ((getCurrentPoint() < this.getRoute().getRoutePoints().size() - 1))
            {
                destination =
                    this.getRoute().getRoutePoints().get(getCurrentPoint() + 1);
            }
            else
            {
                destination = this.getRoute().getRoutePoints().get(0);
            }

            if (destination != null)
            {
                p.setHeading(e.azimuthAngle(location, destination.getPoint()));
                p.setElevationAngle(e.elevationAngle(location,
                    destination.getPoint(), IEarthModel.EarthFactor.REAL_EARTH));
            }

            //  Give SIMDIS a hit.
            p.simdisUpdate(current_time, p.getWorld().getASIFile());
        }

        logger.debug("Platform " + p.getId() + " orbiting " + " until " +
            (current_time + duration));
    }

    /**
     * Called by BreakOrbitBehavior to break the orbit.
     * @param current_time current time.
     */
    @Override
    public void breakOrbit(double current_time)
    {
        MobilePlatform p = getPlatform();

        if (p != null && p.getStatus() == Platform.Status.ACTIVE)
        {
            super.breakOrbit(current_time);

            p.simdisUpdate(current_time, p.getWorld().getASIFile());

            //  Accrue distance orbited
            p.accrueDistanceMoved(this.getRoute().getRoutePoints().get(getCurrentPoint()).
                getOrbitTime() * p.getSpeed() / 3600.);
        }
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
