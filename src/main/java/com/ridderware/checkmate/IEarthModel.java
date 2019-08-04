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
 * Interface for Earth Models.  Declares a set of methods for computing distances
 * and angles between points.
 *
 * @author Jeff Ridder
 */
public interface IEarthModel
{
    /**
     * Enumeration of Earth radius factors.
     */
    public enum EarthFactor
    {
        /** Real Earth with unaugmented radius */
        REAL_EARTH(1.0),
        /** Electromagnetic Earth with artificially augmented radius to account for refraction */
        EM_EARTH(4. / 3.);

        private final double earth_factor;

        private EarthFactor(double earth_factor)
        {
            this.earth_factor = earth_factor;
        }

        /**
         * Returns the value of the EarthFactor as a double so that it can be used directly
         * in calculations.
         *
         * @return double value of the EarthFactor
         */
        public double value()
        {
            return this.earth_factor;
        }
    }

    /**
     * Returns the coordinate system as a String.
     *
     * @return coordinate system in use.
     */
    public String getCoordinateSystem();

    /**
     *  Computes the line-of-site distance between two points.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      line of site distance in nmi.
     */
    public double trueDistance(Double3D pt1, Double3D pt2);

    /**
     * Returns the square of the true line-of-site distance between two points.
     * Although it might appear redundant, we include it in this interface since many calculations can
     * be significantly accelerated by computing and working with the square of the distance.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      square of the line of site distance in nmi-squared.
     */
    public double trueDistanceSq(Double3D pt1, Double3D pt2);

    /**
     *  Projects the ending location given a starting point, distance, and azimuth angle.
     *  The object returned by this method is temporary and may be modified by subsequent
     *  calls.  Therefore, users should take care to copy the object's attributes as soon as they 
     *  are returned, and not retain a reference to the Double3D object itself.
     *
     * @param  pt     starting point.
     * @param  distance  distance in nmi.
     * @param  azimuth   azimuth between +/- PI and in radians.
     * @return           ending location.
     */
    public Double3D projectLocation(Double3D pt, double distance,
        double azimuth);

    /**
     *  Interpolates between two points to find an intermediate point.  If the input distance is greater
     *  than the distance between the two points, the ending point is returned.
     *  The object returned by this method is temporary and may be modified by subsequent
     *  calls.  Therefore, users should take care to copy the object's attributes as soon as they 
     *  are returned, and not retain a reference to the Double3D object itself.
     *
     * @param  pt1  starting point.
     * @param  pt2  ending point.
     * @param  distance distance from starting point in nmi.
     * @return  intermediate point.
     */
    public Double3D interpolateLocation(Double3D pt1, Double3D pt2,
        double distance);

    /**
     *  Calculates the azimuth angle from point 1 to point 2.
     *
     * @param  pt1  point 1.
     * @param  pt2  point 2.
     * @return      azimuth angle between two points between +/- PI in radians.
     */
    public double azimuthAngle(Double3D pt1, Double3D pt2);

    /**
     *  Calculates the elevation angle from point 1 to point 2.
     *
     * @param  pt1     point 1
     * @param  pt2     point 2
     * @param  k       Earth factor
     * @return         elevation angle in radians.
     */
    public double elevationAngle(Double3D pt1, Double3D pt2, EarthFactor k);
}
