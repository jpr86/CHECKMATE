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

import com.ridderware.fuse.Double3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A point in a Route.
 * @author Jeff Ridder
 */
public class RoutePoint implements IXML
{
    /**
     * Enum of point types.
     */
    public enum PointType
    {
        /**
         * Orbit point.
         */
        ORBIT,
        /**
         * Waypoint.
         */
        WAYPOINT
    }

    private Double3D point;

    private PointType point_type;

    private double orbit_time;

    /** Creates a new instance of RoutePoint */
    public RoutePoint()
    {
        this(null, PointType.WAYPOINT, 0.);
    }

    /**
     * Creates a simple RoutePoint of type WAYPOINT.
     * @param x X position of the point.
     * @param y Y position of the point.
     * @param z Z position of the point.
     */
    public RoutePoint(double x, double y, double z)
    {
        this(new Double3D(x, y, z));
    }

    /**
     * Creates a RoutePoint with specification of type.
     * @param x X position of RoutePoint.
     * @param y Y position of RoutePoint.
     * @param z Z position of RoutePoint
     * @param point_type type of point to create.
     */
    public RoutePoint(double x, double y, double z, PointType point_type)
    {
        this(new Double3D(x, y, z), point_type);
    }

    /**
     * Creates a RoutePoint with full specification of parameters.
     * @param x X position of RoutePoint.
     * @param y Y position of RoutePoint.
     * @param z Z position of RoutePoint.
     * @param point_type type of point to create.
     * @param orbit_time orbit time (if orbit point).
     */
    public RoutePoint(double x, double y, double z, PointType point_type,
        double orbit_time)
    {
        this(new Double3D(x, y, z), point_type, orbit_time);
    }

    /**
     * Creates a RoutePoint using a Double3D object as input.
     * @param point Double3D specification of the point.
     */
    public RoutePoint(Double3D point)
    {
        this(point, PointType.WAYPOINT, 0.);
    }

    /**
     * Creates a RoutePoint with Double3D and type.
     * @param point a Double3D object with X, Y, and Z.
     * @param point_type type of point.
     */
    public RoutePoint(Double3D point, PointType point_type)
    {
        this(point, point_type, 0.);
    }

    /**
     * Creates a RoutePoint with Double3D, type, and orbit time.
     * @param point a Double3D object containing the point.
     * @param point_type type of point.
     * @param orbit_time orbit time.
     */
    public RoutePoint(Double3D point, PointType point_type, double orbit_time)
    {
        this.point = point;
        this.point_type = point_type;
        this.orbit_time = orbit_time;
    }

    /**
     * Returns the point.
     * @return a Double3D object.
     */
    public Double3D getPoint()
    {
        return this.point;
    }

    /**
     * Returns the point type.
     * @return ORBIT or WAYPONT.
     */
    public PointType getPointType()
    {
        return this.point_type;
    }

    /**
     * Returns the orbit time.
     * @return orbit duration in seconds.
     */
    public double getOrbitTime()
    {
        return this.orbit_time;
    }

    /**
     * Sets the point's attributes by parsing the XML.
     *
     * @param node root node for the route point.
     */
    @Override
    public void fromXML(Node node)
    {
        NodeList children = node.getChildNodes();

        Double orbitTime = null;
        Double3D point3d = null;
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeName().equalsIgnoreCase("point3D"))
            {
                assert (point3d == null);

                String units = "";
                if (child.getAttributes().getNamedItem("units") != null)
                {
                    units = child.getAttributes().getNamedItem("units").
                        getTextContent();
                }


                Double3D parsed_point = CMWorld.parsePoint3DNode(child);

                if (units.equalsIgnoreCase("DD"))
                {
                    this.point = new Double3D(AngleUnit.DD.convert(parsed_point.getX(), AngleUnit.RADIANS),
                        AngleUnit.DD.convert(parsed_point.getY(),
                        AngleUnit.RADIANS),
                        LengthUnit.FEET.convert(parsed_point.getZ(),
                        LengthUnit.NAUTICAL_MILES));
                }
                else if (units.equalsIgnoreCase("DMS"))
                {
                    this.point = new Double3D(AngleUnit.DMS.convert(parsed_point.getX(), AngleUnit.RADIANS),
                        AngleUnit.DMS.convert(parsed_point.getY(),
                        AngleUnit.RADIANS),
                        LengthUnit.FEET.convert(parsed_point.getZ(),
                        LengthUnit.NAUTICAL_MILES));
                }
                else
                {
                    //  Either flat earth or units input as radians.
                    this.point = new Double3D(parsed_point.getX(),
                        parsed_point.getY(),
                        LengthUnit.FEET.convert(parsed_point.getZ(),
                        LengthUnit.NAUTICAL_MILES));
                }
            }
            else if (child.getNodeName().equalsIgnoreCase("orbit"))
            {
                String orbitTimeVal = child.getTextContent();
                if (orbitTimeVal.equalsIgnoreCase("INFINITE"))
                {
                    orbitTime = Double.POSITIVE_INFINITY;
                }
                else
                {
                    orbitTime = Double.parseDouble(orbitTimeVal);
                }
            }
        }

        if (orbitTime == null || orbitTime == 0.)
        {
            this.orbit_time = 0.;
            this.point_type = PointType.WAYPOINT;
        }
        else
        {
            this.orbit_time = orbitTime;
            this.point_type = PointType.ORBIT;
        }
    }

    /**
     * Creates XML nodes containing the points attributes.
     *
     * @param node root node for the point.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        assert (node.getNodeName().equalsIgnoreCase("route-point"));

        Element e;

        //  Create elephants

        if (CMWorld.getEarthModel().getCoordinateSystem().equalsIgnoreCase("LLA"))
        {
            //  We're going to write out as DD
            e = CMWorld.createPoint3DNode(new Double3D(AngleUnit.RADIANS.convert(point.getX(), AngleUnit.DD),
                AngleUnit.RADIANS.convert(point.getY(), AngleUnit.DD),
                LengthUnit.NAUTICAL_MILES.convert(this.point.getZ(),
                LengthUnit.FEET)), node);
            e.setAttribute("units", "DD");
        }
        else
        {
            //  ENU
            CMWorld.createPoint3DNode(new Double3D(this.point.getX(),
                this.point.getY(),
                LengthUnit.NAUTICAL_MILES.convert(this.point.getZ(),
                LengthUnit.FEET)), node);
        }


        if (orbit_time > 0.)
        {
            e = document.createElement("orbit");
            e.setTextContent(String.valueOf(this.orbit_time));
            node.appendChild(e);
        }
    }
}
