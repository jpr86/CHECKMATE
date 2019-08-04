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
 * A behavior for platforms that move back and forth over route points.  This behavior will use the same set of points
 * on both the ingress and egress.
 *
 * @author Jeff Ridder
 */
public class TwoWayRoutePointsBehavior extends RoutePointsBehavior
{
    private static final Logger logger =
        LogManager.getLogger(TwoWayRoutePointsBehavior.class);

    /**
     * Creates a new instance of TwoWayRoutePointsBehavior.
     *
     * @param platform the platform to be moved by this behavior.
     */
    public TwoWayRoutePointsBehavior(MobilePlatform platform)
    {
        super(platform);
    }

    /**
     * Called by the owning platform class to activate the behavior and platform.
     * @param time current time.
     */
    @Override
    public void activate(double time)
    {
        MobilePlatform p = getPlatform();
        Route r = getRoute();

        //  If currently inactive and the route is feasible, then activate.
        if (p.getStatus() == Platform.Status.INACTIVE && roundTripDistance(r) <
            p.getMaxRange())
        {
            p.setStatus(Platform.Status.ACTIVE);
            this.setTimeOfLastKinematicUpdate(time);

            //  Must enable the update_position_behavior since the framework turned it
            //  off while status was INACTIVE

            if (r.getRoutePoints().get(getCurrentPoint()).getPointType() ==
                RoutePoint.PointType.ORBIT)
            {
                this.enterOrbit(time, r.getRoutePoints().get(getCurrentPoint()).
                    getOrbitTime());
            }
            else
            {
                this.setEnabled(true);
                this.setMoving(true);
                this.getBreakOrbitBehavior().setEnabled(false);
            }
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
        return 2. * this.oneWayDistance(route) - orbitDistance(route);
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

        //  Give SIMDIS an update, but first set the heading correctly.
        if (p != null && p.getWorld().getASIFile() != null)
        {

            RoutePoint destination = null;  //  Next RoutePoint I'm heading toward

            Double3D location = p.getLocation();
            if ((getCurrentPoint() < this.getRoute().getRoutePoints().size() - 1 &&
                !p.isRTB()) ||
                (getCurrentPoint() > 0 && p.isRTB()))
            {
                if (!p.isRTB())
                {
                    destination =
                        this.getRoute().getRoutePoints().get(getCurrentPoint() +
                        1);
                }
                else
                {
                    destination =
                        this.getRoute().getRoutePoints().get(getCurrentPoint() -
                        1);
                }
            }

            if (destination != null)
            {
                p.setHeading(CMWorld.getEarthModel().azimuthAngle(location,
                    destination.getPoint()));
                p.setElevationAngle(CMWorld.getEarthModel().elevationAngle(location,
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
        MobilePlatform p = (MobilePlatform) getPlatform();

        if (p != null && p.getStatus() == Platform.Status.ACTIVE)
        {
            super.breakOrbit(current_time);

            //  Check for rtb
            if (getCurrentPoint() == getRoute().getRoutePoints().size() - 1)
            {
                p.setRTB(true);
            }

            p.simdisUpdate(current_time, p.getWorld().getASIFile());

            //  Accrue distance orbited
            p.accrueDistanceMoved(this.getRoute().getRoutePoints().get(getCurrentPoint()).
                getOrbitTime() * p.getSpeed() / 3600.);
        }
    }

    /**
     * Moves the platform over a set of route points.
     *
     * @param time the current simulation time.
     */
    @Override
    public void movePlatform(double time)
    {
        double delta_time = time - this.getTimeOfLastKinematicUpdate();

        MobilePlatform p = getPlatform();

        Route r = getRoute();

        double distance = delta_time * p.getSpeed() / 3600.;

        //  This accrues even if I'm orbiting, because distance_flown is intended
        //  to be used to support rtb decisions (e.g. by comparison of distance_flown
        //  to max range).
        p.accrueDistanceMoved(distance);

        RoutePoint destination = null;  //  Next RoutePoint I'm heading toward

        Double3D location = p.getLocation();

        if ((getCurrentPoint() < r.getRoutePoints().size() - 1 && !p.isRTB()) ||
            (getCurrentPoint() > 0 && p.isRTB()))
        {
            if (!p.isRTB())
            {
                destination = r.getRoutePoints().get(getCurrentPoint() + 1);
            }
            else
            {
                destination = r.getRoutePoints().get(getCurrentPoint() - 1);
            }

            double dist_to_dest =
                CMWorld.getEarthModel().trueDistance(location,
                destination.getPoint());

            if (distance >= dist_to_dest)
            {
                distance -= dist_to_dest;

                if (!p.isRTB())
                {
                    incrementCurrentPoint();
                }
                else
                {
                    decrementCurrentPoint();
                }

                if (getCurrentPoint() == 0 && p.isRTB())
                {
                    p.deactivate(time, Platform.Status.INACTIVE);

                    return;
                }

                p.setLocation(destination.getPoint());
                location = p.getLocation();


                //  Check for whether I've entered an orbit at this point.
                if (r.getRoutePoints().get(getCurrentPoint()).getPointType() ==
                    RoutePoint.PointType.ORBIT && !p.isRTB())
                {
                    this.enterOrbit(time + dist_to_dest * 3600. / p.getSpeed(), r.getRoutePoints().
                        get(getCurrentPoint()).getOrbitTime());
                    return;
                }

                //  Check for rtb
                if (getCurrentPoint() == r.getRoutePoints().size() - 1)
                {
                    p.setRTB(true);
                }

                if (getCurrentPoint() < r.getRoutePoints().size() - 1 &&
                    !p.isRTB())
                {
                    destination = r.getRoutePoints().get(getCurrentPoint() + 1);
                }
                else if (getCurrentPoint() > 0 && p.isRTB())
                {
                    destination = r.getRoutePoints().get(getCurrentPoint() - 1);
                }

                if (destination != null && destination.getPoint() != location)
                {
                    dist_to_dest =
                        CMWorld.getEarthModel().trueDistance(location,
                        destination.getPoint());
                }
                else
                {
                    dist_to_dest = 0.;
                }
            }

            if (dist_to_dest > 0.)
            {
                Double3D new_loc = CMWorld.getEarthModel().
                    interpolateLocation(location, destination.getPoint(),
                    distance);
                p.setLocation(new_loc);
            }

            //  Set heading
            if (p.getStatus() == Platform.Status.ACTIVE && destination != null)
            {
                p.setHeading(CMWorld.getEarthModel().azimuthAngle(location,
                    destination.getPoint()));
                //  The elevation must be corrected by subtracting the elevation for the destination point, but at the same height as current location.
                p.setElevationAngle(CMWorld.getEarthModel().elevationAngle(location,
                    destination.getPoint(), IEarthModel.EarthFactor.REAL_EARTH) - CMWorld.getEarthModel().
                    elevationAngle(location, new Double3D(destination.getPoint().
                    getX(), destination.getPoint().getY(),
                    location.getZ()), IEarthModel.EarthFactor.REAL_EARTH));
            }
        }

        this.setTimeOfLastKinematicUpdate(time);
    }
}
