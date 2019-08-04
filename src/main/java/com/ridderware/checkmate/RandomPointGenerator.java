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
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble2D;
import com.ridderware.fuse.Space;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class to generate random points about a centroid, bounded by a space.  Points
 * are generated using a Gaussian distribution.
 *
 * @author Jeff Ridder
 */
public class RandomPointGenerator implements IXML
{
    //  x/y distance for Flat Earth, or Lat/Long for Round Earth
    private Double2D centroid;

    //  Nautical miles
    private double random_radius;

    private CMWorld world;

    private static final Logger logger =
        LogManager.getLogger(RandomPointGenerator.class);

    /**
     * Creates a new instance of RandomPointGenerator.
     * @param world CMWOrld object.
     */
    public RandomPointGenerator(CMWorld world)
    {
        this.centroid = new Double2D(0, 0);
        this.random_radius = 0.;
        this.world = world;
    }

    /**
     * Returns the centroid about which to generate random points.
     * @return Double2D object.
     */
    public Double2D getCentroid()
    {
        return centroid;
    }

    /**
     * Sets the centroid of the pseudo-random locations.  X/Y from origin for
     * Flat Earth, or Lat/Long for Round Earth.
     * @param x X (or Lat) location of centroid.
     * @param y Y (or Long) location of centroid.
     */
    public void setCentroid(double x, double y)
    {
        this.centroid = new Double2D(x, y);
    }

    /**
     * Sets the centroid of the pseudo-random locations.
     * @param centroid a Double2D object containing the centroid coordinates.
     */
    public void setCentroid(Double2D centroid)
    {
        this.centroid = centroid;
    }

    /**
     * Returns the random radius (sigma) used to generate points.
     * @return the random radius in nautical miles.
     */
    public double getRandomRadius()
    {
        return random_radius;
    }

    /**
     * Sets the randome radius (sigma) used to generate points.
     * @param random_radius random radius in nautical miles.
     */
    public void setRandomRadius(double random_radius)
    {
        this.random_radius = random_radius;
    }

    /**
     * Makes a random centroid within the specified space.
     */
    public void makeRandomCentroid()
    {
        RandomNumberGenerator rng = world.getRNG();

        Space space = world.getSpace();

        this.centroid = new Double2D(space.getXmin() + rng.nextDouble() *
            (space.getXmax() - space.getXmin()),
            space.getYmin() + rng.nextDouble() * (space.getYmax() -
            space.getYmin()));
    }

