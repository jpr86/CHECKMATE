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

import com.ridderware.jrandom.RandomNumberGenerator;
import java.util.ArrayList;
import com.ridderware.fuse.Space;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Describes a route to be traversed by a platform.
 * @author Jeff Ridder
 */
public class Route implements IXML
{
    private CMWorld world;

    //  Random route parameters
    private boolean make_random;

    private int num_points;

    private double altitude;

    private boolean orbitable;

    private double max_orbit_time;

    private ArrayList<RoutePoint> route_points = new ArrayList<>();

    /**
     * Creates a new instance of Route
     */
    public Route()
    {
        this.world = null;
        this.make_random = false;
        this.num_points = 5;
        this.setAltitude(0.);
        this.setOrbitable(false);
        this.setMaxOrbitTime(900.);
    }

    /**
     * Used to create a new route while parsing the scenario file from XML.
     * @param world CMWorld object
     */
    public Route(CMWorld world)
    {
        this();
        this.world = world;
    }

    /**
     * Returns whether this route is randomly generated.
     * @return true if random, false if fixed.
     */
    public boolean isRandomRoute()
    {
        return this.make_random;
    }

    /**
     * Sets whether this route is randomly generated.
     * @param make_random true for random, false for fixed.
     */
    public void setRandomRoute(boolean make_random)
    {
        this.make_random = make_random;
    }

    /**
     * Sets the number of random points to generate.
     * @param num_points number of random points.
     */
    public void setNumRandomPoints(int num_points)
    {
        this.num_points = num_points;
    }

    /**
     * Returns the number of random points to generate.
     * @return number of random points.
     */
    public int getNumRandomPoints()
    {
        return this.num_points;
    }

    /**
     * Returns the altitude of the randomly generated route.
     * @return altitude in feet.
     */
    public double getAltitude()
    {
        return altitude;
    }

    /**
     * Sets the altitude of the randomly generated route.
     * @param altitude altitude in feet.
     */
    public void setAltitude(double altitude)
    {
        this.altitude = altitude;
    }

    /**
     * Returns whether route points in the randomly generated route may be orbit points.
     * @return true if orbit is allowed, false otherwise.
     */
    public boolean isOrbitable()
    {
        return orbitable;
    }

    /**
     * Sets whether the route points in the randomly generated route may be orbit points.
     * @param orbitable true if orbit is allowed, and false otherwise.
     */
    public void setOrbitable(boolean orbitable)
    {
        this.orbitable = orbitable;
    }

    /**
     * Returns the maximum orbit time of any orbit points in the randomly generated route.
     * @return max orbit time in seconds.
     */
    public double getMaxOrbitTime()
    {
        return max_orbit_time;
    }

    /**
     * Sets the maximum orbit time of any orbit points in the randomly generated route.
     * @param max_orbit_time max orbit time in seconds.
     */
    public void setMaxOrbitTime(double max_orbit_time)
    {
        this.max_orbit_time = max_orbit_time;
    }

    /**
     * Adds a RoutePoint to the route.
     * @param point a RoutePoint object.
     */
    public void addRoutePoint(RoutePoint point)
    {
        this.route_points.add(point);
    }

    /**
     * Sets the route with an array of RoutePoint objects.
     * @param route_points ArrayList of RoutePoint objects.
     */
    public void setRoutePoints(ArrayList<RoutePoint> route_points)
    {
        this.route_points = route_points;
    }

    /**
     * Returns a collection of RoutePoint objects characterizing the route.
     * @return ArrayList of RoutePoint objects.
     */
    public ArrayList<RoutePoint> getRoutePoints()
    {
        return this.route_points;
    }

