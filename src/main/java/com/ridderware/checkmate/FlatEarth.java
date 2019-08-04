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

/**
 * Flat Earth model.
 *
 * @author Jeff Ridder
 */
public class FlatEarth implements IEarthModel
{
    /** Creates a new instance of FlatEarth */
    public FlatEarth()
    {
    }

    /**
     * Returns the coordinate system as a String.
     *
     * @return coordinate system in use.
     */
    @Override
    public final String getCoordinateSystem()
    {
        return "ENU";
    }

    /**
     *  Computes the line-of-site distance between two points.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      line of site distance in nmi.
     */
    @Override
    public final double trueDistance(Double3D pt1, Double3D pt2)
    {
        return pt1.distance(pt2);
    }

    /**
     * Returns the square of the true line-of-site distance between two points.
     * Although it might appear redundant, we include it in this interface since many calculations can
     * be significantly accelerated by computing and working with the square of the distance.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      square of the line of site distance in nmi-squared.
     */
    @Override
    public final double trueDistanceSq(Double3D pt1, Double3D pt2)
    {
        return pt1.distanceSq(pt2);
    }

    /**
     *  Projects the ending location given a staring point, distance, and azimuth.
     *
     * @param  pt     starting point.
     * @param  distance  distance in nmi.
     * @param  azimuth   azimuth between +/- PI and in radians.
     * @return           ending location.
     */
    @Override
    public final Double3D projectLocation(Double3D pt, double distance,
        double azimuth)
    {
        final double x = pt.getX() + distance * Math.sin(azimuth);
        final double y = pt.getY() + distance * Math.cos(azimuth);

        return new Double3D(x, y, pt.getZ());
    }

    /**
     *  Interpolates between two points to find an intermediate point.  If the input distance is greater
     *  than the distance between the two points, the ending point is returned.
     *
     * @param  pt1  starting point.
     * @param  pt2  ending point.
     * @param  distance distance from starting point in nmi.
     * @return  intermediate point.
     */
    @Override
    public final Double3D interpolateLocation(Double3D pt1, Double3D pt2,
        double distance)
    {
        final double d12 = pt1.distance(pt2);
        Double3D location = null;
        if (distance >= d12)
        {
            location = pt2;
        }
        else
        {
            final double frac = distance / d12;

            final double x3 = pt1.getX() + frac * (pt2.getX() - pt1.getX());
            final double y3 = pt1.getY() + frac * (pt2.getY() - pt1.getY());
            final double z3 = pt1.getZ() + frac * (pt2.getZ() - pt1.getZ());
            location = new Double3D(x3, y3, z3);
        }

        return location;
    }

    /**
     *  Calculates the azimuth angle from point 1 to point 2.
     *
     * @param  pt1  point 1.
     * @param  pt2  point 2.
     * @return      azimuth angle between points in radians.
     */
    @Override
    public final double azimuthAngle(Double3D pt1, Double3D pt2)
    {
        return pt1.angleTheta(pt2);
    }

    /**
     *  Calculates the elevation angle from point 1 to point 2.
     *
     * @param  pt1     point 1
     * @param  pt2     point 2
     * @param  k       EarthFactor
     * @return         elevation angle in radians.
     */
    @Override
    public final double elevationAngle(Double3D pt1, Double3D pt2,
        EarthFactor k)
    {
        return pt1.anglePhi(pt2);
    }
}