    /**
     * Returns a randomly generated point relative to the centroid using a Gaussian
     * probability distribution.  If the Earth model is Flat Earth, then the random point
     * is X/Y in nautical miles from the origin.  Otherwise, if the Earth model is Round
     * Earth, the random point is returned as Lat/Long in radians.
     *
     * @return Random point as MutableDouble2D.
     */
    public MutableDouble2D randomGaussianPoint()
    {
        RandomNumberGenerator rng = world.getRNG();

        Space space = world.getSpace();

        IEarthModel e = CMWorld.getEarthModel();

        Double3D centroid3D = new Double3D(centroid);

        double x;
        double y;

        double xmin = -Double.MAX_VALUE;
        double xmax = Double.MAX_VALUE;
        double ymin = -Double.MAX_VALUE;
        double ymax = Double.MAX_VALUE;

        if (space != null)
        {
            if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("ENU"))
            {
                xmin = space.getXmin();
                xmax = space.getXmax();
                ymin = space.getYmin();
                ymax = space.getYmax();
            }
            else if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("LLA"))
            {
                xmin = space.getYmin();
                xmax = space.getYmax();
                ymin = space.getXmin();
                ymax = space.getXmax();
            }
        }

        int pass = 0;
        do
        {
            pass++;

            double dist = rng.nextGaussian() * random_radius;
            if (pass > 1 && dist == 0)
            {
                logger.error("Point " + centroid3D +
                    " is outside the space bounds.  Exiting.");
                System.exit(1);
            }
            double angle = -Math.PI + rng.nextDouble() * Math.PI * 2.;

            Double3D xyz = e.projectLocation(centroid3D, dist, angle);
            x = xyz.getX();
            y = xyz.getY();
        }
        while (x < xmin || x > xmax || y < ymin || y > ymax);

        MutableDouble2D pt = new MutableDouble2D(x, y);

        logger.debug("Centroid: " + centroid.toString() + ", Point: " +
            pt.toString());

        return pt;
    }

    /**
     * Returns a randomly generated point relative to the centroid using a uniform
     * probability distribution.
     *
     * @return Random point as Double2D.
     */
    public Double2D randomUniformPoint()
    {
        RandomNumberGenerator rng = world.getRNG();

        Space space = world.getSpace();

        IEarthModel e = CMWorld.getEarthModel();

        Double3D centroid3D = new Double3D(centroid);

        double x;
        double y;

        double xmin = -Double.MAX_VALUE;
        double xmax = Double.MAX_VALUE;
        double ymin = -Double.MAX_VALUE;
        double ymax = Double.MAX_VALUE;

        if (CMWorld.getEarthModel().getCoordinateSystem().equalsIgnoreCase("ENU"))
        {
            xmin = space.getXmin();
            xmax = space.getXmax();
            ymin = space.getYmin();
            ymax = space.getYmax();
        }
        else if (CMWorld.getEarthModel().getCoordinateSystem().
            equalsIgnoreCase("LLA"))
        {
            xmin = space.getYmin();
            xmax = space.getYmax();
            ymin = space.getXmin();
            ymax = space.getXmax();
        }

        int pass = 0;
        do
        {
            pass++;
            double dist = rng.nextDouble() * random_radius;
            if (pass > 1 && dist == 0)
            {
                logger.error("Point " + centroid3D +
                    " is outside the space bounds.  Exiting.");
                System.exit(1);
            }
            double angle = -Math.PI + rng.nextDouble() * Math.PI * 2.;

            Double3D xyz = e.projectLocation(centroid3D, dist, angle);
            x = xyz.getX();
            y = xyz.getY();
        }
        while (x < xmin || x > xmax || y < ymin || y > ymax);

        return new Double2D(x, y);
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node.
     */
    @Override
    public void fromXML(Node node)
    {
        if (node.getNodeName().equalsIgnoreCase("Location"))
        {
            boolean random = false;
            String locationType = node.getAttributes().getNamedItem("type").
                getTextContent();
            if (locationType.equalsIgnoreCase("random"))
            {
                this.makeRandomCentroid();
                random = true;
            }

            NodeList chillens = node.getChildNodes();
            for (int k = 0; k < chillens.getLength(); k++)
            {
                Node chicken = chillens.item(k);

                if (chicken.getNodeName().equalsIgnoreCase("random-radius"))
                {
                    this.random_radius =
                        Double.parseDouble(chicken.getTextContent());
                }
                else if (chicken.getNodeName().equalsIgnoreCase("point2D") &&
                    !random)
                {
                    Double2D ptcentroid = CMWorld.parsePoint2DNode(chicken);
                    if (CMWorld.getEarthModel().getCoordinateSystem().
                        equalsIgnoreCase("LLA"))
                    {
                        this.setCentroid(new Double2D(AngleUnit.DD.convert(ptcentroid.getX(),
                            AngleUnit.RADIANS),
                            AngleUnit.DD.convert(ptcentroid.getY(),
                            AngleUnit.RADIANS)));
                    }
                    else
                    {
                        this.setCentroid(ptcentroid);
                    }
                }
            }
        }
    }

    /**
     * Creates XML nodes containing the attributes.
     *
     * @param node root node.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        Element e;

        //  Create element
        e = document.createElement("random-radius");
        e.setTextContent(String.valueOf(this.random_radius));
        node.appendChild(e);

        Double2D ddCentroid = new Double2D(AngleUnit.RADIANS.convert(centroid.getX(), AngleUnit.DD),
            AngleUnit.RADIANS.convert(centroid.getY(), AngleUnit.DD));

        CMWorld.createPoint2DNode(ddCentroid, node);
    }
}
