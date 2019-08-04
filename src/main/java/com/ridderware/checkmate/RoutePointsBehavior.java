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

import com.ridderware.fuse.Behavior;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An abstract behavior for mobile platforms that move over route points.
 *
 * @author Jeff Ridder
 */
public abstract class RoutePointsBehavior extends MovePlatformBehavior
{
    private Route route;

    private int current_point;

    private BreakOrbitBehavior break_orbit_behavior;

    private static final Logger logger =
        LogManager.getLogger(RoutePointsBehavior.class);

    /**
     * Creates a new instance of RoutePointsBehavior
     * @param platform the MobilePlatform controlled by this behavior.
     */
    public RoutePointsBehavior(MobilePlatform platform)
    {
        super(platform);
        this.route = null;
        this.current_point = 0;
        this.break_orbit_behavior = new BreakOrbitBehavior();
        platform.addBehavior(this.break_orbit_behavior);
    }

    /**
     * Sets the route to over which to move the platform.
     *
     *
     * @param route the Route defining the path for the platform.
     */
    public void setRoute(Route route)
    {
        this.route = route;
    }

    /**
     * Returns the route.
     * @return Route object.
     */
    public Route getRoute()
    {
        return route;
    }

    /**
     * Returns the current route point index.
     * @return current route point.
     */
    public int getCurrentPoint()
    {
        return this.current_point;
    }

    /**
     * Sets the current route point index.
     * @param current_point new value of the route point index.
     */
    public void setCurrentPoint(int current_point)
    {
        this.current_point = current_point;
    }

    /**
     * Increments the current route point index and returns the value.
     * @return incremented route point index.
     */
    public int incrementCurrentPoint()
    {
        return ++this.current_point;
    }

    /**
     * Decrements the current route point index and returns the value.
     * @return decremented route point index.
     */
    public int decrementCurrentPoint()
    {
        return --this.current_point;
    }

    /**
     * Returns the break orbit behavior.
     * @return BreakOrbitBehavior.
     */
    public BreakOrbitBehavior getBreakOrbitBehavior()
    {
        return this.break_orbit_behavior;
    }

    /**
     * Resets the kinematic parameters for this behavior.
     */
    @Override
    public void initialize()
    {
        super.initialize();
        this.current_point = 0;

        if (route != null && route.isRandomRoute())
        {
            this.route.makeRandomRoute();
        }

        if (!route.getRoutePoints().isEmpty())
        {
            getPlatform().setLocation(route.getRoutePoints().get(0).getPoint());

            if (route.getRoutePoints().size() > 1)
            {
                getPlatform().setHeading(CMWorld.getEarthModel().
                    azimuthAngle(getPlatform().getLocation(), route.getRoutePoints().
                    get(1).getPoint()));
            }
        }
    }

    /**
     * Called by the owning platform class to activate the behavior and platform.
     * @param time current time.
     */
    @Override
    public void activate(double time)
    {
        MobilePlatform p = getPlatform();

        //  If currently inactive and the route is feasible, then activate.
        if (p.getStatus() == Platform.Status.INACTIVE)
        {
            p.setStatus(Platform.Status.ACTIVE);
            this.setTimeOfLastKinematicUpdate(time);


            if (this.route.getRoutePoints().get(current_point).getPointType() ==
                RoutePoint.PointType.ORBIT)
            {
                this.enterOrbit(time, route.getRoutePoints().get(current_point).
                    getOrbitTime());
            }
            else
            {
                this.setEnabled(true);
                this.setMoving(true);
                this.break_orbit_behavior.setEnabled(false);
            }
        }
    }

    /**
     * Called upon entering an orbit in order to enable the BreakOrbitBehavior.
     * @param current_time current time.
     * @param duration duration of the orbit in seconds.
     */
    protected void enterOrbit(double current_time, double duration)
    {
        //  Disable update position behavior by flagging it to be not be moving.
        this.setMoving(false);

        //  Enable break orbit behavior and set time to break
        this.break_orbit_behavior.setEnabled(true);
        this.break_orbit_behavior.setBreakTime(current_time + duration);
    }

    /**
     * Called by BreakOrbitBehavior to break the orbit.
     * @param current_time current time.
     */
    public void breakOrbit(double current_time)
    {
        MobilePlatform p = (MobilePlatform) getPlatform();

        if (p != null && p.getStatus() == Platform.Status.ACTIVE)
        {
            //  Set time of last kinematic update to current_time
            this.setTimeOfLastKinematicUpdate(current_time);

            //  Enable update_position_behavior
            this.setMoving(true);
            this.setEnabled(true);

            logger.debug("Platform " + p.getId() + " breaking orbit at " +
                current_time);
        }
    }

    /**
     * A sub-behavior to break the orbit of the platform.
     */
    protected class BreakOrbitBehavior extends Behavior
    {
        private double break_time;

        /**
         * Creates an instance of BreakOrbitBehavior.
         */
        public BreakOrbitBehavior()
        {
            this.break_time = 0.;
        }

        /**
         * Sets the time to break the orbit.
         * @param break_time break time.
         */
        public void setBreakTime(double break_time)
        {
            this.break_time = break_time;
        }

        /**
         * Called by the Swarm framework to get the next time at which to perform the behavior.
         * @param current_time current time.
         * @return next time to perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            return break_time;
        }

        /**
         * Called by the Swarm framework to perform the behavior.  Calls breakOrbit.
         * @param current_time current time.
         */
        @Override
        public void perform(double current_time)
        {
            breakOrbit(current_time);
        }
    }

    /**
     * Returns the one-way distance of the route, include distance orbited.
     *
     * @param route a Route object.
     * @return one-way distance in nautical miles.
     */
    public double oneWayDistance(Route route)
    {
        double distance = 0.;
        for (int i = 0; i < route.getRoutePoints().size() - 1; i++)
        {
            distance += CMWorld.getEarthModel().trueDistance(route.getRoutePoints().
                get(i).getPoint(),
                route.getRoutePoints().get(i + 1).getPoint());
        }

        distance += orbitDistance(route);

        return distance;
    }

    /**
     * Computes the distance flown while in orbit.
     * @param route a Route object.
     * @return distance flown in orbit.
     */
    protected double orbitDistance(Route route)
    {
        double distance = 0.;

        for (int i = 0; i < route.getRoutePoints().size(); i++)
        {
            if (route.getRoutePoints().get(i).getPointType() ==
                RoutePoint.PointType.ORBIT)
            {
                distance += getPlatform().getSpeed() * route.getRoutePoints().
                    get(i).getOrbitTime() / 3600.;
            }
        }

        return distance;
    }

    /**
     * Sets the behavior's attributes by parsing the XML.
     * @param node root node for the behavior.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();
        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("route"))
            {
                Route rt = new Route(getPlatform().getWorld());
                rt.fromXML(child);
                this.setRoute(rt);
            }
        }
    }

    /**
     * Creates XML nodes containing the behavior's attributes.
     *
     * @param node root node for the behavior.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        if (this.getRoute() != null)
        {
            e = document.createElement("route");
            node.appendChild(e);
            this.getRoute().toXML(e);
        }
    }
}