    /**
     * Method to make a random route.
     */
    public void makeRandomRoute()
    {
        route_points.clear();

        RandomNumberGenerator rng = world.getRNG();

        Space space = world.getSpace();

        for (int j = 0; j < num_points; j++)
        {
            RoutePoint.PointType ptype = RoutePoint.PointType.WAYPOINT;

            if (orbitable)
            {
                if (rng.nextDouble() < 0.5)
                {
                    ptype = RoutePoint.PointType.WAYPOINT;
                }
                else
                {
                    ptype = RoutePoint.PointType.ORBIT;
                }
            }

            this.addRoutePoint(new RoutePoint(space.getXmin() +
                rng.nextDouble() * (space.getXmax() - space.getXmin()),
                space.getYmin() + rng.nextDouble() * (space.getYmax() -
                space.getYmin()),
                LengthUnit.FEET.convert(altitude, LengthUnit.NAUTICAL_MILES),
                ptype, rng.nextDouble() * max_orbit_time));
        }
    }

//    /**
//     * This has been commented out because it works only for straight line route segments (i.e., flat earth) and
//     * also because this routine is not used anywhere.  Question whether it is even needed.
//     *
//     * Computes the closest horizontal distance to the route from the input point.  This is the
//     * two-dimensional distance in the XY plane, ignoring altitude.
//     *
//     * @param point point from which to compute the distance to the route.
//     * @return distance to route in nautical miles.
//     */
//    public double calcHorizontalDistanceToRoute(Double2D point)
//    {
//        double dist = Double.MAX_VALUE;
//        double x = point.getX();
//        double y = point.getY();
//        Double3D point1 = route_points.get(0).getPoint();
//        double x1 = point1.getX();
//        double y1 = point1.getY();
//        //  Loop over the points and compute the distance to each line segment.
//        for ( int i = 0; i < this.route_points.size(); i++ )
//        {
//            Double3D point2 = route_points.get(i+1).getPoint();
//            double x2 = point2.getX();
//            double y2 = point2.getY();
//
//            double delx = x2-x1;
//            double dely = y2-y1;
//
//            double u = ((x-x1)*(x2-x1)+(y-y1)*(y2-y1))/(delx*delx+dely*dely);
//            u = (u < 0. ? 0 : (u > 1. ? 1. : u ));
//
//            //  Closest point on the line segment
//            double x3 = x1+u*delx;
//            double y3 = y1+u*dely;
//
//            double dx = x3-x;
//            double dy = y3-y;
//            //  Distance squared to closest point on line segment
//            double d2 = dx*dx+dy*dy;
//            dist = (d2 < dist ? d2 : dist);
//
//            //  Shift point 2 into the 1 slot for the next segment.
//            point1 = point2;
//            x1 = x2;
//            y1 = y2;
//        }
//
//        //  Square root it to get the distance.
//        return Math.sqrt(dist);
//    }
    /**
     * Sets the route's attributes by parsing the XML.
     *
     * @param routeNode root node for the route.
     */
    @Override
    public void fromXML(Node routeNode)
    {
        route_points.clear();

        assert (routeNode.getNodeName().equalsIgnoreCase("route"));
        String routeType = routeNode.getAttributes().getNamedItem("type").
            getTextContent();
        if (routeType.equalsIgnoreCase("Random"))
        {
            this.make_random = true;

            NodeList routeProperties = routeNode.getChildNodes();
            for (int r = 0; r < routeProperties.getLength(); r++)
            {
                Node routeProperty = routeProperties.item(r);
                if (routeProperty.getNodeName().equalsIgnoreCase("num-points"))
                {
                    this.num_points =
                        Integer.parseInt(routeProperty.getTextContent());
                }
                else if (routeProperty.getNodeName().equalsIgnoreCase("orbitable"))
                {
                    this.orbitable =
                        Boolean.parseBoolean(routeProperty.getTextContent());
                }
                else if (routeProperty.getNodeName().equalsIgnoreCase("max-orbit-time"))
                {
                    this.max_orbit_time =
                        Double.parseDouble(routeProperty.getTextContent());
                }
                else if (routeProperty.getNodeName().equalsIgnoreCase("altitude"))
                {
                    this.altitude =
                        Double.parseDouble(routeProperty.getTextContent());
                }
            }

            assert (world != null) :
                "Wrong constructor used, world not initialized";
            this.makeRandomRoute();
        }
        else if (routeType.equalsIgnoreCase("Fixed"))
        {
            this.make_random = false;

            NodeList routeChildren = routeNode.getChildNodes();
            for (int r = 0; r < routeChildren.getLength(); r++)
            {
                Node routeChild = routeChildren.item(r);
                if (routeChild.getNodeName().equalsIgnoreCase("route-point"))
                {
                    RoutePoint rp = world.createRoutePoint(routeChild);
                    if (rp != null)
                    {
                        this.addRoutePoint(rp);
                    }
                    else
                    {
                        assert (true) : "parsed a null route point";
                    }
                }
            }
        }
        else
        {
            throw new IllegalArgumentException("Unhandled route type: " +
                routeType);
        }
    }

    /**
     * Creates XML nodes containing the route's attributes.
     *
     * @param node root node for the route.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        assert (node.getNodeName().equalsIgnoreCase("route"));

        Element e;

        if (this.make_random)
        {
            //  Set the random attributes
            e = (Element) node;
            e.setAttribute("type", "Random");

            e = document.createElement("num-points");
            e.setTextContent(String.valueOf(this.getNumRandomPoints()));
            node.appendChild(e);

            e = document.createElement("orbitable");
            e.setTextContent(String.valueOf(this.isOrbitable()));
            node.appendChild(e);

            e = document.createElement("max-orbit-time");
            e.setTextContent(String.valueOf(this.getMaxOrbitTime()));
            node.appendChild(e);

            e = document.createElement("altitude");
            e.setTextContent(String.valueOf(this.getAltitude()));
            node.appendChild(e);
        }
        else
        {
            e = (Element) node;
            e.setAttribute("type", "Fixed");
            //  Loop over route points and serialize them.
            for (RoutePoint p : this.route_points)
            {
                //  Create elephants
                world.writeRoutePointToXML(p, node);
            }
        }
    }
}
